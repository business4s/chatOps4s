package chatops4s.slack

import cats.effect.IO
import chatops4s.slack.models.*
import cats.effect.unsafe.implicits.global
import io.circe.syntax.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SlackClientTest extends AnyFreeSpec with Matchers {

  "SlackClient" - {
    "postMessage should work" in {
      val config = SlackConfig("test-token", "test-secret")
      val backend = new MockBackend()
      val client = new SlackClient(config, backend)

      val response = SlackPostMessageResponse(
        ok = true,
        channel = Some("C123"),
        ts = Some("1234567890.123")
      )

      backend.setResponse("chat.postMessage", response.asJson.noSpaces)

      val request = SlackPostMessageRequest("C123", "test message")
      val result = client.postMessage(request).unsafeRunSync()

      result.ok shouldBe true
      result.channel shouldBe Some("C123")
    }

    "postMessageToThread should work" in {
      val config = SlackConfig("test-token", "test-secret")
      val backend = new MockBackend()
      val client = new SlackClient(config, backend)

      val response = SlackPostMessageResponse(
        ok = true,
        channel = Some("C123"),
        ts = Some("1234567891.456")
      )

      backend.setResponse("chat.postMessage", response.asJson.noSpaces)

      val result = client.postMessageToThread("C123", "1234567890.123", "thread reply").unsafeRunSync()

      result.ok shouldBe true
    }
  }
}