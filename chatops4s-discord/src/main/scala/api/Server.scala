package api

import cats.effect.unsafe.IORuntime
import cats.effect.{ExitCode, IO, IOApp}
import io.circe.Json
import models.InteractionContext
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.tapir.*
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.json.circe.schemaForCirceJson

object Server extends IOApp:
  private val discordInbound = new DiscordInbound()

  private val logic: Json => IO[Either[String, String]] = (json: Json) => {
    println(s"Incoming JSON: $json")
    val maybeContext = for {
      customId <- json.hcursor.downField("data").get[String]("custom_id").toOption
      userId <- json.hcursor.downField("member").downField("user").get[String]("id").toOption
      channelId <- json.hcursor.get[String]("channel_id").toOption
      messageId <- json.hcursor.downField("message").get[String]("id").toOption
    } yield (customId, InteractionContext(userId, channelId, messageId))
    println(s"context $maybeContext")

    maybeContext match {
      case Some((id, ctx)) =>
        discordInbound.handlers.get(id) match {
          case Some(f) => f(ctx).map(Right(_)).as(Right("Success"))
          case None => IO.pure(Right("Success"))
        }
      case None => IO.pure(Left("Failure"))
    }
  }

  private val interactionEndpoint = endpoint.post
    .in("interactions")
    .in(jsonBody[Json])
    .errorOut(stringBody)
    .out(stringBody)

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
