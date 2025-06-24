package api

import cats.effect.unsafe.IORuntime
import cats.effect.{ExitCode, IO, IOApp}
import io.circe.Json
import models.{DiscordResponse, InteractionContext}
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.tapir.*
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.json.circe.schemaForCirceJson
import sttp.tapir.generic.auto.*
import io.circe.generic.auto.*

object Server extends IOApp:
  private val discordInbound = new DiscordInbound()

  private val logic: Json => IO[Either[DiscordResponse, DiscordResponse]] = (json: Json) => {
    val maybeContext = for {
      customId <- json.hcursor.downField("data").get[String]("custom_id").toOption
      userId <- json.hcursor.downField("member").downField("user").get[String]("id").toOption
      channelId <- json.hcursor.get[String]("channel_id").toOption
      messageId <- json.hcursor.downField("message").get[String]("id").toOption
      _type     <- json.hcursor.get[Int]("type").toOption

    } yield (customId, _type, InteractionContext(userId, channelId, messageId))
    maybeContext match {
      case Some((id, _type, ctx)) =>
        if (_type == 1) {
          println("ping")
          IO.pure(Right(DiscordResponse(`type` = 1)))
        } else {
          discordInbound.handlers.get(id) match {
            case Some(f) => f(ctx).map(Right(_)).as(Right(DiscordResponse(`type` = 1)))
            case None => IO.pure(Right(DiscordResponse(`type` = 1)))
          }
        }
      case None => IO.pure(Left(DiscordResponse(`type` = 1)))
    }
  }

  private val interactionEndpoint = endpoint.post
    .in("interactions")
    .in(jsonBody[Json])
    .errorOut(jsonBody[DiscordResponse])
    .out(jsonBody[DiscordResponse])

  override def run(args: List[String]): IO[ExitCode] = {
    val routes: HttpRoutes[IO] =
      Http4sServerInterpreter[IO]()
        .toRoutes(interactionEndpoint.serverLogic(logic))

    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(Router("/" -> routes).orNotFound)
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }
