package chatops4s.slack

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.traverse.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.testing.WebSocketBackendStub

class SlackGatewayTest extends AnyFreeSpec with Matchers {

  private val okPostMessage = """{"ok":true,"channel":"C123","ts":"1234567890.123"}"""

  private def createGateway(
      backend: WebSocketBackendStub[IO] = MockBackend.create(),
  ): SlackGatewayImpl[IO] = {
    given sttp.monad.MonadError[IO] = backend.monad
    val handlersRef                 = Ref.of[IO, Map[String, ErasedHandler[IO]]](Map.empty).unsafeRunSync()
    val commandHandlersRef          = Ref.of[IO, Map[String, CommandEntry[IO]]](Map.empty).unsafeRunSync()
    val client                      = new SlackClient[IO]("test-token", backend)
    new SlackGatewayImpl[IO](client, handlersRef, commandHandlersRef, backend)
  }

  "SlackGateway" - {

    "send" - {
      "should send a simple message" in {
        val gateway = createGateway(MockBackend.withPostMessage(okPostMessage))

        val result = gateway.send("C123", "Hello World").unsafeRunSync()

        result shouldBe MessageId("C123", "1234567890.123")
      }

      "should send a message with buttons" in {
        val gateway = createGateway(MockBackend.withPostMessage(okPostMessage))
        val approve = gateway.registerButton[String](_ => IO.unit).unsafeRunSync()
        val reject  = gateway.registerButton[String](_ => IO.unit).unsafeRunSync()

        val result = gateway
          .send(
            "C123",
            "Deploy?",
            Seq(
              Button("Approve", approve, approve.value),
              Button("Reject", reject, reject.value),
            ),
          )
          .unsafeRunSync()

        result shouldBe MessageId("C123", "1234567890.123")
      }

      "should handle API errors" in {
        val errorBody = """{"ok":false,"error":"invalid_auth"}"""
        val gateway   = createGateway(MockBackend.withPostMessage(errorBody))

        val ex = intercept[chatops4s.slack.api.SlackApiError] {
          gateway.send("C123", "Test").unsafeRunSync()
        }
        ex.error shouldBe "invalid_auth"
      }
    }

    "reply" - {
      "should reply in thread" in {
        val body    = """{"ok":true,"channel":"C123","ts":"1234567891.456"}"""
        val gateway = createGateway(MockBackend.withPostMessage(body))

        val result = gateway.reply(MessageId("C123", "1234567890.123"), "Thread reply").unsafeRunSync()

        result shouldBe MessageId("C123", "1234567891.456")
      }

      "should reply in thread with buttons" in {
        val body    = """{"ok":true,"channel":"C123","ts":"1234567891.456"}"""
        val gateway = createGateway(MockBackend.withPostMessage(body))
        val btn     = gateway.registerButton[String](_ => IO.unit).unsafeRunSync()

        val result = gateway
          .reply(
            MessageId("C123", "1234567890.123"),
            "Confirm?",
            Seq(Button("OK", btn, btn.value)),
          )
          .unsafeRunSync()

        result shouldBe MessageId("C123", "1234567891.456")
      }
    }

    "update" - {
      "should update a message" in {
        val gateway = createGateway(
          MockBackend.withUpdate(
            """{"ok":true,"channel":"C123","ts":"1234567890.123"}""",
          ),
        )

        val msgId  = MessageId("C123", "1234567890.123")
        val result = gateway.update(msgId, "Updated text").unsafeRunSync()

        result shouldBe msgId
      }

      "should handle API errors on update" in {
        val errorBody = """{"ok":false,"error":"message_not_found"}"""
        val gateway   = createGateway(MockBackend.withUpdate(errorBody))

        val msgId = MessageId("C123", "1234567890.123")
        val ex    = intercept[chatops4s.slack.api.SlackApiError] {
          gateway.update(msgId, "Updated text").unsafeRunSync()
        }
        ex.error shouldBe "message_not_found"
      }
    }

    "onButton" - {
      "should generate unique button IDs" in {
        val gateway = createGateway()

        val ids = (1 to 10).toList.traverse(_ => gateway.registerButton[String](_ => IO.unit)).unsafeRunSync()

        ids.map(_.value).toSet.size shouldBe 10
      }
    }

    "interaction handling" - {
      "should dispatch button click to registered handler" in {
        val gateway                               = createGateway()
        var captured: Option[ButtonClick[String]] = None

        val btnId = gateway
          .registerButton[String] { click =>
            IO { captured = Some(click) }
          }
          .unsafeRunSync()

        val payload = interactionPayload(btnId.value, "my-value")
        gateway.handleInteractionPayload(payload).unsafeRunSync()

        captured shouldBe defined
        captured.get.userId shouldBe "U123"
        captured.get.messageId shouldBe MessageId("C123", "1234567890.123")
        captured.get.value shouldBe "my-value"
      }

      "should ignore unknown action IDs" in {
        val gateway = createGateway()
        var called  = false

        gateway.registerButton[String] { _ => IO { called = true } }.unsafeRunSync()

        val payload = interactionPayload("unknown_action_id", "v")
        gateway.handleInteractionPayload(payload).unsafeRunSync()

        called shouldBe false
      }

      "should dispatch multiple actions in one payload" in {
        val gateway = createGateway()
        var count   = 0

        val btn1 = gateway.registerButton[String] { _ => IO { count += 1 } }.unsafeRunSync()
        val btn2 = gateway.registerButton[String] { _ => IO { count += 10 } }.unsafeRunSync()

        val payload = SlackModels.InteractionPayload(
          `type` = "block_actions",
          user = SlackModels.User("U123"),
          channel = SlackModels.Channel("C123"),
          container = SlackModels.Container(Some("1234567890.123")),
          actions = Some(
            List(
              SlackModels.Action(btn1.value, Some("v1")),
              SlackModels.Action(btn2.value, Some("v2")),
            ),
          ),
        )
        gateway.handleInteractionPayload(payload).unsafeRunSync()

        count shouldBe 11
      }
    }

    "delete" - {
      "should delete a message" in {
        val body    = """{"ok":true,"channel":"C123","ts":"1234567890.123"}"""
        val gateway = createGateway(MockBackend.create().whenAnyRequest.thenRespondAdjust(body))

        gateway.delete(MessageId("C123", "1234567890.123")).unsafeRunSync()
      }

      "should handle API errors on delete" in {
        val errorBody = """{"ok":false,"error":"message_not_found"}"""
        val gateway   = createGateway(MockBackend.create().whenAnyRequest.thenRespondAdjust(errorBody))

        val ex = intercept[chatops4s.slack.api.SlackApiError] {
          gateway.delete(MessageId("C123", "1234567890.123")).unsafeRunSync()
        }
        ex.error shouldBe "message_not_found"
      }
    }

    "reactions" - {
      "should add a reaction" in {
        val gateway = createGateway(MockBackend.withOkApi())

        gateway.addReaction(MessageId("C123", "1234567890.123"), "rocket").unsafeRunSync()
      }

      "should remove a reaction" in {
        val gateway = createGateway(MockBackend.withOkApi())

        gateway.removeReaction(MessageId("C123", "1234567890.123"), "rocket").unsafeRunSync()
      }
    }

    "sendEphemeral" - {
      "should send an ephemeral message" in {
        val body    = """{"ok":true,"message_ts":"1234567890.123"}"""
        val gateway = createGateway(MockBackend.create().whenAnyRequest.thenRespondAdjust(body))

        gateway.sendEphemeral("C123", "U456", "Only you can see this").unsafeRunSync()
      }
    }

    "onCommand" - {
      "should dispatch slash command to registered handler" in {
        val gateway                           = createGateway(MockBackend.withResponseUrl())
        var captured: Option[Command[String]] = None

        gateway
          .registerCommand[String]("/deploy") { cmd =>
            IO { captured = Some(cmd) }.as(CommandResponse.InChannel(s"Deploying ${cmd.args}"))
          }
          .unsafeRunSync()

        val payload = slashCommandPayload("/deploy", "v1.2.3")
        gateway.handleSlashCommandPayload(payload).unsafeRunSync()

        captured shouldBe defined
        captured.get.args shouldBe "v1.2.3"
        captured.get.userId shouldBe "U123"
        captured.get.channelId shouldBe "C123"
        captured.get.text shouldBe "v1.2.3"
      }

      "should normalize command names (strip / and lowercase)" in {
        val gateway = createGateway(MockBackend.withResponseUrl())
        var called  = false

        gateway
          .registerCommand[String]("Deploy") { _ =>
            IO { called = true }.as(CommandResponse.Ephemeral("ok"))
          }
          .unsafeRunSync()

        val payload = slashCommandPayload("/deploy", "test")
        gateway.handleSlashCommandPayload(payload).unsafeRunSync()

        called shouldBe true
      }

      "should ignore unregistered commands" in {
        val gateway = createGateway()
        var called  = false

        gateway
          .registerCommand[String]("/deploy") { _ =>
            IO { called = true }.as(CommandResponse.Ephemeral("ok"))
          }
          .unsafeRunSync()

        val payload = slashCommandPayload("/rollback", "test")
        gateway.handleSlashCommandPayload(payload).unsafeRunSync()

        called shouldBe false
      }

      "should return ephemeral error on parse failure" in {
        val gateway = createGateway(MockBackend.withResponseUrl())

        given CommandParser[Int] with {
          def parse(text: String): Either[String, Int] =
            text.toIntOption.toRight(s"'$text' is not a number")
        }

        gateway
          .registerCommand[Int]("/count") { cmd =>
            IO.pure(CommandResponse.InChannel(s"Count: ${cmd.args}"))
          }
          .unsafeRunSync()

        // This should not throw - parse error is handled internally
        val payload = slashCommandPayload("/count", "not-a-number")
        gateway.handleSlashCommandPayload(payload).unsafeRunSync()
      }
    }

    "manifest" - {
      "minimal - no commands or buttons" in {
        val gateway = createGateway()

        val result = gateway.manifest("TestApp").unsafeRunSync()

        SnapshotTest.testSnapshot(result, "snapshots/manifest-minimal.yaml")
      }

      "with commands" in {
        val gateway = createGateway()

        gateway
          .registerCommand[String]("deploy", "Deploy to prod") { _ =>
            IO.pure(CommandResponse.Silent)
          }
          .unsafeRunSync()

        val result = gateway.manifest("TestApp").unsafeRunSync()

        SnapshotTest.testSnapshot(result, "snapshots/manifest-with-commands.yaml")
      }

      "with buttons" in {
        val gateway = createGateway()

        gateway.registerButton[String](_ => IO.unit).unsafeRunSync()

        val result = gateway.manifest("TestApp").unsafeRunSync()

        SnapshotTest.testSnapshot(result, "snapshots/manifest-with-buttons.yaml")
      }
    }
  }

  private def slashCommandPayload(command: String, text: String): SlackModels.SlashCommandPayload =
    SlackModels.SlashCommandPayload(
      command = command,
      text = text,
      user_id = "U123",
      channel_id = "C123",
      response_url = "https://hooks.slack.com/commands/T123/456/789",
    )

  private def interactionPayload(actionId: String, value: String): SlackModels.InteractionPayload =
    SlackModels.InteractionPayload(
      `type` = "block_actions",
      user = SlackModels.User("U123"),
      channel = SlackModels.Channel("C123"),
      container = SlackModels.Container(Some("1234567890.123")),
      actions = Some(
        List(
          SlackModels.Action(actionId, Some(value)),
        ),
      ),
    )
}
