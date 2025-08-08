// TODO models is not a good package for this. It could be moved to the top level (chatops4s.discord)

package models

import cats.effect.IO

// TODO parametrize with F[_]
trait OutboundGateway {
  def sendToChannel(channelId: String, message: Message): IO[MessageResponse]
  def sendToThread(channelId: String, threadName: String, message: Message): IO[MessageResponse]
  def replyToMessage(channelId: String, messageId: String, message: Message): IO[MessageResponse]
}
