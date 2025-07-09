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
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.openapi.circe.yaml.*

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
    .name("slack-interactions")
    .description("Handle Slack interactive button clicks")
    .tag("Slack")
    .serverLogicSuccess[IO] { payload =>
      handleInteraction(payload).as("OK")
    }

  private val healthEndpoint = endpoint
    .get
    .in("health")
    .out(stringBody)
    .name("health-check")
    .description("Health check endpoint")
    .tag("System")
    .serverLogicSuccess[IO](_ => IO.pure("OK"))

  private val apiEndpoints = List(interactionsEndpoint, healthEndpoint)

  // Generate OpenAPI docs
  private val openApiDocs = SwaggerInterpreter()
    .fromServerEndpoints[IO](apiEndpoints, "ChatOps4s Slack API", "1.0.0")

  private val routes = Http4sServerInterpreter[IO]().toRoutes(
    apiEndpoints ++ openApiDocs
  )

  def start: Resource[IO, Server] = {
    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString("0.0.0.0").get)
      .withPort(Port.fromInt(config.port).get)
      .withHttpApp(routes.orNotFound)
      .build
      .evalTap(_ => logger.info(s"Slack server started on port ${config.port}"))
      .evalTap(_ => logger.info(s"Swagger UI available at: http://localhost:${config.port}/docs"))
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