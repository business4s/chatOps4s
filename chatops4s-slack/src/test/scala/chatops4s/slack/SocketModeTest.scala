package chatops4s.slack

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.syntax.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.StatusCode
import sttp.ws.WebSocketFrame
import sttp.ws.testing.WebSocketStub

import scala.concurrent.duration.*
import scala.io.Source
import scala.util.Using

import SlackModels.*

class SocketModeTest extends AnyFreeSpec with Matchers {

  "SocketMode" - {

    "real Slack events" - {

      "should dispatch interactive payload" in {
        val fixture = loadFixture("interactive.json")
        assume(fixture.isDefined, "Fixture ws-events/interactive.json not found — run SocketModeCollector first")

        var captured: Option[InteractionPayload] = None
        var commandCalled = false
        run(
          fixture.get,
          onInteraction = p => IO { captured = Some(p) },
          onSlashCommand = _ => IO { commandCalled = true },
        )

        commandCalled shouldBe false
        captured shouldBe Some(InteractionPayload(
          `type` = "block_actions",
          user = User("U05GUDS0A48"),
          channel = Channel("C0ADN3WUR8D"),
          container = Container(Some("1770813738.876949")),
          message = Some(InteractionMessage(None)),
          actions = Some(List(Action("collector-test-btn", Some("test-value")))),
          trigger_id = "10501237082865.5534214501223.48e1216dab3421ad85dcfb49cea8c8f2",
        ))
      }

      "should dispatch slash command payload" in {
        val fixture = loadFixture("slash_commands.json")
        assume(fixture.isDefined, "Fixture ws-events/slash_commands.json not found — run SocketModeCollector first")

        var captured: Option[SlashCommandPayload] = None
        var interactionCalled = false
        run(
          fixture.get,
          onInteraction = _ => IO { interactionCalled = true },
          onSlashCommand = p => IO { captured = Some(p) },
        )

        interactionCalled shouldBe false
        captured shouldBe Some(SlashCommandPayload(
          command = "/deploy",
          text = "1.2.3",
          user_id = "U05GUDS0A48",
          channel_id = "C0ADN3WUR8D",
          response_url = "https://hooks.slack.com/commands/T05FQ6AER6K/10472872949175/cpDA6yXHpAZg0Le5snF4LYma",
          trigger_id = "10484895534197.5534214501223.19b967da015b9de9d9f92ba8403cc639",
        ))
      }

      "should send ack with envelope_id from real event" in {
        val fixture = loadFixture("interactive.json")
        assume(fixture.isDefined, "Fixture ws-events/interactive.json not found — run SocketModeCollector first")

        var sentFrames = List.empty[WebSocketFrame]
        val wsStub = WebSocketStub
          .initialReceive(List(WebSocketFrame.text(fixture.get)))
          .thenRespond { frame =>
            sentFrames = sentFrames :+ frame
            List.empty
          }

        runRaw(wsStub)

        val ackTexts = sentFrames.collect { case f: WebSocketFrame.Text => f.payload }
        ackTexts should not be empty
        io.circe.parser.decode[SocketAck](ackTexts.head) shouldBe
          Right(SocketAck("7c9d3f65-6586-4506-b22e-1ffcf6da41eb"))
      }
    }

    "should ignore unknown envelope types" in {
      var interactionCalled = false
      var commandCalled = false
      val envelope = SocketEnvelope("env-789", "unknown_type", None).asJson.noSpaces

      run(
        envelope,
        onInteraction = _ => IO { interactionCalled = true },
        onSlashCommand = _ => IO { commandCalled = true },
      )

      interactionCalled shouldBe false
      commandCalled shouldBe false
    }

    "should continue processing after malformed JSON" in {
      var captured: Option[InteractionPayload] = None
      val wsStub = WebSocketStub
        .initialReceive(List(
          WebSocketFrame.text("not valid json"),
          WebSocketFrame.text(syntheticInteractionEnvelope()),
        ))
        .thenRespond(_ => List.empty)

      runRaw(wsStub, onInteraction = p => IO { captured = Some(p) })

      captured shouldBe Some(syntheticInteractionPayload)
    }

    "should handle missing payload gracefully" in {
      var interactionCalled = false
      val envelope = SocketEnvelope("env-no-payload", "interactive", None).asJson.noSpaces
      run(envelope, onInteraction = _ => IO { interactionCalled = true })
      interactionCalled shouldBe false
    }

    "should handle invalid payload without crashing" in {
      var interactionCalled = false
      val envelope = SocketEnvelope(
        "env-bad-payload",
        "interactive",
        Some(io.circe.Json.fromString("not-a-valid-payload")),
      ).asJson.noSpaces
      run(envelope, onInteraction = _ => IO { interactionCalled = true })
      interactionCalled shouldBe false
    }

    "should process multiple messages in sequence" in {
      var interactionCount = 0
      var commandCount = 0
      val wsStub = WebSocketStub
        .initialReceive(List(WebSocketFrame.text(syntheticInteractionEnvelope("env-1"))))
        .thenRespondS(0) { (count, _) =>
          if (count == 0) (1, List(WebSocketFrame.text(syntheticSlashCommandEnvelope("env-2"))))
          else (count + 1, List.empty)
        }

      runRaw(
        wsStub,
        onInteraction = _ => IO { interactionCount += 1 },
        onSlashCommand = _ => IO { commandCount += 1 },
      )

      interactionCount should be >= 1
      commandCount should be >= 1
    }

    "should recover from handler errors and continue processing" in {
      var callCount = 0
      val wsStub = WebSocketStub
        .initialReceive(List(WebSocketFrame.text(syntheticInteractionEnvelope("env-1"))))
        .thenRespondS(0) { (count, _) =>
          if (count == 0) (1, List(WebSocketFrame.text(syntheticInteractionEnvelope("env-2"))))
          else (count + 1, List.empty)
        }

      runRaw(
        wsStub,
        onInteraction = _ => IO { callCount += 1 } >> IO.raiseError(new RuntimeException("boom")),
      )

      callCount should be >= 2
    }
  }

  private val connectionsOpenResponse = """{"ok":true,"url":"wss://mock-ws"}"""

  /** Convenience: wraps a single envelope text into a WebSocketStub and calls runRaw. */
  private def run(
      envelopeText: String,
      onInteraction: InteractionPayload => IO[Unit] = _ => IO.unit,
      onSlashCommand: SlashCommandPayload => IO[Unit] = _ => IO.unit,
      onViewSubmission: ViewSubmissionPayload => IO[Unit] = _ => IO.unit,
  ): Unit = {
    val wsStub = WebSocketStub
      .initialReceive(List(WebSocketFrame.text(envelopeText)))
      .thenRespond(_ => List.empty)
    runRaw(wsStub, onInteraction, onSlashCommand, onViewSubmission)
  }

  /** Runs SocketMode.runLoop with the given WebSocket stub and a no-op retry delay. */
  private def runRaw(
      wsStub: WebSocketStub[?],
      onInteraction: InteractionPayload => IO[Unit] = _ => IO.unit,
      onSlashCommand: SlashCommandPayload => IO[Unit] = _ => IO.unit,
      onViewSubmission: ViewSubmissionPayload => IO[Unit] = _ => IO.unit,
  ): Unit = {
    val backend = MockBackend
      .create()
      .whenRequestMatches(_.uri.toString().contains("apps.connections.open"))
      .thenRespondAdjust(connectionsOpenResponse)
      .whenRequestMatches(_.uri.toString().startsWith("wss://"))
      .thenRespondAdjust(wsStub, StatusCode.SwitchingProtocols)

    SocketMode
      .runLoop("app-token", backend, onInteraction, onSlashCommand, onViewSubmission, retryDelay = Some(IO.unit))
      .timeout(1.second)
      .attempt
      .unsafeRunSync()
    ()
  }

  private def loadFixture(name: String): Option[String] =
    Option(getClass.getResourceAsStream(s"/ws-events/$name")).map { is =>
      Using.resource(Source.fromInputStream(is))(_.mkString)
    }

  private val syntheticInteractionPayload = InteractionPayload(
    `type` = "block_actions",
    user = User("U123"),
    channel = Channel("C123"),
    container = Container(Some("1234567890.123")),
    actions = Some(List(Action("btn-1", Some("approve")))),
    trigger_id = "test-trigger-id",
  )

  private val syntheticSlashCommandPayload = SlashCommandPayload(
    command = "/deploy",
    text = "v1.2.3",
    user_id = "U123",
    channel_id = "C123",
    response_url = "https://hooks.slack.com/commands/T123/456/789",
    trigger_id = "test-trigger-id",
  )

  private def syntheticInteractionEnvelope(envelopeId: String = "env-123"): String =
    SocketEnvelope(envelopeId, "interactive", Some(syntheticInteractionPayload.asJson)).asJson.noSpaces

  private def syntheticSlashCommandEnvelope(envelopeId: String = "env-456"): String =
    SocketEnvelope(envelopeId, "slash_commands", Some(syntheticSlashCommandPayload.asJson)).asJson.noSpaces
}
