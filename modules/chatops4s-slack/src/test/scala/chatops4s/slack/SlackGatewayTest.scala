package chatops4s.slack

import cats.effect.IO
import chatops4s.{Message, OutboundGateway}
import chatops4s.slack.models.*
import cats.effect.unsafe.implicits.global
import io.circe.syntax.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.StatusCode

class SlackGatewayTest extends AnyFreeSpec with Matchers {

  "SlackGateway" - {
    "create should work" in {
      val config = SlackConfig("test-token", "test-secret")
      val backend = new MockBackend()

      val response = SlackPostMessageResponse(
        ok = true,
        channel = Some("C123"),
        ts = Some("1234567890.123")
      )

      backend.setResponse("chat.postMessage", response.asJson.noSpaces)

      val (outbound, inbound) = SlackGateway.create(config, backend).use { gateways =>
        IO.pure(gateways)
      }.unsafeRunSync()

      outbound shouldBe a[OutboundGateway]
      inbound shouldBe a[SlackInboundGateway]
    }

    "createOutboundOnly should work" in {
      val config = SlackConfig("test-token", "test-secret")
      val backend = new MockBackend()

      val response = SlackPostMessageResponse(
        ok = true,
        channel = Some("C123"),
        ts = Some("1234567890.123")
      )

      backend.setResponse("chat.postMessage", response.asJson.noSpaces)

      val outbound = SlackGateway.createOutboundOnly(config, backend).use { gateway =>
        val message = Message("Test message")
        gateway.sendToChannel("C123", message).map(_ => gateway)
      }.unsafeRunSync()

      outbound shouldBe a[OutboundGateway]
    }
  }
}