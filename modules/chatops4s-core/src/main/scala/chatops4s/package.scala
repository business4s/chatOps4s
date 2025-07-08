package chatops4s

import cats.effect.IO

// Core domain models
case class Message(
                    text: String,
                    interactions: Seq[Button] = Seq.empty,
                  )

case class MessageResponse(
                            messageId: String,
                          )

case class Button(label: String, value: String)

case class InteractionContext(
                               userId: String,
                               channelId: String,
                               messageId: String,
                             )

// Core gateways
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