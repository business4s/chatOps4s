package chatops4s.slack

import chatops4s.slack.api.ChannelId
import io.circe.syntax.*
import sttp.client4.WebSocketBackend
import sttp.monad.MonadError
import sttp.monad.syntax.*
import chatops4s.slack.monadSyntax.*

import java.util.UUID

import SlackModels.*

private[slack] type ErasedHandler[F[_]] = ButtonClick[String] => F[Unit]
private[slack] type ErasedCommandHandler[F[_]] = SlackModels.SlashCommandPayload => F[CommandResponse]

private[slack] opaque type CommandName = String
private[slack] object CommandName {
  def apply(raw: String): CommandName = raw.stripPrefix("/").toLowerCase
  extension (cn: CommandName) def value: String = cn
}

private[slack] case class CommandEntry[F[_]](
    handler: ErasedCommandHandler[F],
    description: String,
)

private[slack] case class FormEntry[F[_]](
    formDef: FormDef[Any],
    handler: FormSubmission[Any] => F[Unit],
)

private[slack] class SlackGatewayImpl[F[_]](
    client: SlackClient[F],
    handlersRef: Ref[F, Map[ButtonId[?], ErasedHandler[F]]],
    commandHandlersRef: Ref[F, Map[CommandName, CommandEntry[F]]],
    formHandlersRef: Ref[F, Map[FormId[?], FormEntry[F]]],
    backend: WebSocketBackend[F],
) extends SlackGateway[F] with SlackSetup[F] {

  private given monad: MonadError[F] = backend.monad

  override def registerButton[T <: String](handler: ButtonClick[T] => F[Unit]): F[ButtonId[T]] = {
    val id = ButtonId[T](UUID.randomUUID().toString)
    val erased = handler.asInstanceOf[ErasedHandler[F]]
    handlersRef.update(_ + (id -> erased)).as(id)
  }

  override def registerCommand[T: {CommandParser as parser}](name: String, description: String = "")(handler: Command[T] => F[CommandResponse]): F[Unit] = {
    val normalized = CommandName(name)
    val erased: ErasedCommandHandler[F] = { payload =>
      parser.parse(payload.text) match {
        case Left(error) =>
          monad.unit(CommandResponse.Ephemeral(s"Invalid command arguments: $error"))
        case Right(args) =>
          val cmd = Command(
            args = args,
            userId = payload.user_id,
            channelId = ChannelId(payload.channel_id),
            text = payload.text,
            triggerId = TriggerId(payload.trigger_id),
          )
          handler(cmd)
      }
    }
    commandHandlersRef.update(_ + (normalized -> CommandEntry(erased, description)))
  }

  override def registerForm[T: {FormDef as fd}](handler: FormSubmission[T] => F[Unit]): F[FormId[T]] = {
    val id = FormId[T](UUID.randomUUID().toString)
    val entry = FormEntry[F](
      formDef = fd.asInstanceOf[FormDef[Any]],
      handler = handler.asInstanceOf[FormSubmission[Any] => F[Unit]],
    )
    formHandlersRef.update(_ + (id -> entry)).as(id)
  }

  override def manifest(appName: String): F[String] = {
    for {
      handlers <- handlersRef.get
      commands <- commandHandlersRef.get
      forms    <- formHandlersRef.get
    } yield {
      val commandDefs = commands.map((name, entry) => name.value -> entry.description)
      SlackManifest.generate(appName, commandDefs, hasInteractivity = handlers.nonEmpty || forms.nonEmpty)
    }
  }

  override def listen(appToken: String): F[Unit] =
    SocketMode.runLoop(appToken, backend, handleInteractionPayload, handleSlashCommandPayload, handleViewSubmissionPayload)

  override def send(channel: String, text: String, buttons: Seq[Button]): F[MessageId] =
    client.postMessage(channel, text, buildBlocks(text, buttons), threadTs = None)

  override def reply(to: MessageId, text: String, buttons: Seq[Button]): F[MessageId] =
    client.postMessage(to.channel.value, text, buildBlocks(text, buttons), threadTs = Some(to.ts))

  override def update(messageId: MessageId, text: String, buttons: Seq[Button]): F[MessageId] =
    client.updateMessage(messageId, text, buildBlocks(text, buttons))

  override def delete(messageId: MessageId): F[Unit] =
    client.deleteMessage(messageId)

  override def addReaction(messageId: MessageId, emoji: String): F[Unit] =
    client.addReaction(messageId, emoji)

  override def removeReaction(messageId: MessageId, emoji: String): F[Unit] =
    client.removeReaction(messageId, emoji)

  override def sendEphemeral(channel: String, userId: String, text: String): F[Unit] =
    client.postEphemeral(channel, userId, text)

  override def openForm[T](triggerId: TriggerId, formId: FormId[T], title: String, submitLabel: String = "Submit", initialValues: InitialValues[T] = InitialValues.of[T]): F[Unit] = {
    formHandlersRef.get.flatMap { forms =>
      forms.get(formId) match {
        case None => monad.error(new RuntimeException(s"Form not found: ${formId.value}"))
        case Some(entry) =>
          val viewBlocks = buildViewBlocks(entry.formDef, initialValues.toMap)
          val view = View(
            `type` = "modal",
            callback_id = formId.value,
            title = TextObject(`type` = "plain_text", text = title),
            submit = Some(TextObject(`type` = "plain_text", text = submitLabel)),
            blocks = viewBlocks,
          )
          client.openView(triggerId.value, view.asJson.deepDropNullValues)
      }
    }
  }

  private[slack] def handleInteractionPayload(payload: InteractionPayload): F[Unit] = {
    handlersRef.get.flatMap { handlers =>
      val channelId = ChannelId(payload.channel.id)
      val messageId = MessageId(
        channel = channelId,
        ts = payload.container.message_ts.getOrElse(""),
      )
      val threadId = payload.message.flatMap(_.thread_ts).map(ts => MessageId(channelId, ts))

      payload.actions.getOrElse(Nil).traverse_ { action =>
        val click = ButtonClick[String](
          userId = payload.user.id,
          messageId = messageId,
          value = action.value.getOrElse(""),
          triggerId = TriggerId(payload.trigger_id),
          threadId = threadId,
        )

        handlers.get(ButtonId[String](action.action_id)).traverse_(handler => handler(click))
      }
    }
  }

  private[slack] def handleSlashCommandPayload(payload: SlackModels.SlashCommandPayload): F[Unit] = {
    val normalized = CommandName(payload.command)
    commandHandlersRef.get.flatMap { commands =>
      commands.get(normalized).traverse_ { entry =>
        entry.handler(payload).flatMap {
          case CommandResponse.Silent      => monad.unit(())
          case CommandResponse.Ephemeral(t) =>
            client.respondToCommand(payload.response_url, t, "ephemeral")
          case CommandResponse.InChannel(t) =>
            client.respondToCommand(payload.response_url, t, "in_channel")
        }
      }
    }
  }

  private[slack] def handleViewSubmissionPayload(payload: ViewSubmissionPayload): F[Unit] = {
    val callbackId = FormId[Any](payload.view.callback_id.getOrElse(""))
    formHandlersRef.get.flatMap { forms =>
      forms.get(callbackId).traverse_ { entry =>
        val values = payload.view.state.map(_.values).getOrElse(Map.empty)
        entry.formDef.parse(values) match {
          case Left(error) =>
            monad.error(new RuntimeException(s"Form parse error: $error"))
          case Right(parsed) =>
            val submission = FormSubmission(userId = payload.user.id, values = parsed)
            entry.handler(submission)
        }
      }
    }
  }

  private def buildBlocks(text: String, buttons: Seq[Button]): Option[List[Block]] =
    if (buttons.nonEmpty) {
      Some(List(
        Block(
          `type` = "section",
          text = Some(TextObject(`type` = "mrkdwn", text = text)),
        ),
        Block(
          `type` = "actions",
          elements = Some(buttons.map(buttonToElement).toList),
        ),
      ))
    } else None

  private def buttonToElement(button: Button): BlockElement =
    BlockElement(
      `type` = "button",
      text = Some(TextObject(`type` = "plain_text", text = button.label)),
      action_id = Some(button.actionId),
      value = Some(button.value),
    )

  private def buildViewBlocks(formDef: FormDef[?], initialValues: Map[String, String]): List[Block] =
    formDef.fields.map { field =>
      val initial = initialValues.get(field.id)
      val element = field.fieldType match {
        case FormFieldType.PlainText =>
          BlockElement(
            `type` = "plain_text_input",
            action_id = Some(field.id),
            initial_value = initial,
          )
        case FormFieldType.Integer =>
          BlockElement(
            `type` = "number_input",
            action_id = Some(field.id),
            is_decimal_allowed = Some(false),
            initial_value = initial,
          )
        case FormFieldType.Decimal =>
          BlockElement(
            `type` = "number_input",
            action_id = Some(field.id),
            is_decimal_allowed = Some(true),
            initial_value = initial,
          )
        case FormFieldType.Checkbox =>
          BlockElement(
            `type` = "checkboxes",
            action_id = Some(field.id),
            options = Some(List(
              BlockOption(
                text = TextObject(`type` = "plain_text", text = "Yes"),
                value = "true",
              ),
            )),
          )
      }
      Block(
        `type` = "input",
        block_id = Some(field.id),
        label = Some(TextObject(`type` = "plain_text", text = field.label)),
        element = Some(element),
        optional = if (field.optional) Some(true) else None,
      )
    }
}
