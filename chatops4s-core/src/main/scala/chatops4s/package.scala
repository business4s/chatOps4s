package chatops4s

import io.circe.{Codec, Decoder, Encoder}

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

trait OutboundGateway[F[_]] {
  def sendToChannel(channelId: String, message: Message): F[MessageResponse]
  def sendToThread(messageId: String, message: Message): F[MessageResponse]
}

trait InboundGateway[F[_]] {
  def registerAction(handler: InteractionContext => F[Unit]): F[ButtonInteraction]
}

trait ButtonInteraction {
  def render(label: String): Button
}
