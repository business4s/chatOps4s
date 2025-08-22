package discord.docs

import api.{DiscordInbound, DiscordOutbound}
import cats.effect.{ExitCode, IO, IOApp}
import models.Message
import sttp.client4.httpclient.cats.HttpClientCatsBackend

object SendingAReply extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    // start_doc
    HttpClientCatsBackend.resource[IO]().use { backend =>
      val discordInbound  = new DiscordInbound()
      val discordOutbound = new DiscordOutbound(
        token = "",
        url = "",
        backend = backend,
      )
      val acceptButton    = discordInbound.registerAction(ctx =>
        IO.pure(
          discordOutbound.replyToMessage(
            channelId = ctx.channelId,
            messageId = ctx.messageId,
            message = Message(
              text = "You Accepted!",
            ),
          ),
        ),
      )
      val declineButton   = discordInbound.registerAction(ctx =>
        IO.pure(
          discordOutbound.replyToMessage(
            channelId = ctx.channelId,
            messageId = ctx.messageId,
            message = Message(
              text = "You Declined!",
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
      // end_doc
    }
  }
}
