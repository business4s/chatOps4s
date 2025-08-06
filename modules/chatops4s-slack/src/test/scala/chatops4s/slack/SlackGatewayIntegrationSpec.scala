package chatops4s.slack

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import chatops4s.{Button, Message}
import chatops4s.slack.models.*
import io.circe.syntax.*
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.*
import sttp.client4.testing.*
import sttp.model.StatusCode

class SlackGatewayIntegrationSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  "SlackOutboundGateway" - {

    "should convert ChatOps4s Message to Slack format and send successfully" in {
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

      val slackClient = new SlackClient(config, backend)

      SlackOutboundGateway.create(slackClient).flatMap { outboundGateway =>
        val message = Message(
          text = "Deploy to production?",
          interactions = Seq(
            Button("Approve", "approve_action"),
            Button("Decline", "decline_action"),
          ),
        )

        outboundGateway.sendToChannel("C1234567890", message).asserting { response =>
          response.messageId shouldBe "C1234567890-1234567890.123456"
        }
      }
    }

    "should handle simple text messages without interactions" in {
      val config = SlackConfig(botToken = "xoxb-test-token", signingSecret = "test-secret")

      val mockResponse = SlackPostMessageResponse(
        ok = true,
        channel = Some("C1234567890"),
        ts = Some("1234567890.123456"),
      )

      val backend = BackendStub[IO]
        .whenRequestMatches(_.uri.toString().contains("chat.postMessage"))
        .thenRespondAdjust(mockResponse.asJson.noSpaces)

      val slackClient = new SlackClient(config, backend)
      SlackOutboundGateway.create(slackClient).flatMap { outboundGateway =>
        val message = Message(text = "Simple message")

        outboundGateway.sendToChannel("C1234567890", message).asserting { response =>
          response.messageId shouldBe "C1234567890-1234567890.123456"
        }
      }
    }

    "should send thread replies correctly" in {
      val config = SlackConfig(botToken = "xoxb-test-token", signingSecret = "test-secret")

      val mockResponse = SlackPostMessageResponse(
        ok = true,
        channel = Some("C1234567890"),
        ts = Some("1234567891.123456"),
      )

      val backend = BackendStub[IO]
        .whenRequestMatches(_.uri.toString().contains("chat.postMessage"))
        .thenRespondAdjust(mockResponse.asJson.noSpaces)

      val slackClient = new SlackClient(config, backend)
      SlackOutboundGateway.create(slackClient).flatMap { outboundGateway =>
        val message = Message(text = "Thanks for your feedback")

        outboundGateway.sendToThread("C1234567890-1234567890.123456", message).asserting { response =>
          response.messageId shouldBe "C1234567890-1234567891.123456"
        }
      }
    }

    "should handle Slack API errors gracefully" in {
      val config = SlackConfig(botToken = "xoxb-invalid-token", signingSecret = "test-secret")

      val errorResponse = SlackPostMessageResponse(
        ok = false,
        error = Some("invalid_auth"),
      )

      val backend = BackendStub[IO]
        .whenRequestMatches(_.uri.toString().contains("chat.postMessage"))
        .thenRespondAdjust(errorResponse.asJson.noSpaces)

      val slackClient = new SlackClient(config, backend)
      SlackOutboundGateway.create(slackClient).flatMap { outboundGateway =>
        val message = Message(text = "Test message")

        outboundGateway.sendToChannel("C1234567890", message).attempt.asserting { result =>
          result.isLeft shouldBe true
          result.left.toOption.get shouldBe a[RuntimeException]
          result.left.toOption.get.getMessage should include("Slack API error: invalid_auth")
        }
      }
    }

    "should handle network errors properly" in {
      val config = SlackConfig(botToken = "xoxb-test-token", signingSecret = "test-secret")

      val backend = BackendStub[IO]
        .whenRequestMatches(_.uri.toString().contains("chat.postMessage"))
        .thenRespondAdjust(Response("Internal Server Error", StatusCode.InternalServerError))

      val slackClient = new SlackClient(config, backend)
      SlackOutboundGateway.create(slackClient).flatMap { outboundGateway =>
        val message = Message(text = "Test message")

        outboundGateway.sendToChannel("C1234567890", message).attempt.asserting { result =>
          result.isLeft shouldBe true
          result.left.toOption.get shouldBe a[RuntimeException]
          result.left.toOption.get.getMessage should include("Failed to send message")
        }
      }
    }

    "should correctly format messages with multiple buttons" in {
      val config = SlackConfig(botToken = "xoxb-test-token", signingSecret = "test-secret")

      val mockResponse = SlackPostMessageResponse(
        ok = true,
        channel = Some("C1234567890"),
        ts = Some("1234567890.123456"),
      )

      val backend = BackendStub[IO]
        .whenRequestMatches { request =>
          request.uri.toString().contains("chat.postMessage") &&
            request.body.toString.contains("actions") &&
            request.body.toString.contains("Approve") &&
            request.body.toString.contains("Decline") &&
            request.body.toString.contains("Maybe")
        }
        .thenRespondAdjust(mockResponse.asJson.noSpaces)

      val slackClient = new SlackClient(config, backend)
      SlackOutboundGateway.create(slackClient).flatMap { outboundGateway =>
        val message = Message(
          text = "What should we do?",
          interactions = Seq(
            Button("Approve", "approve_action"),
            Button("Decline", "decline_action"),
            Button("Maybe", "maybe_action"),
          ),
        )

        outboundGateway.sendToChannel("C1234567890", message).asserting { response =>
          response.messageId shouldBe "C1234567890-1234567890.123456"
        }
      }
    }

    "should validate thread message contains thread_ts" in {
      val config = SlackConfig(botToken = "xoxb-test-token", signingSecret = "test-secret")

      val mockResponse = SlackPostMessageResponse(
        ok = true,
        channel = Some("C1234567890"),
        ts = Some("1234567891.123456"),
      )

      val backend = BackendStub[IO]
        .whenRequestMatches { request =>
          request.uri.toString().contains("chat.postMessage") &&
            request.body.toString.contains("thread_ts") &&
            request.body.toString.contains("1234567890.123456")
        }
        .thenRespondAdjust(mockResponse.asJson.noSpaces)

      val slackClient = new SlackClient(config, backend)
      SlackOutboundGateway.create(slackClient).flatMap { outboundGateway =>
        val message = Message(text = "Thread reply message")

        outboundGateway.sendToThread("C1234567890-1234567890.123456", message).asserting { response =>
          response.messageId shouldBe "C1234567890-1234567891.123456"
        }
      }
    }

    "should handle missing timestamp in response" in {
      val config = SlackConfig(botToken = "xoxb-test-token", signingSecret = "test-secret")

      val mockResponse = SlackPostMessageResponse(
        ok = true,
        channel = Some("C1234567890"),
        ts = None, // Missing timestamp
      )

      val backend = BackendStub[IO]
        .whenRequestMatches(_.uri.toString().contains("chat.postMessage"))
        .thenRespondAdjust(mockResponse.asJson.noSpaces)

      val slackClient = new SlackClient(config, backend)
      SlackOutboundGateway.create(slackClient).flatMap { outboundGateway =>
        val message = Message(text = "Test message")

        outboundGateway.sendToChannel("C1234567890", message).attempt.asserting { result =>
          result.isLeft shouldBe true
          result.left.toOption.get shouldBe a[RuntimeException]
          result.left.toOption.get.getMessage should include("No message timestamp returned")
        }
      }
    }

    "should handle invalid message ID format in thread replies" in {
      val config = SlackConfig(botToken = "xoxb-test-token", signingSecret = "test-secret")

      val backend = BackendStub[IO]
        .whenRequestMatches(_.uri.toString().contains("chat.postMessage"))
        .thenRespondAdjust("should not be called")

      val slackClient = new SlackClient(config, backend)
      SlackOutboundGateway.create(slackClient).flatMap { outboundGateway =>
        val message = Message(text = "Thread reply message")

        outboundGateway.sendToThread("invalid-format", message).attempt.asserting { result =>
          result.isLeft shouldBe true
          result.left.toOption.get shouldBe an[IllegalArgumentException]
          result.left.toOption.get.getMessage should include("Invalid message ID format")
        }
      }
    }
  }

  "SlackInboundGateway" - {

    "should register actions and handle interactions correctly" in {
      SlackInboundGateway.create.flatMap { inboundGateway =>
        var capturedContext: Option[chatops4s.InteractionContext] = None

        val handler: chatops4s.InteractionContext => IO[Unit] = { ctx =>
          IO {
            capturedContext = Some(ctx)
          }
        }

        for {
          buttonInteraction <- inboundGateway.registerAction(handler)
          button             = buttonInteraction.render("Test Button")

          payload = SlackInteractionPayload(
            `type` = "block_actions",
            user = SlackUser(id = "U123456", name = "testuser"),
            container = SlackContainer(`type` = "message", message_ts = Some("1234567890.123456")),
            trigger_id = "trigger123",
            team = SlackTeam(id = "T123456", domain = "testteam"),
            channel = SlackChannel(id = "C123456", name = "general"),
            actions = Some(
              List(
                SlackAction(
                  action_id = button.value,
                  text = SlackText(`type` = "plain_text", text = button.label),
                  value = Some(button.value),
                  `type` = "button",
                  action_ts = "1234567890",
                ),
              ),
            ),
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

    "should handle multiple actions in a single interaction" in {
      SlackInboundGateway.create.flatMap { inboundGateway =>
        var capturedContexts: List[chatops4s.InteractionContext] = List.empty

        val handler: chatops4s.InteractionContext => IO[Unit] = { ctx =>
          IO {
            capturedContexts = capturedContexts :+ ctx
          }
        }

        for {
          buttonInteraction1 <- inboundGateway.registerAction(handler)
          buttonInteraction2 <- inboundGateway.registerAction(handler)
          button1             = buttonInteraction1.render("Button 1")
          button2             = buttonInteraction2.render("Button 2")

          payload = SlackInteractionPayload(
            `type` = "block_actions",
            user = SlackUser(id = "U123456", name = "testuser"),
            container = SlackContainer(`type` = "message", message_ts = Some("1234567890.123456")),
            trigger_id = "trigger123",
            team = SlackTeam(id = "T123456", domain = "testteam"),
            channel = SlackChannel(id = "C123456", name = "general"),
            actions = Some(
              List(
                SlackAction(
                  action_id = button1.value,
                  text = SlackText(`type` = "plain_text", text = button1.label),
                  value = Some(button1.value),
                  `type` = "button",
                  action_ts = "1234567890",
                ),
                SlackAction(
                  action_id = button2.value,
                  text = SlackText(`type` = "plain_text", text = button2.label),
                  value = Some(button2.value),
                  `type` = "button",
                  action_ts = "1234567891",
                ),
              ),
            ),
          )

          _ <- inboundGateway.handleInteraction(payload)
        } yield {
          capturedContexts should have length 2
          capturedContexts.forall(_.userId == "U123456") shouldBe true
          capturedContexts.forall(_.channelId == "C123456") shouldBe true
          capturedContexts.forall(_.messageId == "1234567890.123456") shouldBe true
        }
      }
    }

    "should ignore unknown actions" in {
      SlackInboundGateway.create.flatMap { inboundGateway =>
        var capturedContext: Option[chatops4s.InteractionContext] = None

        val handler: chatops4s.InteractionContext => IO[Unit] = { ctx =>
          IO {
            capturedContext = Some(ctx)
          }
        }

        for {
          _ <- inboundGateway.registerAction(handler)

          payload = SlackInteractionPayload(
            `type` = "block_actions",
            user = SlackUser(id = "U123456", name = "testuser"),
            container = SlackContainer(`type` = "message", message_ts = Some("1234567890.123456")),
            trigger_id = "trigger123",
            team = SlackTeam(id = "T123456", domain = "testteam"),
            channel = SlackChannel(id = "C123456", name = "general"),
            actions = Some(
              List(
                SlackAction(
                  action_id = "unknown_action_id",
                  text = SlackText(`type` = "plain_text", text = "Unknown Button"),
                  value = Some("unknown_value"),
                  `type` = "button",
                  action_ts = "1234567890",
                ),
              ),
            ),
          )

          _ <- inboundGateway.handleInteraction(payload)
        } yield {
          capturedContext shouldBe None
        }
      }
    }

    "should handle interactions without actions" in {
      SlackInboundGateway.create.flatMap { inboundGateway =>
        val payload = SlackInteractionPayload(
          `type` = "view_submission",
          user = SlackUser(id = "U123456", name = "testuser"),
          container = SlackContainer(`type` = "view"),
          trigger_id = "trigger123",
          team = SlackTeam(id = "T123456", domain = "testteam"),
          channel = SlackChannel(id = "C123456", name = "general"),
          actions = None,
        )
        inboundGateway.handleInteraction(payload).asserting(_ => succeed)
      }
    }

    "should handle empty actions list" in {
      SlackInboundGateway.create.flatMap { inboundGateway =>
        val payload = SlackInteractionPayload(
          `type` = "block_actions",
          user = SlackUser(id = "U123456", name = "testuser"),
          container = SlackContainer(`type` = "message", message_ts = Some("1234567890.123456")),
          trigger_id = "trigger123",
          team = SlackTeam(id = "T123456", domain = "testteam"),
          channel = SlackChannel(id = "C123456", name = "general"),
          actions = Some(List.empty),
        )
        inboundGateway.handleInteraction(payload).asserting(_ => succeed)
      }
    }

    "should handle container without message_ts" in {
      SlackInboundGateway.create.flatMap { inboundGateway =>
        var capturedContext: Option[chatops4s.InteractionContext] = None

        val handler: chatops4s.InteractionContext => IO[Unit] = { ctx =>
          IO {
            capturedContext = Some(ctx)
          }
        }

        for {
          buttonInteraction <- inboundGateway.registerAction(handler)
          button             = buttonInteraction.render("Test Button")

          payload = SlackInteractionPayload(
            `type` = "block_actions",
            user = SlackUser(id = "U123456", name = "testuser"),
            container = SlackContainer(`type` = "view", message_ts = None),
            trigger_id = "trigger123",
            team = SlackTeam(id = "T123456", domain = "testteam"),
            channel = SlackChannel(id = "C123456", name = "general"),
            actions = Some(
              List(
                SlackAction(
                  action_id = button.value,
                  text = SlackText(`type` = "plain_text", text = button.label),
                  value = Some(button.value),
                  `type` = "button",
                  action_ts = "1234567890",
                ),
              ),
            ),
          )

          _ <- inboundGateway.handleInteraction(payload)
        } yield {
          capturedContext shouldBe defined
          capturedContext.get.userId shouldBe "U123456"
          capturedContext.get.channelId shouldBe "C123456"
          capturedContext.get.messageId shouldBe "" // Empty when message_ts is None
        }
      }
    }

    "should create unique action IDs for different button interactions" in {
      SlackInboundGateway.create.flatMap { inboundGateway =>
        for {
          buttonInteraction1 <- inboundGateway.registerAction(_ => IO.unit)
          buttonInteraction2 <- inboundGateway.registerAction(_ => IO.unit)
          button1             = buttonInteraction1.render("Button 1")
          button2             = buttonInteraction2.render("Button 2")
        } yield {
          button1.value should not be button2.value
          button1.label shouldBe "Button 1"
          button2.label shouldBe "Button 2"
        }
      }
    }
  }
}