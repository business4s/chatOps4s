package discord.docs

import api.{DiscordInbound, DiscordOutbound}
import cats.effect.{ExitCode, IO, IOApp}
import models.Message
import sttp.client4.httpclient.cats.HttpClientCatsBackend

object SendToThread extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    HttpClientCatsBackend.resource[IO]().use { backend =>
      val discordInbound  = new DiscordInbound()
      val discordOutbound = new DiscordOutbound(
        token = "",
        url = "",
        backend = backend,
      )
      val acceptButton    = discordInbound.registerAction(ctx =>
        IO.pure(
          discordOutbound.sendToThread(
            channelId = ctx.channelId,
            threadName = "Accepts Thread",
            message = Message(
              text = "Someone Accepted!",
            ),
          ),
        ),
      )
      val declineButton   = discordInbound.registerAction(ctx =>
        IO.pure(
          discordOutbound.sendToThread(
            channelId = ctx.channelId,
            threadName = "Declines Thread",
            message = Message(
              text = "Someone Declined!",
            ),
          ),
        ),
      )
      val message         = Message(
        text = "Deploy to production?",
        interactions = Seq(
          acceptButton.render("Accept"),
          declineButton.render("Decline"),
        ),
      )
      discordOutbound
        .sendToChannel("", message)
        .flatMap(_ => {
          IO.pure(ExitCode.Success)
        })
    }
  }
}
