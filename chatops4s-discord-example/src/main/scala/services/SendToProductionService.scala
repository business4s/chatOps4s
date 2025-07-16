package services

import api.DiscordOutbound
import cats.effect.IO
import com.typesafe.scalalogging.{Logger, StrictLogging}
import models.Message

class SendToProductionService(discordOutbound: DiscordOutbound) extends StrictLogging {
  def onAccept(channelId: String): IO[Unit] = {
    discordOutbound.sendToChannel(
      channelId,
      Message(
        text = "Sending to production!"
      )
    ).flatMap { response =>
      IO.println(s"Accepted. Sent message ${response.messageId}")
    }
  }

  def onDecline(channelId: String): IO[Unit] = {
    discordOutbound.sendToChannel(
      channelId,
      Message(
        text = "Not sending to production!"
      )
    ).flatMap { response =>
      IO.println(s"Declined. Sent message ${response.messageId}")
    }
  }
}
