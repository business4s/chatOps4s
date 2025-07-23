package chatops4s

import cats.effect.IO
import io.circe.Codec.AsObject

case class Message(
  text: String,
  interactions: Seq[Button] = Seq.empty
) derives AsObject

case class MessageResponse(
  messageId: String
) derives AsObject

case class Button(
  label: String,
  value: String
) derives AsObject

case class InteractionContext(
  userId: String,
  channelId: String,
  messageId: String
) derives AsObject

trait OutboundGateway {
  def sendToChannel(channelId: String, message: Message): IO[MessageResponse]
  def sendToThread(messageId: String, message: Message): IO[MessageResponse]
}

trait InboundGateway {
  def registerAction(handler: InteractionContext => IO[Unit]): IO[ButtonInteraction]
}

trait ButtonInteraction {
  def render(label: String): Button
}
