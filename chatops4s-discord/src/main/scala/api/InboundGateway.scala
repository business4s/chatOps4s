package api

import cats.effect.IO
import enums.{InputType, InteractionType}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import models.*
import interactions.*
import sttp.shared.Identity
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import utilities.{DiscordBot, EnvLoader}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.model.StatusCode
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.netty.NettyConfig
import io.circe.parser.*

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.*

case class InteractionContext(
   userId: String,
   channelId: String,
   messageId: String,
 )

class InboundGateway(interactionContext: InteractionContext) {
  private sealed trait ErrorInfo
  private case class NotFound() extends ErrorInfo
  private object NotFound {
    implicit val decoder: Decoder[NotFound] = deriveDecoder
    implicit val encoder: Encoder[NotFound] = deriveEncoder
  }
  private case class Unauthorized() extends ErrorInfo
  private object Unauthorized {
    implicit val decoder: Decoder[Unauthorized] = deriveDecoder
    implicit val encoder: Encoder[Unauthorized] = deriveEncoder
  }
  private case class BadRequest() extends ErrorInfo
  private object BadRequest {
    implicit val decoder: Decoder[BadRequest] = deriveDecoder
    implicit val encoder: Encoder[BadRequest] = deriveEncoder
  }
  private val interactions: collection.mutable.ArrayBuffer[InteractionContext => IO[Unit]] = ArrayBuffer()

  private val baseEndpoint = endpoint.errorOut(
    oneOf[ErrorInfo](
      oneOfVariant(StatusCode.NotFound, jsonBody[NotFound].description("not found")),
      oneOfVariant(StatusCode.Unauthorized, jsonBody[Unauthorized].description("unauthorized")),
      oneOfVariant(StatusCode.BadRequest, jsonBody[BadRequest].description("bad request")),
    )
  )
  private val interactionsEndpoint = baseEndpoint
    .post
    .in("api" / "interactions")
    .in(header[String]("X-Signature-Ed25519"))
    .in(header[String]("X-Signature-Timestamp"))
    .in(stringBody)
    .out(jsonBody[MessageResponse])
    .handle { case (signature, timestamp, body) =>
      val isValid = DiscordBot.verifySignature(
        signature,
        timestamp,
        body
      )
      if (!isValid) {
        Left(Unauthorized())
      } else {
        val decoded = decode[InteractionRequest](body)
        decoded match {
          case Right(interactionRequest) =>
            Right(MessageResponse(
              messageId = "test"
            ))
          case Left(error) =>
            Left(BadRequest())
        }
      }
    }

  def registerAction(handler: (ctx: InteractionContext) => IO[Unit]): IO[ButtonInteraction] = {
    class ButtonInteractionTest extends ButtonInteraction {
      override def render(label: String): IO[Button] = {
        handler(interactionContext)
        for {
          _ <- handler(interactionContext)
          button = Button(
            label = label,
            value = label.toUpperCase().replace(' ', '_')
          )
        } yield button
      }
    }
    IO(ButtonInteractionTest())
  }
  // Add shutdown hook to clean up server
  def start(): Unit = {
    val config = NettyConfig.default.withGracefulShutdownTimeout(2.seconds)
    val endpoints = List(this.interactionsEndpoint)
    val swaggerEndpoints = SwaggerInterpreter()
      .fromServerEndpoints[Identity](endpoints, "ChatOps4s", "1.0")
    NettySyncServer(config)
      .port(8080)
      .addEndpoints(endpoints)
      .addEndpoints(swaggerEndpoints)
      .startAndWait()
  }
}