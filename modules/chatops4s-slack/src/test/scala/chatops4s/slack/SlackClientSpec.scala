package chatops4s.slack

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import chatops4s.slack.models.*
import chatops4s.slack.models.SlackModels.given
import io.circe.syntax.*
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.Response
import sttp.client4.testing.SttpBackendStub
import sttp.model.{Method, StatusCode}

class SlackClientSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  "SlackClient" - {

    "should send a simple message successfully" in {
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
        .whenRequestMatches(_.uri.path.startsWith(List("api", "chat.postMessage")))
        .thenRespond(Response.ok(mockResponse.asJson.noSpaces))

      val client = new SlackClient(config, backend)
      val request = SlackPostMessageRequest(
        channel = "C1234567890",
        text = "Deploy to production?"
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
        message = Some(SlackMessage(
          text = "Deploy to production?",
          user = Some("U87654321"),
          ts = "1234567890.123456"
        ))
      )

      val backend = SttpBackendStub[IO]
        .whenRequestMatches { req =>
          req.uri.path.startsWith(List("api", "chat.postMessage")) &&
            req.method == Method.POST &&
            req.headers.exists(h => h.name == "Authorization" && h.value == "Bearer xoxb-test-token")
        }
        .thenRespond(Response.ok(mockResponse.asJson.noSpaces))

      val client = new SlackClient(config, backend)
      val request = SlackPostMessageRequest(
        channel = "C1234567890",
        text = "Deploy to production?",
        blocks = Some(List(
          SlackBlock(
            `type` = "section",
            text = Some(SlackText(`type` = "mrkdwn", text = "Deploy to production?"))
          ),
          SlackBlock(
            `type` = "actions",
            elements = Some(List(
              SlackBlockElement(
                `type` = "button",
                text = Some(SlackText(`type` = "plain_text", text = "Approve")),
                action_id = Some("approve_deploy"),
                value = Some("approve_deploy"),
                style = Some("primary")
              ),
              SlackBlockElement(
                `type` = "button",
                text = Some(SlackText(`type` = "plain_text", text = "Decline")),
                action_id = Some("decline_deploy"),
                value = Some("decline_deploy"),
                style = Some("danger")
              )
            ))
          )
        ))
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
        error = Some("invalid_auth")
      )

      val backend = SttpBackendStub[IO]
        .whenRequestMatches(_.uri.path.startsWith(List("api", "chat.postMessage")))
        .thenRespond(Response.ok(errorResponse.asJson.noSpaces))

      val client = new SlackClient(config, backend)
      val request = SlackPostMessageRequest(
        channel = "C1234567890",
        text = "Test message"
      )

      recoverToSucceededIf[RuntimeException] {
        client.postMessage(request)
      }
    }

    "should send thread replies correctly" in {
      val config = SlackConfig(botToken = "xoxb-test-token", signingSecret = "test-secret")

      val mockResponse = SlackPostMessageResponse(
        ok = true,
        channel = Some("C1234567890"),
        ts = Some("1234567891.123456"),
        message = Some(SlackMessage(
          text = "Thanks for your feedback",
          user = Some("U87654321"),
          ts = "1234567891.123456",
          thread_ts = Some("1234567890.123456")
        ))
      )

      val backend = SttpBackendStub[IO]
        .whenRequestMatches { req =>
          req.uri.path.startsWith(List("api", "chat.postMessage")) &&
            req.body.toString.contains("thread_ts")
        }
        .thenRespond(Response.ok(mockResponse.asJson.noSpaces))

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

      val backend = SttpBackendStub[IO]
        .whenRequestMatches(_.uri.path.startsWith(List("api", "chat.postMessage")))
        .thenRespond(Response("Network error", StatusCode.InternalServerError))

      val client = new SlackClient(config, backend)
      val request = SlackPostMessageRequest(
        channel = "C1234567890",
        text = "Test message"
      )

      recoverToSucceededIf[RuntimeException] {
        client.postMessage(request)
      }
    }
  }
}