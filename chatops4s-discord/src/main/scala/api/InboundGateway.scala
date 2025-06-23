package api

import enums.{InputType, InteractionType}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import models._
import sttp.shared.Identity
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import utilities.{Chat4Ops, DiscordBot, EnvLoader}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.model.StatusCode
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.netty.NettyConfig
import io.circe.parser.*
import scala.concurrent.duration.*

class InboundGateway(val interactions: Interactions) {
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
    .out(jsonBody[InteractionResponse])
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
            val interactionResponse = Chat4Ops.executeInteraction(
              interactionRequest = interactionRequest,
              interactions = interactions
            )
            if interactionResponse.isDefined then Right(interactionResponse.get) else Left(BadRequest())
          case Left(error) =>
            Left(BadRequest())
        }
      }
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