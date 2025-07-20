package chatops4s.slack

import cats.effect.{IO, Resource}
import cats.implicits.*
import chatops4s.slack.models.*
import chatops4s.slack.models.SlackModels.given
import com.comcast.ip4s.*
import io.circe.parser.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class SlackServer(
                   config: SlackConfig,
                   inboundGateway: SlackInboundGateway
                 ) {

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val interactionsEndpoint = endpoint
    .post
    .in("slack" / "interactions")
    .in(formBody[String])
    .out(stringBody)
    .serverLogicSuccess[IO] { payload =>
      handleInteraction(payload).as("OK")
    }

  private val healthEndpoint = endpoint
    .get
    .in("health")
    .out(stringBody)
    .serverLogicSuccess[IO](_ => IO.pure("OK"))

  private val apiEndpoints = List(interactionsEndpoint, healthEndpoint)

  private val routes = Http4sServerInterpreter[IO]().toRoutes(apiEndpoints)

  def start: Resource[IO, Server] = {
    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString("0.0.0.0").get)
      .withPort(Port.fromInt(config.port).get)
      .withHttpApp(routes.orNotFound)
      .build
      .evalTap(_ => logger.info(s"Slack server started on port ${config.port}"))
  }

  private def handleInteraction(formData: String): IO[Unit] = {
    for {
      _ <- logger.debug(s"Received interaction: $formData")
      payload <- parsePayload(formData)
      _ <- inboundGateway.handleInteraction(payload)
    } yield ()
  }

  private def parsePayload(formData: String): IO[SlackInteractionPayload] = {
    IO.fromEither {
      for {
        decoded <- Either.catchNonFatal(URLDecoder.decode(formData, StandardCharsets.UTF_8))
        payloadJson <- {
          if (decoded.startsWith("payload=")) {
            Right(decoded.substring(8))
          } else {
            Right(decoded)
          }
        }
        payload <- decode[SlackInteractionPayload](payloadJson)
      } yield payload
    }.handleErrorWith { error =>
      logger.error(error)(s"Failed to parse interaction payload: $formData") *>
        IO.raiseError(error)
    }
  }
}