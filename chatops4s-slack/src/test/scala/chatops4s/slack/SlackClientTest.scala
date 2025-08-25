package chatops4s.slack

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chatops4s.slack.instances.given
import chatops4s.slack.models.*
import io.circe.syntax.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.StatusCode

class SlackClientTest extends AnyFreeSpec with Matchers {

  "SlackClient" - {
    "postMessage should work" in {
      val config   = SlackConfig("test-token", "test-secret")
      val response = SlackPostMessageResponse(
        ok = true,
        channel = Some("C123"),
        ts = Some("1234567890.123"),
      )

      val backend = MockBackend.withResponse(
        MockBackend.create(),
        "chat.postMessage",
        response.asJson.noSpaces,
      )

      val client  = new SlackClient[IO](config, backend)
      val request = SlackPostMessageRequest("C123", "test message")
      val result  = client.postMessage(request).unsafeRunSync()

      result.ok shouldBe true
      result.channel shouldBe Some("C123")
      result.ts shouldBe Some("1234567890.123")
    }

    "postMessageToThread should work" in {
      val config   = SlackConfig("test-token", "test-secret")
      val response = SlackPostMessageResponse(
        ok = true,
        channel = Some("C123"),
        ts = Some("1234567891.456"),
      )

      val backend = MockBackend.withResponse(
        MockBackend.create(),
        "chat.postMessage",
        response.asJson.noSpaces,
      )

      val client = new SlackClient[IO](config, backend)
      val result = client.postMessageToThread("C123", "1234567890.123", "thread reply").unsafeRunSync()

      result.ok shouldBe true
      result.channel shouldBe Some("C123")
      result.ts shouldBe Some("1234567891.456")
    }

    "should handle API errors gracefully" in {
      val config  = SlackConfig("invalid-token", "test-secret")
      val backend = MockBackend.withResponse(
        MockBackend.create(),
        "chat.postMessage",
        "Internal Server Error",
        StatusCode.InternalServerError,
      )

      val client  = new SlackClient[IO](config, backend)
      val request = SlackPostMessageRequest("C123", "test message")

      assertThrows[RuntimeException] {
        client.postMessage(request).unsafeRunSync()
      }
    }

    "should handle Slack API errors" in {
      val config        = SlackConfig("invalid-token", "test-secret")
      val errorResponse = SlackPostMessageResponse(
        ok = false,
        error = Some("invalid_auth"),
      )

      val backend = MockBackend.withResponse(
        MockBackend.create(),
        "chat.postMessage",
        errorResponse.asJson.noSpaces,
      )

      val client  = new SlackClient[IO](config, backend)
      val request = SlackPostMessageRequest("C123", "test message")

      assertThrows[RuntimeException] {
        client.postMessage(request).unsafeRunSync()
      }
    }
  }
}
