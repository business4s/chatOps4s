package models

import cats.effect.IO

trait OutboundGateway {
  def sendToChannel(channelId: String, message: Message): IO[MessageResponse]
  def sendToThread(channelId: String, threadName: String, message: Message): IO[MessageResponse]
  def replyToMessage(channelId: String, messageId: String, message: Message): IO[MessageResponse]
}
