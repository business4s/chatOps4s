package api

import cats.effect.IO
import models.{InteractionRequest, Message, SlashInteraction}
import utilities.DiscordBot

case class MessageResponse(
  messageId: String
)

class OutboundGateway {
  def sendToChannel(interactionRequest: InteractionRequest, message: Message): IO[MessageResponse] = {
    DiscordBot.sendInteraction(incoming = interactionRequest, interaction = SlashInteraction(
      message = message.text,
      ephemeral = true
    ))
    IO(MessageResponse(
      messageId = "messageId"
    ))
  }
  def sendToThread(interactionRequest: InteractionRequest, message: Message): IO[MessageResponse] = {
    DiscordBot.sendInteraction(incoming = interactionRequest, interaction = SlashInteraction(
      message = message.text,
      ephemeral = true
    ))
    IO(MessageResponse(
      messageId = "messageId"
    ))
  }
}