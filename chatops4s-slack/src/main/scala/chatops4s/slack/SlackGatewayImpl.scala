package chatops4s.slack

import cats.effect.kernel.{Async, Ref}
import cats.syntax.all.*

import java.util.UUID

import SlackModels.*

private[slack] type ErasedHandler[F[_]] = ButtonClick[String] => F[Unit]
private[slack] type ErasedCommandHandler[F[_]] = SlackModels.SlashCommandPayload => F[CommandResponse]

private[slack] class SlackGatewayImpl[F[_]: Async](
    client: SlackClient[F],
    handlersRef: Ref[F, Map[String, ErasedHandler[F]]],
    commandHandlersRef: Ref[F, Map[String, ErasedCommandHandler[F]]],
    val listen: F[Unit],
) extends SlackGateway[F] with SlackSetup[F] {

  override def onButton[T <: String](handler: ButtonClick[T] => F[Unit]): F[ButtonId[T]] = {
    val id = ButtonId[T](UUID.randomUUID().toString)
    val erased = handler.asInstanceOf[ErasedHandler[F]]
    handlersRef.update(_ + (id.value -> erased)).as(id)
  }

  override def onCommand[T: {CommandParser as parser}](name: String)(handler: Command[T] => F[CommandResponse]): F[Unit] = {
    val normalized = normalizeCommandName(name)
    val erased: ErasedCommandHandler[F] = { payload =>
      parser.parse(payload.text) match {
        case Left(error) =>
          Async[F].pure(CommandResponse.Ephemeral(s"Invalid command arguments: $error"))
        case Right(args) =>
          val cmd = Command(
            args = args,
            userId = payload.user_id,
            channelId = payload.channel_id,
            text = payload.text,
          )
          handler(cmd)
      }
    }
    commandHandlersRef.update(_ + (normalized -> erased))
  }

  override def send(channel: String, text: String, buttons: Seq[Button]): F[MessageId] =
    client.postMessage(channel, text, buildBlocks(text, buttons), threadTs = None)

  override def reply(to: MessageId, text: String, buttons: Seq[Button]): F[MessageId] =
    client.postMessage(to.channel, text, buildBlocks(text, buttons), threadTs = Some(to.ts))

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

  private[slack] def handleInteractionPayload(payload: InteractionPayload): F[Unit] = {
    handlersRef.get.flatMap { handlers =>
      val messageId = MessageId(
        channel = payload.channel.id,
        ts = payload.container.message_ts.getOrElse(""),
      )
      val threadId = payload.message.flatMap(_.thread_ts).map(ts => MessageId(payload.channel.id, ts))

      payload.actions.getOrElse(Nil).traverse_ { action =>
        val click = ButtonClick[String](
          userId = payload.user.id,
          messageId = messageId,
          value = action.value.getOrElse(""),
          threadId = threadId,
        )

        handlers.get(action.action_id).traverse_(handler => handler(click))
      }
    }
  }

  private[slack] def handleSlashCommandPayload(payload: SlackModels.SlashCommandPayload): F[Unit] = {
    val normalized = normalizeCommandName(payload.command)
    // fox-comp would be cleaner
    commandHandlersRef.get.flatMap { handlers =>
      handlers.get(normalized).traverse_ { handler =>
        handler(payload).flatMap {
          case CommandResponse.Silent => Async[F].unit
          case CommandResponse.Ephemeral(t) =>
            client.respondToCommand(payload.response_url, t, "ephemeral")
          case CommandResponse.InChannel(t) =>
            client.respondToCommand(payload.response_url, t, "in_channel")
        }
      }
    }
  }

  private def normalizeCommandName(name: String): String =
    name.stripPrefix("/").toLowerCase

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
}
