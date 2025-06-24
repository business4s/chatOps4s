package models

import cats.effect.IO

trait OutboundGateway {
  def sendToChannel(channelId: String, message: Message): IO[MessageResponse]
  def sendToThread(messageId: String, message: Message): IO[MessageResponse]
}
