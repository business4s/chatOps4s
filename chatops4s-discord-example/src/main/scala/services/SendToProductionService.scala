package services

import cats.effect.IO
import com.typesafe.scalalogging.{Logger, StrictLogging}

class SendToProductionService extends StrictLogging {
  private val discordOutbound = new DiscordOutbound()

  def onAccept(channelId: String): IO[Unit] = {
    discordOutbound.sendToChannel(
      ctx.channelId,
      Message(
        text = "Sending to production!"
      )
    ).flatMap { response =>
      IO.println(s"Accepted. Sent message ${response.messageId}")
    }
  }

  def onDecline(channelId: String): IO[Unit] = {
    discordOutbound.sendToChannel(
      ctx.channelId,
      Message(
        text = "Not sending to production!"
      )
    ).flatMap { response =>
      IO.println(s"Declined. Sent message ${response.messageId}")
    }
  }
}
