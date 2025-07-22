import api.{DiscordInbound, DiscordOutbound, Server}
import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter
import utilities.EnvLoader
import org.http4s.server.Router
import models.*
import services.*
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import io.circe.generic.auto.*
import sttp.client4.httpclient.cats.HttpClientCatsBackend

object Main extends IOApp {
  private val discordInbound = new DiscordInbound()
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

    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(
        Router(
          "/" -> routes,
        ).orNotFound,
      )
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }
}
