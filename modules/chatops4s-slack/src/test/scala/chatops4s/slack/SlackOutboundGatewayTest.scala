package chatops4s.slack

import cats.effect.IO
import chatops4s.{Button, Message}
import chatops4s.slack.models.*
import cats.effect.unsafe.implicits.global
import io.circe.syntax.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.*
import sttp.model.StatusCode

class SlackOutboundGatewayTest extends AnyFreeSpec with Matchers {

  "SlackOutboundGateway" - {
    "sendToChannel should work with simple message" in {
      val config = SlackConfig("test-token", "test-secret")
      val backend = new MockBackend()
      val client = new SlackClient(config, backend)

      val response = SlackPostMessageResponse(
        ok = true,
        channel = Some("C123"),
        ts = Some("1234567890.123")
      )

      backend.setResponse("chat.postMessage", response.asJson.noSpaces)

      val gateway = SlackOutboundGateway.create(client).unsafeRunSync()
      val message = Message("Hello World")
      val result = gateway.sendToChannel("C123", message).unsafeRunSync()

      result.messageId shouldBe "C123-1234567890.123"
    }

    "sendToChannel should work with interactive buttons" in {
      val config = SlackConfig("test-token", "test-secret")
      val backend = new MockBackend()
      val client = new SlackClient(config, backend)

      val response = SlackPostMessageResponse(
        ok = true,
        channel = Some("C123"),
        ts = Some("1234567890.123")
      )

      backend.setResponse("chat.postMessage", response.asJson.noSpaces)

      val gateway = SlackOutboundGateway.create(client).unsafeRunSync()
      val message = Message(
        text = "Deploy?",
        interactions = Seq(
          Button("Accept", "accept_123"),
          Button("Decline", "decline_123")
        )
      )
      val result = gateway.sendToChannel("C123", message).unsafeRunSync()

      result.messageId shouldBe "C123-1234567890.123"
    }

    "sendToThread should work" in {
      val config = SlackConfig("test-token", "test-secret")
      val backend = new MockBackend()
      val client = new SlackClient(config, backend)

      val response = SlackPostMessageResponse(
        ok = true,
        channel = Some("C123"),
        ts = Some("1234567891.456")
      )

      backend.setResponse("chat.postMessage", response.asJson.noSpaces)

      val gateway = SlackOutboundGateway.create(client).unsafeRunSync()
      val message = Message("Thread reply")
      val result = gateway.sendToThread("C123-1234567890.123", message).unsafeRunSync()

      result.messageId shouldBe "C123-1234567891.456"
    }

    "should handle API errors" in {
      val config = SlackConfig("invalid-token", "test-secret")
      val backend = new MockBackend()
      val client = new SlackClient(config, backend)

      val errorResponse = SlackPostMessageResponse(
        ok = false,
        error = Some("invalid_auth")
      )

      backend.setResponse("chat.postMessage", errorResponse.asJson.noSpaces)

      val gateway = SlackOutboundGateway.create(client).unsafeRunSync()
      val message = Message("Test")

      assertThrows[RuntimeException] {
        gateway.sendToChannel("C123", message).unsafeRunSync()
      }
    }
  }
}