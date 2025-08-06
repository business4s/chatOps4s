package chatops4s.slack

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import chatops4s.slack.models.*
import io.circe.syntax.*
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.*
import sttp.client4.testing.*
import sttp.model.StatusCode

class SlackClientSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  "SlackClient" - {

    "should send a simple message successfully" in {
      val config = SlackConfig(botToken = "xoxb-test-token", signingSecret = "test-secret")

      val mockResponse = SlackPostMessageResponse(
        ok = true,
        channel = Some("C1234567890"),
        ts = Some("1234567890.123456"),
        message = Some(
          SlackMessage(
            text = "Deploy to production?",
            user = Some("U87654321"),
            ts = "1234567890.123456",
          ),
        ),
      )

      val backend = BackendStub[IO]
        .whenAnyRequest(_.uri.toString().contains("chat.postMessage"))
        .thenRespondAdjust(mockResponse.asJson.noSpaces)

      val client  = new SlackClient(config, backend)
      val request = SlackPostMessageRequest(
        channel = "C1234567890",
        text = "Deploy to production?",
      )

      client.postMessage(request).asserting { response =>
        response.ok shouldBe true
        response.channel shouldBe Some("C1234567890")
        response.ts shouldBe Some("1234567890.123456")
      }
    }

    "should send a message with interactive buttons" in {
      val config = SlackConfig(botToken = "xoxb-test-token", signingSecret = "test-secret")

      val mockResponse = SlackPostMessageResponse(
        ok = true,
        channel = Some("C1234567890"),
        ts = Some("1234567890.123456"),
        message = Some(
          SlackMessage(
            text = "Deploy to production?",
            user = Some("U87654321"),
            ts = "1234567890.123456",
          ),
        ),
      )

      val backend = BackendStub[IO]
        .whenRequestMatches(_.uri.toString().contains("chat.postMessage"))
        .thenRespondAdjust(mockResponse.asJson.noSpaces)

      val client  = new SlackClient(config, backend)
      val request = SlackPostMessageRequest(
        channel = "C1234567890",
        text = "Deploy to production?",
        blocks = Some(
          List(
            SlackBlock(
              `type` = "section",
              text = Some(SlackText(`type` = "mrkdwn", text = "Deploy to production?")),
            ),
            SlackBlock(
              `type` = "actions",
              elements = Some(
                List(
                  SlackBlockElement(
                    `type` = "button",
                    text = Some(SlackText(`type` = "plain_text", text = "Approve")),
                    action_id = Some("approve_deploy"),
                    value = Some("approve_deploy"),
                    style = Some("primary"),
                  ),
                  SlackBlockElement(
                    `type` = "button",
                    text = Some(SlackText(`type` = "plain_text", text = "Decline")),
                    action_id = Some("decline_deploy"),
                    value = Some("decline_deploy"),
                    style = Some("danger"),
                  ),
                ),
              ),
            ),
          ),
        ),
      )

      client.postMessage(request).asserting { response =>
        response.ok shouldBe true
        response.channel shouldBe Some("C1234567890")
        response.ts shouldBe Some("1234567890.123456")
        response.message.map(_.text) shouldBe Some("Deploy to production?")
      }
    }

    "should handle API errors gracefully" in {
      val config = SlackConfig(botToken = "xoxb-invalid-token", signingSecret = "test-secret")

      val errorResponse = SlackPostMessageResponse(
        ok = false,
        error = Some("invalid_auth"),
      )

      val backend = BackendStub[IO]
        .whenRequestMatches(_.uri.toString().contains("chat.postMessage"))
        .thenRespondAdjust(errorResponse.asJson.noSpaces)

      val client  = new SlackClient(config, backend)
      val request = SlackPostMessageRequest(
        channel = "C1234567890",
        text = "Test message",
      )

      client.postMessage(request).attempt.asserting { result =>
        result.isLeft shouldBe true
        result.left.toOption.get shouldBe a[RuntimeException]
        result.left.toOption.get.getMessage should include("Slack API error: invalid_auth")
      }
    }

    "should send thread replies correctly" in {
      val config = SlackConfig(botToken = "xoxb-test-token", signingSecret = "test-secret")

      val mockResponse = SlackPostMessageResponse(
        ok = true,
        channel = Some("C1234567890"),
        ts = Some("1234567891.123456"),
        message = Some(
          SlackMessage(
            text = "Thanks for your feedback",
            user = Some("U87654321"),
            ts = "1234567891.123456",
            thread_ts = Some("1234567890.123456"),
          ),
        ),
      )

      val backend = BackendStub[IO]
        .whenRequestMatches(_.uri.toString().contains("chat.postMessage"))
        .thenRespondAdjust(mockResponse.asJson.noSpaces)

      val client = new SlackClient(config, backend)

      client.postMessageToThread("C1234567890", "1234567890.123456", "Thanks for your feedback").asserting { response =>
        response.ok shouldBe true
        response.channel shouldBe Some("C1234567890")
        response.ts shouldBe Some("1234567891.123456")
        response.message.flatMap(_.thread_ts) shouldBe Some("1234567890.123456")
      }
    }

    "should handle network errors" in {
      val config = SlackConfig(botToken = "xoxb-test-token", signingSecret = "test-secret")

      val backend = BackendStub[IO]
        .whenRequestMatches(_.uri.toString().contains("chat.postMessage"))
        .thenRespondAdjust(Response("Network error", StatusCode.InternalServerError))

      val client  = new SlackClient(config, backend)
      val request = SlackPostMessageRequest(
        channel = "C1234567890",
        text = "Test message",
      )

      client.postMessage(request).attempt.asserting { result =>
        result.isLeft shouldBe true
        result.left.toOption.get shouldBe a[RuntimeException]
        result.left.toOption.get.getMessage should include("Failed to send message")
      }
    }

    "should handle JSON parsing errors" in {
      val config = SlackConfig(botToken = "xoxb-test-token", signingSecret = "test-secret")

      val backend = BackendStub[IO]
        .whenRequestMatches(_.uri.toString().contains("chat.postMessage"))
        .thenRespondAdjust("invalid json response")

      val client  = new SlackClient(config, backend)
      val request = SlackPostMessageRequest(
        channel = "C1234567890",
        text = "Test message",
      )

      client.postMessage(request).attempt.asserting { result =>
        result.isLeft shouldBe true
        result.left.toOption.get shouldBe a[RuntimeException]
        result.left.toOption.get.getMessage should include("Failed to send message")
      }
    }

    "should validate request headers are set correctly" in {
      val config = SlackConfig(botToken = "xoxb-test-token", signingSecret = "test-secret")

      val mockResponse = SlackPostMessageResponse(ok = true, channel = Some("C1234567890"), ts = Some("1234567890.123456"))

      val backend = BackendStub[IO]
        .whenRequestMatchesPartial{ request =>
          request.uri.toString().contains("chat.postMessage") &&
            request.headers.exists(h => h.name == "Authorization" && h.value == s"Bearer ${config.botToken}") &&
            request.headers.exists(h => h.name == "Content-Type" && h.value == "application/json")
        }
        .thenRespondAdjust(mockResponse.asJson.noSpaces)

      val client  = new SlackClient(config, backend)
      val request = SlackPostMessageRequest(
        channel = "C1234567890",
        text = "Test message with header validation",
      )

      client.postMessage(request).asserting { response =>
        response.ok shouldBe true
        response.channel shouldBe Some("C1234567890")
      }
    }
  }
}