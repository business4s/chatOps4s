package chatops4s.examples.discord

import api.DiscordInbound
import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.{Host, Port}
import discord.services.*
import discord.utilities.EnvLoader
import io.circe.generic.auto.*
import models.MessageResponse
import org.http4s.HttpRoutes
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import api.Server
import api.DiscordOutbound
import com.typesafe.scalalogging.StrictLogging
import models.Message
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

object Main extends IOApp with StrictLogging {
  private val discordInbound = new DiscordInbound()
  final private val port     = 8080;
  private val server         =
    new Server(discordPublicKey = "cec2f053ddcba6bb67570ac176afc730df3325a729ccb32edbed9dbe4d1741ca", discordInbound = discordInbound)

  private val sendEndpoint = endpoint.post
    .in("send")
    .out(jsonBody[MessageResponse])
    .serverLogicSuccess[IO](_ => {
      HttpClientCatsBackend.resource[IO]().use { backend =>
        val discordOutbound = new DiscordOutbound(
          token = EnvLoader.get("DISCORD_BOT_TOKEN"),
          url = EnvLoader.get("DISCORD_BOT_URL"),
          backend = backend,
        )
        val acceptDecline   = SendToProductionService(discordOutbound = discordOutbound)
        val acceptButton    = discordInbound.registerAction(ctx => acceptDecline.onAccept(ctx.channelId))
        val declineButton   = discordInbound.registerAction(ctx => acceptDecline.onDecline(ctx.channelId))
        val message         = Message(
          text = "Deploy to production?",
          interactions = Seq(
            acceptButton.render("Accept"),
            declineButton.render("Decline"),
          ),
        )
        discordOutbound.sendToChannel("1381992880834351184", message)
      }
    })

  override def run(args: List[String]): IO[ExitCode] = {
    EnvLoader.loadEnv()
    val routes: HttpRoutes[IO] =
      Http4sServerInterpreter[IO]()
        .toRoutes(List(server.interactionRoute, sendEndpoint))

    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString("0.0.0.0").get)
      .withPort(Port.fromInt(this.port).get)
      .withHttpApp(routes.orNotFound)
      .build
      .useForever
      .as(ExitCode.Success)
  }
}
