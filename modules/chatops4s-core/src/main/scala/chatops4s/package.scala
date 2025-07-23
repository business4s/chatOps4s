package chatops4s

import cats.effect.IO

case class Message(
    text: String,
    interactions: Seq[Button] = Seq.empty,
) derives io.circe.Codec.AsObject

case class MessageResponse(
    messageId: String,
) derives io.circe.Codec.AsObject

case class Button(
    label: String,
    value: String,
) derives io.circe.Codec.AsObject

case class InteractionContext(
    userId: String,
    channelId: String,
    messageId: String,
) derives io.circe.Codec.AsObject

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
