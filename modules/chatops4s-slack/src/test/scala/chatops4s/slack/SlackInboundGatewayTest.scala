package chatops4s.slack

import cats.effect.IO
import chatops4s.InteractionContext
import chatops4s.slack.models.*
import cats.effect.unsafe.implicits.global
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SlackInboundGatewayTest extends AnyFreeSpec with Matchers {

  "SlackInboundGateway" - {
    "registerAction should work" in {
      val gateway = SlackInboundGateway.create.unsafeRunSync()
      var capturedContext: Option[InteractionContext] = None

      val handler = (ctx: InteractionContext) => IO {
        capturedContext = Some(ctx)
      }

      val buttonInteraction = gateway.registerAction(handler).unsafeRunSync()
      val button = buttonInteraction.render("Test Button")

      val payload = SlackInteractionPayload(
        `type` = "block_actions",
        user = SlackUser("U123", "testuser"),
        container = SlackContainer("message", Some("1234567890.123")),
        trigger_id = "trigger123",
        team = SlackTeam("T123", "testteam"),
        channel = SlackChannel("C123", "general"),
        actions = Some(List(
          SlackAction(
            action_id = button.value,
            text = SlackText("plain_text", button.label),
            value = Some(button.value),
            `type` = "button",
            action_ts = "1234567890"
          )
        ))
      )

      gateway.handleInteraction(payload).unsafeRunSync()

      capturedContext shouldBe defined
      capturedContext.get.userId shouldBe "U123"
      capturedContext.get.channelId shouldBe "C123"
      capturedContext.get.messageId shouldBe "1234567890.123"
    }

    "handleInteraction should ignore unknown actions" in {
      val gateway = SlackInboundGateway.create.unsafeRunSync()
      var capturedContext: Option[InteractionContext] = None

      val handler = (ctx: InteractionContext) => IO {
        capturedContext = Some(ctx)
      }

      gateway.registerAction(handler).unsafeRunSync()

      val payload = SlackInteractionPayload(
        `type` = "block_actions",
        user = SlackUser("U123", "testuser"),
        container = SlackContainer("message", Some("1234567890.123")),
        trigger_id = "trigger123",
        team = SlackTeam("T123", "testteam"),
        channel = SlackChannel("C123", "general"),
        actions = Some(List(
          SlackAction(
            action_id = "unknown_action",
            text = SlackText("plain_text", "Unknown"),
            value = Some("unknown"),
            `type` = "button",
            action_ts = "1234567890"
          )
        ))
      )

      gateway.handleInteraction(payload).unsafeRunSync()

      capturedContext shouldBe None
    }

    "handleInteraction should work with no actions" in {
      val gateway = SlackInboundGateway.create.unsafeRunSync()

      val payload = SlackInteractionPayload(
        `type` = "view_submission",
        user = SlackUser("U123", "testuser"),
        container = SlackContainer("view"),
        trigger_id = "trigger123",
        team = SlackTeam("T123", "testteam"),
        channel = SlackChannel("C123", "general"),
        actions = None
      )

      // Should not throw
      gateway.handleInteraction(payload).unsafeRunSync()
    }
  }
}