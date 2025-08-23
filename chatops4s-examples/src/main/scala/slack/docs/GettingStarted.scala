package chatops4s.examples.slack.docs

import cats.effect.{ExitCode, IO, IOApp, Resource}
import chatops4s.slack.SlackGateway
import chatops4s.slack.instances.given
import chatops4s.slack.models.{SlackConfig, SlackInteractionPayload}
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import com.typesafe.scalalogging.StrictLogging
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import io.circe.parser.*
import chatops4s.slack.SlackInboundGateway
import scala.util.{Failure, Success, Try}

// start_doc
object GettingStarted extends IOApp with StrictLogging {
  override def run(args: List[String]): IO[ExitCode] = {
    val config = SlackConfig(
      botToken = sys.env.getOrElse("SLACK_BOT_TOKEN", ""),
      signingSecret = sys.env.getOrElse("SLACK_SIGNING_SECRET", ""),
      port = sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(3000),
    )

    HttpClientCatsBackend
      .resource[IO]()
      .use { backend =>
        SlackGateway.create[IO](config, backend).flatMap { case (outbound, inbound) =>
          createServer(config, inbound.asInstanceOf[SlackInboundGateway[IO]]).use { _ =>
            IO.delay(logger.info(s"Starting Slack ChatOps server on port ${config.port}")) *>
              IO.never
          }
        }
      }
      .as(ExitCode.Success)
  }

  private def createServer(config: SlackConfig, inboundGateway: SlackInboundGateway[IO]): Resource[IO, Server] = {
    val interactionsEndpoint = endpoint.post
      .in("slack" / "interactions")
      .in(stringBody)
      .out(stringBody)
      .serverLogicSuccess[IO] { payload =>
        handleInteraction(payload, inboundGateway).as("OK")
      }

    val routes = Http4sServerInterpreter[IO]().toRoutes(List(interactionsEndpoint))

    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString("0.0.0.0").get)
      .withPort(Port.fromInt(config.port).get)
      .withHttpApp(routes.orNotFound)
      .build
  }

  private def handleInteraction(formData: String, inboundGateway: SlackInboundGateway[IO]): IO[Unit] = {
    for {
      payload <- parsePayload(formData)
      _       <- inboundGateway.handleInteraction(payload)
    } yield ()
  }

  private def parsePayload(formData: String): IO[SlackInteractionPayload] = {
    val result = for {
      decoded     <- Try(URLDecoder.decode(formData, StandardCharsets.UTF_8)) match {
                       case Success(value)     => Right(value)
                       case Failure(exception) => Left(s"URL decode error: ${exception.getMessage}")
                     }
      payloadJson <- if (decoded.startsWith("payload=")) {
                       Right(decoded.substring(8))
                     } else {
                       Right(decoded)
                     }
      payload     <- parse(payloadJson).flatMap(_.as[SlackInteractionPayload]) match {
                       case Right(value) => Right(value)
                       case Left(error)  => Left(s"JSON parse error: ${error.getMessage}")
                     }
    } yield payload

    result match {
      case Right(payload) => IO.pure(payload)
      case Left(errorMsg) =>
        IO.delay(logger.error(s"Failed to parse interaction payload: $errorMsg")) *>
          IO.raiseError(new RuntimeException(errorMsg))
    }
  }
}
// end_doc
