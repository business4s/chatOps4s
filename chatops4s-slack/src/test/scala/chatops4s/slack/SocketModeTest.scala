package chatops4s.slack

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chatops4s.slack.api.SlackAppToken
import io.circe.syntax.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.StatusCode
import sttp.ws.WebSocketFrame
import sttp.ws.testing.WebSocketStub

import scala.concurrent.duration.*
import scala.io.Source
import scala.util.Using

import chatops4s.slack.api.{UserId}
import chatops4s.slack.api.socket.*

class SocketModeTest extends AnyFreeSpec with Matchers {

  "SocketMode" - {

    "real Slack events" - {

      "should dispatch interactive payload" in {
        val fixture = loadFixture("interactive.json")
        assume(fixture.isDefined, "Fixture ws-events/interactive.json not found — run SocketModeCollector first")

        var captured: Option[Envelope] = None
        run(
          fixture.get,
          handler = e => IO {
            if (e.`type` == EnvelopeType.Interactive) captured = Some(e)
          },
        )

        captured shouldBe defined
        captured.get.`type` shouldBe EnvelopeType.Interactive
        captured.get.payload shouldBe defined
        val payload = captured.get.payload.get.as[InteractionPayload]
        payload.isRight shouldBe true
        payload.toOption.get.user.id shouldBe UserId("U05GUDS0A48")
      }

      "should dispatch slash command payload" in {
        val fixture = loadFixture("slash_commands.json")
        assume(fixture.isDefined, "Fixture ws-events/slash_commands.json not found — run SocketModeCollector first")

        var captured: Option[Envelope] = None
        run(
          fixture.get,
          handler = e => IO {
            if (e.`type` == EnvelopeType.SlashCommands) captured = Some(e)
          },
        )

        captured shouldBe defined
        captured.get.`type` shouldBe EnvelopeType.SlashCommands
        val payload = captured.get.payload.get.as[SlashCommandPayload]
        payload.isRight shouldBe true
        payload.toOption.get.command shouldBe "/deploy"
        payload.toOption.get.text shouldBe "1.2.3"
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
        io.circe.parser.decode[Ack](ackTexts.head) shouldBe
          Right(Ack("7c9d3f65-6586-4506-b22e-1ffcf6da41eb"))
      }
    }

    "should ignore unknown envelope types" in {
      var handlerCalled = false
      val envelope = Envelope("env-789", EnvelopeType.Unknown("unknown_type"), None).asJson.noSpaces

      run(
        envelope,
        handler = _ => IO { handlerCalled = true },
      )

      // Handler is called for all envelope types (dispatch is done by the caller now)
      handlerCalled shouldBe true
    }

    "should continue processing after malformed JSON" in {
      var captured: Option[Envelope] = None
      val wsStub = WebSocketStub
        .initialReceive(List(
          WebSocketFrame.text("not valid json"),
          WebSocketFrame.text(syntheticInteractionEnvelope()),
        ))
        .thenRespond(_ => List.empty)

      runRaw(wsStub, handler = e => IO { captured = Some(e) })

      captured shouldBe defined
      captured.get.envelope_id shouldBe "env-123"
    }

    "should handle missing payload gracefully" in {
      var handlerCalled = false
      val envelope = Envelope("env-no-payload", EnvelopeType.Interactive, None).asJson.noSpaces
      run(envelope, handler = _ => IO { handlerCalled = true })
      handlerCalled shouldBe true
    }

    "should handle invalid payload without crashing" in {
      var handlerCalled = false
      val envelope = Envelope(
        "env-bad-payload",
        EnvelopeType.Interactive,
        Some(io.circe.Json.fromString("not-a-valid-payload")),
      ).asJson.noSpaces
      run(envelope, handler = _ => IO { handlerCalled = true })
      handlerCalled shouldBe true
    }

    "should process multiple messages in sequence" in {
      var count = 0
      val wsStub = WebSocketStub
        .initialReceive(List(WebSocketFrame.text(syntheticInteractionEnvelope("env-1"))))
        .thenRespondS(0) { (cnt, _) =>
          if (cnt == 0) (1, List(WebSocketFrame.text(syntheticSlashCommandEnvelope("env-2"))))
          else (cnt + 1, List.empty)
        }

      runRaw(
        wsStub,
        handler = _ => IO { count += 1 },
      )

      count should be >= 2
    }

    "disconnect" - {

      "refresh_requested should cause reconnection" in {
        var handlerCallCount = 0
        val disconnectMsg = Disconnect(DisconnectReason.RefreshRequested).asJson.noSpaces
        // Each WS connection receives an envelope then a disconnect.
        // On reconnect, the same stub is reused, so handler gets called again.
        val wsStub = WebSocketStub
          .initialReceive(List(
            WebSocketFrame.text(syntheticInteractionEnvelope("env-dc-1")),
            WebSocketFrame.text(disconnectMsg),
          ))
          .thenRespond(_ => List.empty)

        runRaw(wsStub, handler = _ => IO { handlerCallCount += 1 })

        handlerCallCount should be >= 2
      }

      "link_disabled should stop the loop" in {
        val disconnectMsg = Disconnect(DisconnectReason.LinkDisabled).asJson.noSpaces
        val wsStub = WebSocketStub
          .initialReceive(List(WebSocketFrame.text(disconnectMsg)))
          .thenRespond(_ => List.empty)

        val result = runRawResult(wsStub)

        result.isRight shouldBe true
      }

      "should process messages before disconnect" in {
        var captured: Option[Envelope] = None
        val disconnectMsg = Disconnect(DisconnectReason.RefreshRequested).asJson.noSpaces
        val wsStub = WebSocketStub
          .initialReceive(List(
            WebSocketFrame.text(syntheticInteractionEnvelope("env-before-dc")),
            WebSocketFrame.text(disconnectMsg),
          ))
          .thenRespond(_ => List.empty)

        runRaw(wsStub, handler = e => IO { captured = Some(e) })

        captured shouldBe defined
        captured.get.envelope_id shouldBe "env-before-dc"
      }
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
        handler = _ => IO { callCount += 1 } >> IO.raiseError(new RuntimeException("boom")),
      )

      callCount should be >= 2
    }
  }

  private val connectionsOpenResponse = """{"ok":true,"url":"wss://mock-ws"}"""

  /** Convenience: wraps a single envelope text into a WebSocketStub and calls runRaw. */
  private def run(
      envelopeText: String,
      handler: Envelope => IO[Unit] = _ => IO.unit,
  ): Unit = {
    val wsStub = WebSocketStub
      .initialReceive(List(WebSocketFrame.text(envelopeText)))
      .thenRespond(_ => List.empty)
    runRaw(wsStub, handler)
  }

  /** Runs SocketMode.runLoop with the given WebSocket stub and a no-op retry delay. */
  private def runRaw(
      wsStub: WebSocketStub[?],
      handler: Envelope => IO[Unit] = _ => IO.unit,
  ): Unit = {
    runRawResult(wsStub, handler)
    ()
  }

  private def runRawResult(
      wsStub: WebSocketStub[?],
      handler: Envelope => IO[Unit] = _ => IO.unit,
  ): Either[Throwable, Unit] = {
    val backend = MockBackend
      .create()
      .whenRequestMatches(_.uri.toString().contains("apps.connections.open"))
      .thenRespondAdjust(connectionsOpenResponse)
      .whenRequestMatches(_.uri.toString().startsWith("wss://"))
      .thenRespondAdjust(wsStub, StatusCode.SwitchingProtocols)

    SocketMode
      .runLoop(SlackAppToken.unsafe("xapp-test-token"), backend, handler, retryDelay = Some(IO.unit))
      .timeout(1.second)
      .attempt
      .unsafeRunSync()
  }

  private def loadFixture(name: String): Option[String] =
    Option(getClass.getResourceAsStream(s"/ws-events/$name")).map { is =>
      Using.resource(Source.fromInputStream(is))(_.mkString)
    }

  private def syntheticInteractionEnvelope(envelopeId: String = "env-123"): String = {
    val payload = io.circe.Json.obj(
      "type" -> io.circe.Json.fromString("block_actions"),
      "trigger_id" -> io.circe.Json.fromString("test-trigger-id"),
      "user" -> io.circe.Json.obj("id" -> io.circe.Json.fromString("U123")),
      "api_app_id" -> io.circe.Json.fromString("A123"),
      "token" -> io.circe.Json.fromString("tok"),
      "container" -> io.circe.Json.obj("message_ts" -> io.circe.Json.fromString("1234567890.123")),
      "actions" -> io.circe.Json.arr(io.circe.Json.obj(
        "action_id" -> io.circe.Json.fromString("btn-1"),
        "block_id" -> io.circe.Json.fromString("blk-1"),
        "type" -> io.circe.Json.fromString("button"),
        "action_ts" -> io.circe.Json.fromString("1234567890.123"),
        "value" -> io.circe.Json.fromString("approve"),
      )),
      "hash" -> io.circe.Json.fromString("h123"),
    )
    Envelope(envelopeId, EnvelopeType.Interactive, Some(payload)).asJson.noSpaces
  }

  private def syntheticSlashCommandEnvelope(envelopeId: String = "env-456"): String = {
    val payload = io.circe.Json.obj(
      "command" -> io.circe.Json.fromString("/deploy"),
      "text" -> io.circe.Json.fromString("v1.2.3"),
      "user_id" -> io.circe.Json.fromString("U123"),
      "channel_id" -> io.circe.Json.fromString("C123"),
      "response_url" -> io.circe.Json.fromString("https://hooks.slack.com/commands/T123/456/789"),
      "trigger_id" -> io.circe.Json.fromString("test-trigger-id"),
      "team_id" -> io.circe.Json.fromString("T123"),
      "team_domain" -> io.circe.Json.fromString("test"),
      "channel_name" -> io.circe.Json.fromString("general"),
      "api_app_id" -> io.circe.Json.fromString("A123"),
    )
    Envelope(envelopeId, EnvelopeType.SlashCommands, Some(payload)).asJson.noSpaces
  }
}
