package chatops4s.examples.slack

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.implicits.*
import chatops4s.*
import chatops4s.slack.*
import chatops4s.slack.models.{SlackConfig, SlackInteractionPayload}
import com.comcast.ip4s.*
import io.circe.parser.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import com.typesafe.scalalogging.StrictLogging
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object Main extends IOApp with StrictLogging {
  override def run(args: List[String]): IO[ExitCode] = {
    val config = SlackConfig(
      botToken = sys.env.getOrElse("SLACK_BOT_TOKEN", ""),
      signingSecret = sys.env.getOrElse("SLACK_SIGNING_SECRET", ""),
      port = sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(3000),
    )

    if (config.botToken.isEmpty || config.signingSecret.isEmpty) {
      IO.delay(logger.error("Missing SLACK_BOT_TOKEN or SLACK_SIGNING_SECRET environment variables")) *>
        IO.pure(ExitCode.Error)
    } else {
      HttpClientCatsBackend.resource[IO]().use { backend =>
        SlackGateway.create[IO](config, backend).flatMap { case (outbound, inbound) =>
          createServer(config, inbound.asInstanceOf[SlackInboundGateway[IO]]).use { _ =>
            for {
              _ <- IO.delay(logger.info(s"Starting Slack ChatOps server on port ${config.port}"))
              _ <- IO.delay(logger.info("Server started! Send a test message..."))

              _ <- runChatOpsExample(outbound, inbound)

              _ <- IO.delay(logger.info("ChatOps example completed. Server will keep running..."))
              _ <- IO.never // Keep server running
            } yield ExitCode.Success
          }
        }
      }
    }
  }

  private def createServer(config: SlackConfig, inboundGateway: SlackInboundGateway[IO]): Resource[IO, Server] = {
    val interactionsEndpoint = endpoint.post
      .in("slack" / "interactions")
      .in(stringBody)
      .out(stringBody)
      .serverLogicSuccess[IO] { payload =>
        handleInteraction(payload, inboundGateway).as("OK")
      }

    val healthEndpoint = endpoint.get
      .in("health")
      .out(stringBody)
      .serverLogicSuccess[IO](_ => IO.pure("OK"))

    val apiEndpoints = List(interactionsEndpoint, healthEndpoint)
    val routes       = Http4sServerInterpreter[IO]().toRoutes(apiEndpoints)

    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString("0.0.0.0").get)
      .withPort(Port.fromInt(config.port).get)
      .withHttpApp(routes.orNotFound)
      .build
      .evalTap(_ => IO.delay(logger.info(s"Slack server started on port ${config.port}")))
  }

  private def handleInteraction(formData: String, inboundGateway: SlackInboundGateway[IO]): IO[Unit] = {
    for {
      _       <- IO.delay(logger.debug(s"Received interaction: $formData"))
      payload <- parsePayload(formData)
      _       <- inboundGateway.handleInteraction(payload)
    } yield ()
  }

  private def parsePayload(formData: String): IO[SlackInteractionPayload] = {
    IO.fromEither {
      for {
        decoded     <- Either.catchNonFatal(URLDecoder.decode(formData, StandardCharsets.UTF_8))
        payloadJson <- {
          if (decoded.startsWith("payload=")) {
            Right(decoded.substring(8))
          } else {
            Right(decoded)
          }
        }
        payload     <- decode[SlackInteractionPayload](payloadJson)
      } yield payload
    }.handleErrorWith { error =>
      IO.delay(logger.error(s"Failed to parse interaction payload: $formData", error)) *>
        IO.raiseError(error)
    }
  }

  private def runChatOpsExample(outbound: OutboundGateway[IO], inbound: InboundGateway[IO]): IO[Unit] = {
    for {
      approveAction <- inbound.registerAction(ctx => IO.delay(logger.info(s"✅ Approved by ${ctx.userId} in channel ${ctx.channelId}")))
      rejectAction  <- inbound.registerAction(ctx => IO.delay(logger.info(s"❌ Rejected by ${ctx.userId} in channel ${ctx.channelId}")))

      msg = Message(
              text = "🚀 Deploy to production?",
              interactions = Seq(
                approveAction.render("✅ Approve"),
                rejectAction.render("❌ Decline"),
              ),
            )

      channelId = sys.env.getOrElse("SLACK_CHANNEL_ID", "C1234567890")

      response <- outbound.sendToChannel(channelId, msg)
      _        <- IO.delay(logger.info(s"Message sent with ID: ${response.messageId}"))

      _ <- outbound.sendToThread(
             response.messageId,
             Message("👆 Please click one of the buttons above to proceed"),
           )
      _ <- IO.delay(logger.info("Follow-up thread message sent"))

    } yield ()
  }
}
