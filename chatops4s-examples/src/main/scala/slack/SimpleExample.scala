package chatops4s.examples.slack

import cats.effect.{ExitCode, IO, IOApp}
import chatops4s.{Message, OutboundGateway}
import chatops4s.slack.SlackGateway
import chatops4s.slack.models.SlackConfig
import com.typesafe.scalalogging.StrictLogging
import sttp.client4.httpclient.cats.HttpClientCatsBackend

object SimpleExample extends IOApp with StrictLogging {

  override def run(args: List[String]): IO[ExitCode] = {
    val config = SlackConfig(
      botToken = sys.env.getOrElse("SLACK_BOT_TOKEN", "xoxb-your-bot-token-here"),
      signingSecret = sys.env.getOrElse("SLACK_SIGNING_SECRET", "your-signing-secret-here"),
    )

    HttpClientCatsBackend
      .resource[IO]()
      .use { backend =>
        SlackGateway
          .createOutboundOnly(config, backend)
          .flatMap { outbound =>
            sendSimpleMessage(outbound)
          }
      }
      .as(ExitCode.Success)
  }

  private def sendSimpleMessage(outbound: OutboundGateway): IO[Unit] = {
    val channelId = sys.env.getOrElse("SLACK_CHANNEL_ID", "C1234567890")
    val message   = Message(text = "🤖 Hello from ChatOps4s! This is a test message.")

    for {
      _        <- IO(logger.info(s"Sending message to channel: $channelId"))
      response <- outbound.sendToChannel(channelId, message)
      _        <- IO(logger.info(s"✅ Message sent successfully! Message ID: ${response.messageId}"))
    } yield ()
  }
}
