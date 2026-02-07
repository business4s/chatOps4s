package chatops4s.slack

import cats.effect.kernel.{Async, Ref}
import cats.syntax.all.*

import java.util.UUID

import SlackModels.*

private[slack] type ErasedHandler[F[_]] = (ButtonClick[String], SlackGateway[F]) => F[Unit]

private[slack] class SlackGatewayImpl[F[_]: Async](
    client: SlackClient[F],
    handlersRef: Ref[F, Map[String, ErasedHandler[F]]],
    val listen: F[Unit],
) extends SlackGateway[F] with SlackSetup[F] {

  override def onButton[T <: String](handler: (ButtonClick[T], SlackGateway[F]) => F[Unit]): F[ButtonId[T]] = {
    val id = ButtonId[T](UUID.randomUUID().toString)
    val erased = handler.asInstanceOf[ErasedHandler[F]]
    handlersRef.update(_ + (id.value -> erased)).as(id)
  }

  override def send(channel: String, text: String, buttons: Seq[Button]): F[MessageId] =
    client.postMessage(channel, text, buildBlocks(text, buttons), threadTs = None)

  override def reply(to: MessageId, text: String, buttons: Seq[Button]): F[MessageId] =
    client.postMessage(to.channel, text, buildBlocks(text, buttons), threadTs = Some(to.ts))

  override def update(messageId: MessageId, text: String, buttons: Seq[Button]): F[MessageId] =
    client.updateMessage(messageId, text, buildBlocks(text, buttons))

  private[slack] def handleInteractionPayload(payload: InteractionPayload): F[Unit] = {
    handlersRef.get.flatMap { handlers =>
      val messageId = MessageId(
        channel = payload.channel.id,
        ts = payload.container.message_ts.getOrElse(""),
      )

      payload.actions.getOrElse(Nil).traverse_ { action =>
        val click = ButtonClick[String](
          userId = payload.user.id,
          messageId = messageId,
          value = action.value.getOrElse(""),
        )

        handlers.get(action.action_id).traverse_(handler => handler(click, this))
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
}
