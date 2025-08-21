package discord.docs

import api.{DiscordInbound, Server}
import cats.effect.{ExitCode, IO, IOApp}
import discord.utilities.EnvLoader
import org.http4s.HttpRoutes
import org.http4s.ember.server.EmberServerBuilder
import sttp.tapir.server.http4s.Http4sServerInterpreter
import com.comcast.ip4s.{Host, Port}

object GettingStarted extends IOApp {
  private val discordInbound = new DiscordInbound()
  final private val port = 8080;
  private val server         =
    new Server(discordPublicKey = "", discordInbound = discordInbound)

  override def run(args: List[String]): IO[ExitCode] = {
    EnvLoader.loadEnv()
    val routes: HttpRoutes[IO] =
      Http4sServerInterpreter[IO]()
        .toRoutes(List(server.interactionRoute))

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
