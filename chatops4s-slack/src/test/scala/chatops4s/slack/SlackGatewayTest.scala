package chatops4s.slack

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.traverse.*
import chatops4s.slack.api
import chatops4s.slack.api.{ChannelId, Timestamp, UserId, TeamId}
import chatops4s.slack.api.socket.*
import chatops4s.slack.api.blocks.*
import io.circe.Json
import io.circe.parser
import chatops4s.slack.api.socket.ViewStateValue
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.testing.WebSocketBackendStub

case class ScaleArgs(service: String, replicas: Int) derives CommandParser

class SlackGatewayTest extends AnyFreeSpec with Matchers {

  private val okPostMessage = """{"ok":true,"channel":"C123","ts":"1234567890.123"}"""

  private def createGateway(
      backend: WebSocketBackendStub[IO] = MockBackend.create(),
  ): SlackGatewayImpl[IO] = {
    given sttp.monad.MonadError[IO] = backend.monad
    val handlersRef = Ref.of[IO, Map[ButtonId[?], ErasedHandler[IO]]](Map.empty).unsafeRunSync()
    val commandHandlersRef = Ref.of[IO, Map[CommandName, CommandEntry[IO]]](Map.empty).unsafeRunSync()
    val formHandlersRef = Ref.of[IO, Map[FormId[?], FormEntry[IO]]](Map.empty).unsafeRunSync()
    val client = new SlackClient[IO]("test-token", backend)
    new SlackGatewayImpl[IO](client, handlersRef, commandHandlersRef, formHandlersRef, backend)
  }

  "SlackGateway" - {

    "send" - {
      "should send a simple message" in {
        val gateway = createGateway(MockBackend.withPostMessage(okPostMessage))

        val result = gateway.send("C123", "Hello World").unsafeRunSync()

        result shouldBe MessageId(ChannelId("C123"), Timestamp("1234567890.123"))
      }

      "should send a message with buttons" in {
        val gateway = createGateway(MockBackend.withPostMessage(okPostMessage))
        val approve = gateway.registerButton[String](_ => IO.unit).unsafeRunSync()
        val reject = gateway.registerButton[String](_ => IO.unit).unsafeRunSync()

        val result = gateway.send("C123", "Deploy?", Seq(
          Button("Approve", approve, approve.value),
          Button("Reject", reject, reject.value),
        )).unsafeRunSync()

        result shouldBe MessageId(ChannelId("C123"), Timestamp("1234567890.123"))
      }

      "should handle API errors" in {
        val errorBody = """{"ok":false,"error":"invalid_auth"}"""
        val gateway = createGateway(MockBackend.withPostMessage(errorBody))

        val ex = intercept[chatops4s.slack.api.SlackApiError] {
          gateway.send("C123", "Test").unsafeRunSync()
        }
        ex.error shouldBe "invalid_auth"
      }
    }

    "reply" - {
      "should reply in thread" in {
        val body = """{"ok":true,"channel":"C123","ts":"1234567891.456"}"""
        val gateway = createGateway(MockBackend.withPostMessage(body))

        val result = gateway.reply(MessageId(ChannelId("C123"), Timestamp("1234567890.123")), "Thread reply").unsafeRunSync()

        result shouldBe MessageId(ChannelId("C123"), Timestamp("1234567891.456"))
      }

      "should reply in thread with buttons" in {
        val body = """{"ok":true,"channel":"C123","ts":"1234567891.456"}"""
        val gateway = createGateway(MockBackend.withPostMessage(body))
        val btn = gateway.registerButton[String](_ => IO.unit).unsafeRunSync()

        val result = gateway.reply(
          MessageId(ChannelId("C123"), Timestamp("1234567890.123")),
          "Confirm?",
          Seq(Button("OK", btn, btn.value)),
        ).unsafeRunSync()

        result shouldBe MessageId(ChannelId("C123"), Timestamp("1234567891.456"))
      }
    }

    "update" - {
      "should update a message" in {
        val gateway = createGateway(MockBackend.withUpdate(
          """{"ok":true,"channel":"C123","ts":"1234567890.123"}""",
        ))

        val msgId = MessageId(ChannelId("C123"), Timestamp("1234567890.123"))
        val result = gateway.update(msgId, "Updated text").unsafeRunSync()

        result shouldBe msgId
      }

      "should handle API errors on update" in {
        val errorBody = """{"ok":false,"error":"message_not_found"}"""
        val gateway = createGateway(MockBackend.withUpdate(errorBody))

        val msgId = MessageId(ChannelId("C123"), Timestamp("1234567890.123"))
        val ex = intercept[chatops4s.slack.api.SlackApiError] {
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
        val gateway = createGateway()
        var captured: Option[ButtonClick[String]] = None

        val btnId = gateway.registerButton[String] { click =>
          IO { captured = Some(click) }
        }.unsafeRunSync()

        val payload = interactionPayload(btnId.value, "my-value")
        gateway.handleInteractionPayload(payload).unsafeRunSync()

        captured shouldBe defined
        captured.get.userId shouldBe UserId("U123")
        captured.get.messageId shouldBe MessageId(ChannelId("C123"), Timestamp("1234567890.123"))
        captured.get.value shouldBe "my-value"
      }

      "should ignore unknown action IDs" in {
        val gateway = createGateway()
        var called = false

        gateway.registerButton[String] { _ => IO { called = true } }.unsafeRunSync()

        val payload = interactionPayload("unknown_action_id", "v")
        gateway.handleInteractionPayload(payload).unsafeRunSync()

        called shouldBe false
      }

      "should dispatch multiple actions in one payload" in {
        val gateway = createGateway()
        var count = 0

        val btn1 = gateway.registerButton[String] { _ => IO { count += 1 } }.unsafeRunSync()
        val btn2 = gateway.registerButton[String] { _ => IO { count += 10 } }.unsafeRunSync()

        val payload = InteractionPayload(
          `type` = "block_actions",
          user = User(UserId("U123")),
          channel = Some(Channel(ChannelId("C123"))),
          container = Container(message_ts = Some(Timestamp("1234567890.123"))),
          actions = List(
            Action(btn1.value, "blk-1", "button", Timestamp("1234567890.123"), value = Some("v1")),
            Action(btn2.value, "blk-2", "button", Timestamp("1234567890.123"), value = Some("v2")),
          ),
          trigger_id = "test-trigger-id",
          api_app_id = "A123",
          token = "tok",
        )
        gateway.handleInteractionPayload(payload).unsafeRunSync()

        count shouldBe 11
      }
    }

    "delete" - {
      "should delete a message" in {
        val body = """{"ok":true,"channel":"C123","ts":"1234567890.123"}"""
        val gateway = createGateway(MockBackend.create().whenAnyRequest.thenRespondAdjust(body))

        gateway.delete(MessageId(ChannelId("C123"), Timestamp("1234567890.123"))).unsafeRunSync()
      }

      "should handle API errors on delete" in {
        val errorBody = """{"ok":false,"error":"message_not_found"}"""
        val gateway = createGateway(MockBackend.create().whenAnyRequest.thenRespondAdjust(errorBody))

        val ex = intercept[chatops4s.slack.api.SlackApiError] {
          gateway.delete(MessageId(ChannelId("C123"), Timestamp("1234567890.123"))).unsafeRunSync()
        }
        ex.error shouldBe "message_not_found"
      }
    }

    "reactions" - {
      "should add a reaction" in {
        val gateway = createGateway(MockBackend.withOkApi())

        gateway.addReaction(MessageId(ChannelId("C123"), Timestamp("1234567890.123")), "rocket").unsafeRunSync()
      }

      "should remove a reaction" in {
        val gateway = createGateway(MockBackend.withOkApi())

        gateway.removeReaction(MessageId(ChannelId("C123"), Timestamp("1234567890.123")), "rocket").unsafeRunSync()
      }
    }

    "sendEphemeral" - {
      "should send an ephemeral message" in {
        val body = """{"ok":true,"message_ts":"1234567890.123"}"""
        val gateway = createGateway(MockBackend.create().whenAnyRequest.thenRespondAdjust(body))

        gateway.sendEphemeral("C123", UserId("U456"), "Only you can see this").unsafeRunSync()
      }
    }

    "onCommand" - {
      "should dispatch slash command to registered handler" in {
        val gateway = createGateway(MockBackend.withResponseUrl())
        var captured: Option[Command[String]] = None

        gateway.registerCommand[String]("/deploy") { cmd =>
          IO { captured = Some(cmd) }.as(CommandResponse.InChannel(s"Deploying ${cmd.args}"))
        }.unsafeRunSync()

        val payload = slashCommandPayload("/deploy", "v1.2.3")
        gateway.handleSlashCommandPayload(payload).unsafeRunSync()

        captured shouldBe defined
        captured.get.args shouldBe "v1.2.3"
        captured.get.userId shouldBe UserId("U123")
        captured.get.channelId shouldBe ChannelId("C123")
        captured.get.text shouldBe "v1.2.3"
      }

      "should normalize command names (strip / and lowercase)" in {
        val gateway = createGateway(MockBackend.withResponseUrl())
        var called = false

        gateway.registerCommand[String]("Deploy") { _ =>
          IO { called = true }.as(CommandResponse.Ephemeral("ok"))
        }.unsafeRunSync()

        val payload = slashCommandPayload("/deploy", "test")
        gateway.handleSlashCommandPayload(payload).unsafeRunSync()

        called shouldBe true
      }

      "should ignore unregistered commands" in {
        val gateway = createGateway()
        var called = false

        gateway.registerCommand[String]("/deploy") { _ =>
          IO { called = true }.as(CommandResponse.Ephemeral("ok"))
        }.unsafeRunSync()

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

        gateway.registerCommand[Int]("/count") { cmd =>
          IO.pure(CommandResponse.InChannel(s"Count: ${cmd.args}"))
        }.unsafeRunSync()

        // This should not throw - parse error is handled internally
        val payload = slashCommandPayload("/count", "not-a-number")
        gateway.handleSlashCommandPayload(payload).unsafeRunSync()
      }

      "should dispatch derived case class command" in {
        val gateway = createGateway(MockBackend.withResponseUrl())
        var captured: Option[Command[ScaleArgs]] = None

        gateway.registerCommand[ScaleArgs]("/scale") { cmd =>
          IO { captured = Some(cmd) }.as(CommandResponse.Ephemeral("ok"))
        }.unsafeRunSync()

        val payload = slashCommandPayload("/scale", "api 3")
        gateway.handleSlashCommandPayload(payload).unsafeRunSync()

        captured shouldBe defined
        captured.get.args.service shouldBe "api"
        captured.get.args.replicas shouldBe 3
      }

      "should return error for derived parser when args are missing" in {
        val gateway = createGateway(MockBackend.withResponseUrl())

        gateway.registerCommand[ScaleArgs]("/scale") { _ =>
          IO.pure(CommandResponse.Ephemeral("ok"))
        }.unsafeRunSync()

        val payload = slashCommandPayload("/scale", "api")
        gateway.handleSlashCommandPayload(payload).unsafeRunSync()
        // parse error is handled internally as ephemeral
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

        gateway.registerCommand[String]("deploy", "Deploy to prod") { _ =>
          IO.pure(CommandResponse.Silent)
        }.unsafeRunSync()

        val result = gateway.manifest("TestApp").unsafeRunSync()

        SnapshotTest.testSnapshot(result, "snapshots/manifest-with-commands.yaml")
      }

      "with commands with usage hint" in {
        val gateway = createGateway()

        gateway.registerCommand[ScaleArgs]("scale", "Scale a service") { _ =>
          IO.pure(CommandResponse.Silent)
        }.unsafeRunSync()

        val result = gateway.manifest("TestApp").unsafeRunSync()

        SnapshotTest.testSnapshot(result, "snapshots/manifest-with-commands-usage-hint.yaml")
      }

      "with explicit usage hint override" in {
        val gateway = createGateway()

        gateway.registerCommand[String]("search", "Search for something", usageHint = "[query]") { _ =>
          IO.pure(CommandResponse.Silent)
        }.unsafeRunSync()

        val result = gateway.manifest("TestApp").unsafeRunSync()

        SnapshotTest.testSnapshot(result, "snapshots/manifest-with-explicit-usage-hint.yaml")
      }

      "with buttons" in {
        val gateway = createGateway()

        gateway.registerButton[String](_ => IO.unit).unsafeRunSync()

        val result = gateway.manifest("TestApp").unsafeRunSync()

        SnapshotTest.testSnapshot(result, "snapshots/manifest-with-buttons.yaml")
      }

      "with forms" in {
        val gateway = createGateway()

        case class TestForm(name: String) derives FormDef

        gateway.registerForm[TestForm](_ => IO.unit).unsafeRunSync()

        val result = gateway.manifest("TestApp").unsafeRunSync()

        SnapshotTest.testSnapshot(result, "snapshots/manifest-with-forms.yaml")
      }
    }

    "forms" - {
      "should generate unique form IDs" in {
        val gateway = createGateway()

        case class TestForm(name: String) derives FormDef

        val ids = (1 to 10).toList.traverse(_ => gateway.registerForm[TestForm](_ => IO.unit)).unsafeRunSync()

        ids.map(_.value).toSet.size shouldBe 10
      }

      "should open form with correct view JSON" in {
        var capturedBody: Option[String] = None
        val backend = MockBackend.create()
          .whenRequestMatches { req =>
            val matches = req.uri.toString().contains("views.open")
            if (matches) {
              capturedBody = req.body match {
                case sttp.client4.StringBody(s, _, _) => Some(s)
                case _ => None
              }
            }
            matches
          }
          .thenRespondAdjust("""{"ok":true}""")

        val gateway = createGateway(backend)

        case class DeployForm(service: String, version: String, dryRun: Boolean) derives FormDef

        val formId = gateway.registerForm[DeployForm](_ => IO.unit).unsafeRunSync()
        gateway.openForm(TriggerId("trigger-123"), formId, "Deploy Service", "Deploy").unsafeRunSync()

        capturedBody shouldBe defined
        val json = parser.parse(capturedBody.get).toOption.get
        val view = json.hcursor.downField("view")
        view.downField("type").as[String] shouldBe Right("modal")
        view.downField("callback_id").as[String] shouldBe Right(formId.value)
        view.downField("title").downField("text").as[String] shouldBe Right("Deploy Service")
        view.downField("submit").downField("text").as[String] shouldBe Right("Deploy")

        val blocks = view.downField("blocks").as[List[Json]].toOption.get
        blocks.size shouldBe 3

        // service field
        blocks(0).hcursor.downField("block_id").as[String] shouldBe Right("service")
        blocks(0).hcursor.downField("element").downField("type").as[String] shouldBe Right("plain_text_input")

        // version field
        blocks(1).hcursor.downField("block_id").as[String] shouldBe Right("version")
        blocks(1).hcursor.downField("element").downField("type").as[String] shouldBe Right("plain_text_input")

        // dryRun field
        blocks(2).hcursor.downField("block_id").as[String] shouldBe Right("dryRun")
        blocks(2).hcursor.downField("element").downField("type").as[String] shouldBe Right("checkboxes")
      }

      "should dispatch view submission to registered handler" in {
        val gateway = createGateway()
        var captured: Option[FormSubmission[TestSubmitForm]] = None

        case class TestSubmitForm(name: String, count: Int) derives FormDef

        val formId = gateway.registerForm[TestSubmitForm] { submission =>
          IO { captured = Some(submission) }
        }.unsafeRunSync()

        val payload = viewSubmissionPayload(
          callbackId = formId.value,
          values = Map(
            "name" -> Map("name" -> ViewStateValue(value = Some("my-service"))),
            "count" -> Map("count" -> ViewStateValue(value = Some("42"))),
          ),
        )
        gateway.handleViewSubmissionPayload(payload).unsafeRunSync()

        captured shouldBe defined
        captured.get.userId shouldBe UserId("U123")
        captured.get.values.name shouldBe "my-service"
        captured.get.values.count shouldBe 42
      }

      "should ignore unknown callback IDs in view submission" in {
        val gateway = createGateway()
        var called = false

        case class TestForm(name: String) derives FormDef

        gateway.registerForm[TestForm] { _ =>
          IO { called = true }
        }.unsafeRunSync()

        val payload = viewSubmissionPayload(
          callbackId = "unknown-id",
          values = Map.empty,
        )
        gateway.handleViewSubmissionPayload(payload).unsafeRunSync()

        called shouldBe false
      }

      "CommandParser.derived should produce correct usage hint" in {
        val parser = summon[CommandParser[ScaleArgs]]
        parser.usageHint shouldBe "[service] [replicas]"
      }

      "CommandParser.derived should parse space-separated args" in {
        val parser = summon[CommandParser[ScaleArgs]]
        parser.parse("api 3") shouldBe Right(ScaleArgs("api", 3))
      }

      "CommandParser.derived should give last field the remainder" in {
        case class MsgArgs(target: String, message: String) derives CommandParser
        val parser = summon[CommandParser[MsgArgs]]
        parser.parse("user hello world") shouldBe Right(MsgArgs("user", "hello world"))
      }

      "CommandParser.derived should fail on too few arguments" in {
        val parser = summon[CommandParser[ScaleArgs]]
        parser.parse("api").isLeft shouldBe true
      }

      "CommandParser.derived should fail on invalid types" in {
        val parser = summon[CommandParser[ScaleArgs]]
        parser.parse("api abc").isLeft shouldBe true
      }

      "CommandParser.derived should convert camelCase to human-readable hint" in {
        case class MyArgs(serviceName: String, replicaCount: Int) derives CommandParser
        summon[CommandParser[MyArgs]].usageHint shouldBe "[service name] [replica count]"
      }

      "FormDef.derived should produce correct fields for a sample case class" in {
        case class SampleForm(
            name: String,
            age: Int,
            score: Double,
            active: Boolean,
            nickname: Option[String],
        ) derives FormDef

        val fd = summon[FormDef[SampleForm]]
        val fields = fd.fields

        fields.size shouldBe 5
        fields(0) shouldBe FormFieldDef("name", "Name", FormFieldType.PlainText, optional = false)
        fields(1) shouldBe FormFieldDef("age", "Age", FormFieldType.Integer, optional = false)
        fields(2) shouldBe FormFieldDef("score", "Score", FormFieldType.Decimal, optional = false)
        fields(3) shouldBe FormFieldDef("active", "Active", FormFieldType.Checkbox, optional = true)
        fields(4) shouldBe FormFieldDef("nickname", "Nickname", FormFieldType.PlainText, optional = true)
      }

      "FormDef.derived should parse values correctly" in {
        case class SampleForm(name: String, count: Int, active: Boolean) derives FormDef

        val fd = summon[FormDef[SampleForm]]
        val values = Map(
          "name" -> Map("name" -> ViewStateValue(value = Some("test"))),
          "count" -> Map("count" -> ViewStateValue(value = Some("5"))),
          "active" -> Map("active" -> ViewStateValue(selected_options = Some(List(api.socket.SelectedOption(value = "true"))))),
        )

        val result = fd.parse(values)
        result shouldBe Right(SampleForm("test", 5, true))
      }

      "FormDef.derived should parse boolean false (empty selected_options)" in {
        case class BoolForm(flag: Boolean) derives FormDef

        val fd = summon[FormDef[BoolForm]]
        val values = Map(
          "flag" -> Map("flag" -> ViewStateValue(selected_options = Some(List.empty))),
        )

        fd.parse(values) shouldBe Right(BoolForm(false))
      }

      "FormDef.derived should parse Option[String] as None when missing" in {
        case class OptForm(note: Option[String]) derives FormDef

        val fd = summon[FormDef[OptForm]]
        val values = Map(
          "note" -> Map("note" -> ViewStateValue()),
        )

        fd.parse(values) shouldBe Right(OptForm(None))
      }

      "FormDef.derived should parse Option[String] as Some when present" in {
        case class OptForm(note: Option[String]) derives FormDef

        val fd = summon[FormDef[OptForm]]
        val values = Map(
          "note" -> Map("note" -> ViewStateValue(value = Some("hello"))),
        )

        fd.parse(values) shouldBe Right(OptForm(Some("hello")))
      }
    }
  }

  private def slashCommandPayload(command: String, text: String): SlashCommandPayload =
    SlashCommandPayload(
      command = command,
      text = text,
      user_id = UserId("U123"),
      channel_id = ChannelId("C123"),
      response_url = "https://hooks.slack.com/commands/T123/456/789",
      trigger_id = "test-trigger-id",
      team_id = TeamId("T123"),
      team_domain = "test",
      channel_name = "general",
      api_app_id = "A123",
    )

  private def interactionPayload(actionId: String, value: String): InteractionPayload =
    InteractionPayload(
      `type` = "block_actions",
      user = User(UserId("U123")),
      channel = Some(Channel(ChannelId("C123"))),
      container = Container(message_ts = Some(Timestamp("1234567890.123"))),
      actions = List(
        Action(actionId, "blk-1", "button", Timestamp("1234567890.123"), value = Some(value)),
      ),
      trigger_id = "test-trigger-id",
      api_app_id = "A123",
      token = "tok",
    )

  private def viewSubmissionPayload(
      callbackId: String,
      values: Map[String, Map[String, ViewStateValue]],
  ): ViewSubmissionPayload =
    ViewSubmissionPayload(
      `type` = "view_submission",
      user = User(UserId("U123")),
      view = ViewPayload(
        id = "V123",
        callback_id = Some(callbackId),
        state = Some(ViewState(values)),
      ),
      api_app_id = "A123",
    )
}
