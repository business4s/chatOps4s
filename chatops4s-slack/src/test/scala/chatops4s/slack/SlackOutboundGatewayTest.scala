package chatops4s.slack

import cats.effect.unsafe.implicits.global
import chatops4s.slack.models.*
import chatops4s.{Button, Message}
import io.circe.syntax.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SlackOutboundGatewayTest extends AnyFreeSpec with Matchers {

  "SlackOutboundGateway" - {
    "sendToChannel should work with simple message" in {
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

      val client  = new SlackClient(config, backend)
      val gateway = SlackOutboundGateway.create(client).unsafeRunSync()
      val message = Message("Hello World")
      val result  = gateway.sendToChannel("C123", message).unsafeRunSync()

      result.messageId shouldBe "C123-1234567890.123"
    }

    "sendToChannel should work with interactive buttons" in {
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

      val client  = new SlackClient(config, backend)
      val gateway = SlackOutboundGateway.create(client).unsafeRunSync()
      val message = Message(
        text = "Deploy to production?",
        interactions = Seq(
          Button("Accept", "accept_123"),
          Button("Decline", "decline_123"),
        ),
      )
      val result  = gateway.sendToChannel("C123", message).unsafeRunSync()

      result.messageId shouldBe "C123-1234567890.123"
    }

    "sendToChannel should work with multiple buttons" in {
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

      val client  = new SlackClient(config, backend)
      val gateway = SlackOutboundGateway.create(client).unsafeRunSync()
      val message = Message(
        text = "Choose an option:",
        interactions = (1 to 5).map(i => Button(s"Option $i", s"option_$i")),
      )
      val result  = gateway.sendToChannel("C123", message).unsafeRunSync()

      result.messageId shouldBe "C123-1234567890.123"
    }

    "sendToThread should work" in {
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

      val client  = new SlackClient(config, backend)
      val gateway = SlackOutboundGateway.create(client).unsafeRunSync()
      val message = Message("Thread reply message")
      val result  = gateway.sendToThread("C123-1234567890.123", message).unsafeRunSync()

      result.messageId shouldBe "C123-1234567891.456"
    }

    "sendToThread should work with buttons" in {
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

      val client  = new SlackClient(config, backend)
      val gateway = SlackOutboundGateway.create(client).unsafeRunSync()
      val message = Message(
        text = "Thread reply with button",
        interactions = Seq(Button("Acknowledge", "ack_thread")),
      )
      val result  = gateway.sendToThread("C123-1234567890.123", message).unsafeRunSync()

      result.messageId shouldBe "C123-1234567891.456"
    }

    "should handle API errors properly" in {
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

      val client  = new SlackClient(config, backend)
      val gateway = SlackOutboundGateway.create(client).unsafeRunSync()
      val message = Message("Test message")

      val exception = intercept[RuntimeException] {
        gateway.sendToChannel("C123", message).unsafeRunSync()
      }

      exception.getMessage should include("invalid_auth")
    }

    "should handle missing timestamp in response" in {
      val config   = SlackConfig("test-token", "test-secret")
      val response = SlackPostMessageResponse(
        ok = true,
        channel = Some("C123"),
        ts = None, // Missing timestamp
      )

      val backend = MockBackend.withResponse(
        MockBackend.create(),
        "chat.postMessage",
        response.asJson.noSpaces,
      )

      val client  = new SlackClient(config, backend)
      val gateway = SlackOutboundGateway.create(client).unsafeRunSync()
      val message = Message("Test message")

      assertThrows[RuntimeException] {
        gateway.sendToChannel("C123", message).unsafeRunSync()
      }
    }

    "should handle malformed message ID in sendToThread" in {
      val config  = SlackConfig("test-token", "test-secret")
      val backend = MockBackend.create()

      val client  = new SlackClient(config, backend)
      val gateway = SlackOutboundGateway.create(client).unsafeRunSync()
      val message = Message("Thread reply")

      assertThrows[IllegalArgumentException] {
        gateway.sendToThread("invalid-message-id", message).unsafeRunSync()
      }
    }

    "should work with empty interactions list" in {
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

      val client  = new SlackClient(config, backend)
      val gateway = SlackOutboundGateway.create(client).unsafeRunSync()
      val message = Message(
        text = "Simple message",
        interactions = Seq.empty,
      )
      val result  = gateway.sendToChannel("C123", message).unsafeRunSync()

      result.messageId shouldBe "C123-1234567890.123"
    }
  }
}
