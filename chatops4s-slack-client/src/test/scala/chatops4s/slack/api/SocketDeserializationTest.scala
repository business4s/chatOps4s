package chatops4s.slack.api

import io.circe.Decoder
import io.circe.parser.decode
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.io.Source
import scala.util.Using

import blocks.ViewType
import socket.*

class SocketDeserializationTest extends AnyFreeSpec with Matchers {

  private def loadFixture(name: String): Option[String] =
    Option(getClass.getResourceAsStream(s"/ws-events/$name")).map { is =>
      Using.resource(Source.fromInputStream(is))(_.mkString)
    }

  private def parseOk[T: Decoder](json: String)(checks: T => Unit): Unit =
    decode[T](json) match {
      case Right(value) => checks(value)
      case Left(err)    => fail(s"Failed to decode: $err")
    }

  "Deserialization of real Slack WS events" - {

    "interactive envelope" in {
      val fixture = loadFixture("interactive.json")
      assume(fixture.isDefined, "Fixture ws-events/interactive.json not found")

      parseOk[Envelope](fixture.get) { env =>
        env.envelope_id shouldBe "7c9d3f65-6586-4506-b22e-1ffcf6da41eb"
        env.`type` shouldBe EnvelopeType.Interactive
        env.payload shouldBe defined
        env.accepts_response_payload shouldBe Some(false)
      }
    }

    "interactive payload" in {
      val fixture = loadFixture("interactive.json")
      assume(fixture.isDefined, "Fixture ws-events/interactive.json not found")

      val envelope = decode[Envelope](fixture.get).toOption.get
      val payload  = envelope.payload.get.as[InteractionPayload]
      payload.isRight shouldBe true
      val p        = payload.toOption.get
      p.`type` shouldBe "block_actions"
      p.user.id shouldBe UserId("U05GUDS0A48")
      p.channel shouldBe Some(Channel(ChannelId("C0ADN3WUR8D"), Some("auto-tests")))
      p.container.message_ts shouldBe Some(Timestamp("1770813738.876949"))
      p.actions.size shouldBe 1
      p.actions.head.action_id shouldBe "collector-test-btn"
      p.actions.head.value shouldBe Some("test-value")
      p.response_url shouldBe defined
    }

    "slash_commands envelope" in {
      val fixture = loadFixture("slash_commands.json")
      assume(fixture.isDefined, "Fixture ws-events/slash_commands.json not found")

      parseOk[Envelope](fixture.get) { env =>
        env.envelope_id shouldBe "906efbcc-3f0a-4e4a-a9d3-43cc75007c53"
        env.`type` shouldBe EnvelopeType.SlashCommands
        env.payload shouldBe defined
        env.accepts_response_payload shouldBe Some(true)
      }
    }

    "slash_commands payload" in {
      val fixture = loadFixture("slash_commands.json")
      assume(fixture.isDefined, "Fixture ws-events/slash_commands.json not found")

      val envelope = decode[Envelope](fixture.get).toOption.get
      val payload  = envelope.payload.get.as[SlashCommandPayload]
      payload.isRight shouldBe true
      val p        = payload.toOption.get
      p.command shouldBe "/deploy"
      p.text shouldBe "1.2.3"
      p.user_id shouldBe UserId("U05GUDS0A48")
      p.channel_id shouldBe ChannelId("C0ADN3WUR8D")
      p.team_id shouldBe TeamId("T05FQ6AER6K")
      p.team_domain shouldBe "voytektestworkspace"
      p.channel_name shouldBe "auto-tests"
      p.api_app_id shouldBe "A0ADK3B6ZV3"
      p.response_url should not be empty
      p.trigger_id.value should not be empty
    }
  }

  "Deserialization of doc examples" - {

    "Envelope" in {
      parseOk[Envelope](
        """{
          |  "envelope_id": "abc-123",
          |  "type": "interactive",
          |  "payload": {"type": "block_actions"},
          |  "accepts_response_payload": false,
          |  "retry_attempt": 0,
          |  "retry_reason": ""
          |}""".stripMargin,
      ) { env =>
        env.envelope_id shouldBe "abc-123"
        env.`type` shouldBe EnvelopeType.Interactive
        env.payload shouldBe defined
        env.accepts_response_payload shouldBe Some(false)
        env.retry_attempt shouldBe Some(0)
        env.retry_reason shouldBe Some("")
      }
    }

    // https://docs.slack.dev/reference/interaction-payloads/block_actions-payload
    "InteractionPayload" in {
      parseOk[InteractionPayload](
        """{
          |  "type": "block_actions",
          |  "trigger_id": "trigger-1",
          |  "user": {"id": "U123", "username": "testuser", "name": "Test User", "team_id": "T123"},
          |  "api_app_id": "A123",
          |  "token": "tok",
          |  "container": {"type": "message", "message_ts": "1234.5678", "channel_id": "C123", "is_ephemeral": false},
          |  "actions": [
          |    {
          |      "action_id": "btn-1",
          |      "block_id": "blk-1",
          |      "type": "button",
          |      "action_ts": "1234567890.123",
          |      "value": "clicked"
          |    }
          |  ],
          |  "team": {"id": "T123", "domain": "test"},
          |  "channel": {"id": "C123", "name": "general"},
          |  "response_url": "https://hooks.slack.com/actions/T123/456/789"
          |}""".stripMargin,
      ) { p =>
        p.`type` shouldBe "block_actions"
        p.trigger_id shouldBe TriggerId("trigger-1")
        p.user.id shouldBe UserId("U123")
        p.user.username shouldBe Some("testuser")
        p.api_app_id shouldBe "A123"
        p.token shouldBe "tok"
        p.container.message_ts shouldBe Some(Timestamp("1234.5678"))
        p.actions.size shouldBe 1
        p.actions.head.action_id shouldBe "btn-1"
        p.actions.head.value shouldBe Some("clicked")
        p.team shouldBe Some(Team(TeamId("T123"), Some("test")))
        p.channel shouldBe Some(Channel(ChannelId("C123"), Some("general")))
        p.response_url shouldBe Some("https://hooks.slack.com/actions/T123/456/789")
      }
    }

    // https://docs.slack.dev/interactivity/implementing-slash-commands
    "SlashCommandPayload" in {
      parseOk[SlashCommandPayload](
        """{
          |  "command": "/deploy",
          |  "text": "v1.2.3",
          |  "user_id": "U123",
          |  "channel_id": "C123",
          |  "response_url": "https://hooks.slack.com/commands/T123/456/789",
          |  "trigger_id": "trigger-1",
          |  "team_id": "T123",
          |  "team_domain": "testworkspace",
          |  "channel_name": "general",
          |  "api_app_id": "A123",
          |  "token": "tok",
          |  "user_name": "testuser"
          |}""".stripMargin,
      ) { p =>
        p.command shouldBe "/deploy"
        p.text shouldBe "v1.2.3"
        p.user_id shouldBe UserId("U123")
        p.channel_id shouldBe ChannelId("C123")
        p.team_id shouldBe TeamId("T123")
        p.team_domain shouldBe "testworkspace"
        p.channel_name shouldBe "general"
        p.api_app_id shouldBe "A123"
        p.token shouldBe Some("tok")
        p.user_name shouldBe Some("testuser")
      }
    }

    // https://docs.slack.dev/reference/interaction-payloads/view-interactions-payload
    "ViewSubmissionPayload" in {
      parseOk[ViewSubmissionPayload](
        """{
          |  "type": "view_submission",
          |  "user": {"id": "U123"},
          |  "view": {
          |    "id": "V123",
          |    "type": "modal",
          |    "callback_id": "my-form",
          |    "state": {
          |      "values": {
          |        "block1": {
          |          "field1": {"value": "hello"}
          |        }
          |      }
          |    },
          |    "hash": "h456",
          |    "private_metadata": "meta"
          |  },
          |  "api_app_id": "A123",
          |  "team": {"id": "T123"},
          |  "token": "tok"
          |}""".stripMargin,
      ) { p =>
        p.`type` shouldBe "view_submission"
        p.user.id shouldBe UserId("U123")
        p.view.id shouldBe "V123"
        p.view.`type` shouldBe Some(ViewType.Modal)
        p.view.callback_id shouldBe Some("my-form")
        p.view.state shouldBe defined
        p.view.state.get.values.contains("block1") shouldBe true
        p.view.hash shouldBe Some("h456")
        p.view.private_metadata shouldBe Some("meta")
        p.api_app_id shouldBe "A123"
        p.team shouldBe Some(Team(TeamId("T123")))
        p.token shouldBe Some("tok")
      }
    }
  }
}
