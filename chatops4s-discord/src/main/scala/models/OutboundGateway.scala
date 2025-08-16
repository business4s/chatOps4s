package models

trait OutboundGateway[F[_]] {
  def sendToChannel(channelId: String, message: Message): F[MessageResponse]
  def sendToThread(channelId: String, threadName: String, message: Message): F[MessageResponse]
  def replyToMessage(channelId: String, messageId: String, message: Message): F[MessageResponse]
}
