package chatops4s.slack

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import chatops4s.{Message, Button}
import chatops4s.slack.models.*
import chatops4s.slack.models.SlackModels.given
import io.circe.syntax.*
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.Response
import sttp.client4.testing.SttpBackendStub
import sttp.model.StatusCode

class SlackGatewayIntegrationSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  "SlackOutboundGateway" - {

    "should convert ChatOps4s Message to Slack format and send successfully" in {
      val config = SlackConfig(botToken = "xoxb-test-token", signingSecret = "test-secret")

      val mockResponse = SlackPostMessageResponse(
        ok = true,
        channel = Some("C1234567890"),
        ts = Some("1234567890.123456"),
        message = Some(SlackMessage(
          text = "Deploy to production?",
          user = Some("U87654321"),
          ts = "1234567890.123456"
        ))
      )

      val backend = SttpBackendStub[IO]
        .whenRequestMatches { req =>
          req.uri.path.startsWith(List("api", "chat.postMessage")) &&
            req.body.toString.contains("Deploy to production?") &&
            req.body.toString.contains("actions") &&
            req.body.toString.contains("Approve") &&
            req.body.toString.contains("Decline")
        }
        .thenRespond(Response.ok(mockResponse.asJson.noSpaces))

      val slackClient = new SlackClient(config, backend)
      val outboundGateway = new SlackOutboundGateway(slackClient)

      val message = Message(
        text = "Deploy to production?",
        interactions = Seq(
          Button("Approve", "approve_action"),
          Button("Decline", "decline_action")
        )
      )

      outboundGateway.sendToChannel("C1234567890", message).asserting { response =>
        response.messageId shouldBe "1234567890.123456"
      }
    }

    "should handle simple text messages without interactions" in {
      val config = SlackConfig(botToken = "xoxb-test-token", signingSecret = "test-secret")

      val mockResponse = SlackPostMessageResponse(
        ok = true,
        channel = Some("C1234567890"),
        ts = Some("1234567890.123456")
      )

      val backend = SttpBackendStub[IO]
        .whenRequestMatches { req =>
          req.uri.path.startsWith(List("api", "chat.postMessage")) &&
            req.body.toString.contains("Simple message") &&
            !req.body.toString.contains("blocks")
        }
        .thenRespond(Response.ok(mockResponse.asJson.noSpaces))

      val slackClient = new SlackClient(config, backend)
      val outboundGateway = new SlackOutboundGateway(slackClient)

      val message = Message(text = "Simple message")

      outboundGateway.sendToChannel("C1234567890", message).asserting { response =>
        response.messageId shouldBe "1234567890.123456"
      }
    }

    "should send thread replies correctly" in {
      val config = SlackConfig(botToken = "xoxb-test-token", signingSecret = "test-secret")

      val mockResponse = SlackPostMessageResponse(
        ok = true,
        channel = Some("C1234567890"),
        ts = Some("1234567891.123456")
      )

      val backend = SttpBackendStub[IO]
        .whenRequestMatches { req =>
          req.uri.path.startsWith(List("api", "chat.postMessage")) &&
            req.body.toString.contains("thread_ts") &&
            req.body.toString.contains("1234567890.123456")
        }
        .thenRespond(Response.ok(mockResponse.asJson.noSpaces))

      val slackClient = new SlackClient(config, backend)
      val outboundGateway = new SlackOutboundGateway(slackClient)

      val message = Message(text = "Thanks for your feedback")

      outboundGateway.sendToThread("C1234567890-1234567890.123456", message).asserting { response =>
        response.messageId shouldBe "1234567891.123456"
      }
    }
  }

  "SlackInboundGateway" - {

    "should register actions and handle interactions correctly" in {
      val inboundGateway = new SlackInboundGateway()
      var capturedContext: Option[chatops4s.InteractionContext] = None

      val handler: chatops4s.InteractionContext => IO[Unit] = { ctx =>
        IO {
          capturedContext = Some(ctx)
        }
      }

      for {
        buttonInteraction <- inboundGateway.registerAction(handler)
        button = buttonInteraction.render("Test Button")

        // Simulate Slack interaction payload
        payload = SlackInteractionPayload(
          `type` = "block_actions",
          user = SlackUser(id = "U123456", name = "testuser"),
          container = SlackContainer(`type` = "message", message_ts = Some("1234567890.123456")),
          trigger_id = "trigger123",
          team = SlackTeam(id = "T123456", domain = "testteam"),
          channel = SlackChannel(id = "C123456", name = "general"),
          actions = Some(List(
            SlackAction(
              action_id = button.value,
              text = SlackText(`type` = "plain_text", text = button.label),
              value = Some(button.value),
              `type` = "button",
              action_ts = "1234567890"
            )
          ))
        )

        _ <- inboundGateway.handleInteraction(payload)
      } yield {
        capturedContext shouldBe defined
        capturedContext.get.userId shouldBe "U123456"
        capturedContext.get.channelId shouldBe "C123456"
        capturedContext.get.messageId shouldBe "1234567890.123456"
      }
    }
  }
}