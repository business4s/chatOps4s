package chatops4s.slack

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chatops4s.InteractionContext
import chatops4s.slack.instances.given
import chatops4s.slack.models.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SlackInboundGatewayTest extends AnyFreeSpec with Matchers {

  "SlackInboundGateway" - {
    "registerAction should create button interaction" in {
      val gateway                                     = SlackInboundGateway.create[IO].unsafeRunSync()
      var capturedContext: Option[InteractionContext] = None

      val handler = (ctx: InteractionContext) =>
        IO {
          capturedContext = Some(ctx)
        }

      val buttonInteraction = gateway.registerAction(handler).unsafeRunSync()
      val button            = buttonInteraction.render("Test Button")

      button.label shouldBe "Test Button"
      button.value should not be empty
    }

    "handleInteraction should execute registered action" in {
      val gateway                                     = SlackInboundGateway.create[IO].unsafeRunSync()
      var capturedContext: Option[InteractionContext] = None

      val handler = (ctx: InteractionContext) =>
        IO {
          capturedContext = Some(ctx)
        }

      val buttonInteraction = gateway.registerAction(handler).unsafeRunSync()
      val button            = buttonInteraction.render("Test Button")

      val payload = SlackInteractionPayload(
        `type` = "block_actions",
        user = SlackUser("U123", "testuser"),
        container = SlackContainer("message", Some("1234567890.123")),
        trigger_id = "trigger123",
        team = SlackTeam("T123", "testteam"),
        channel = SlackChannel("C123", "general"),
        actions = Some(
          List(
            SlackAction(
              action_id = button.value,
              text = SlackText("plain_text", button.label),
              value = Some(button.value),
              `type` = "button",
              action_ts = "1234567890",
            ),
          ),
        ),
      )

      gateway.handleInteraction(payload).unsafeRunSync()

      capturedContext shouldBe defined
      capturedContext.get.userId shouldBe "U123"
      capturedContext.get.channelId shouldBe "C123"
      capturedContext.get.messageId shouldBe "1234567890.123"
    }

    "handleInteraction should ignore unknown actions" in {
      val gateway                                     = SlackInboundGateway.create[IO].unsafeRunSync()
      var capturedContext: Option[InteractionContext] = None

      val handler = (ctx: InteractionContext) =>
        IO {
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
        actions = Some(
          List(
            SlackAction(
              action_id = "unknown_action_id",
              text = SlackText("plain_text", "Unknown"),
              value = Some("unknown"),
              `type` = "button",
              action_ts = "1234567890",
            ),
          ),
        ),
      )

      gateway.handleInteraction(payload).unsafeRunSync()

      capturedContext shouldBe None
    }

    "handleInteraction should work with no actions" in {
      val gateway = SlackInboundGateway.create[IO].unsafeRunSync()

      val payload = SlackInteractionPayload(
        `type` = "view_submission",
        user = SlackUser("U123", "testuser"),
        container = SlackContainer("view"),
        trigger_id = "trigger123",
        team = SlackTeam("T123", "testteam"),
        channel = SlackChannel("C123", "general"),
        actions = None,
      )

      gateway.handleInteraction(payload).unsafeRunSync()
    }

    "should handle multiple actions in one payload" in {
      val gateway        = SlackInboundGateway.create[IO].unsafeRunSync()
      var executionCount = 0

      val handler1 = (ctx: InteractionContext) => IO { executionCount += 1 }
      val handler2 = (ctx: InteractionContext) => IO { executionCount += 10 }

      val button1 = gateway.registerAction(handler1).unsafeRunSync().render("Button 1")
      val button2 = gateway.registerAction(handler2).unsafeRunSync().render("Button 2")

      val payload = SlackInteractionPayload(
        `type` = "block_actions",
        user = SlackUser("U123", "testuser"),
        container = SlackContainer("message", Some("1234567890.123")),
        trigger_id = "trigger123",
        team = SlackTeam("T123", "testteam"),
        channel = SlackChannel("C123", "general"),
        actions = Some(
          List(
            SlackAction(
              action_id = button1.value,
              text = SlackText("plain_text", button1.label),
              value = Some(button1.value),
              `type` = "button",
              action_ts = "1234567890",
            ),
            SlackAction(
              action_id = button2.value,
              text = SlackText("plain_text", button2.label),
              value = Some(button2.value),
              `type` = "button",
              action_ts = "1234567891",
            ),
          ),
        ),
      )

      gateway.handleInteraction(payload).unsafeRunSync()

      executionCount shouldBe 11 // 1 + 10
    }

    "should handle concurrent registrations" in {
      val gateway = SlackInboundGateway.create[IO].unsafeRunSync()

      val registrations = (1 to 10).map { i =>
        IO.delay {
          val handler = (ctx: InteractionContext) => IO.unit
          gateway.registerAction(handler)
        }
      }

      val buttons = registrations.map(_.unsafeRunSync().unsafeRunSync())

      buttons.map(_.render("Test").value).toSet.size shouldBe 10 // All should have unique IDs
    }
  }
}
