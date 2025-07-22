package services

import api.DiscordOutbound
import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import models.Message

class SendToProductionService(discordOutbound: DiscordOutbound) extends StrictLogging {
  def onAccept(channelId: String): IO[Unit] = {
    discordOutbound
      .sendToThread(
        channelId = channelId,
        threadName = "Test Thread!",
        message = Message(
          text = "Sending to production!",
        ),
      )
      .flatMap { response =>
        IO.pure(logger.info(s"Accepted. Sent message ${response.messageId}"))
      }
  }

  def onDecline(channelId: String): IO[Unit] = {
    discordOutbound
      .sendToChannel(
        channelId,
        Message(
          text = "Not sending to production!",
        ),
      )
      .flatMap { response =>
        IO.pure(logger.info(s"Declined. Sent message ${response.messageId}"))
      }
  }
}
