package chatops4s.examples.slack

import cats.effect.{ExitCode, IO, IOApp}
import chatops4s.{Message, OutboundGateway}
import chatops4s.slack.SlackGateway
import chatops4s.slack.models.SlackConfig
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger


object SimpleExample extends IOApp {

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] = {
    val config = SlackConfig(
      botToken = sys.env.getOrElse("SLACK_BOT_TOKEN", "xoxb-your-bot-token-here"),
      signingSecret = sys.env.getOrElse("SLACK_SIGNING_SECRET", "your-signing-secret-here")
    )

    SlackGateway.createOutboundOnly(config).use { outbound =>
      sendSimpleMessage(outbound)
    }.as(ExitCode.Success)
  }

  private def sendSimpleMessage(outbound: OutboundGateway): IO[Unit] = {
    val channelId = sys.env.getOrElse("SLACK_CHANNEL_ID", "C1234567890")
    val message = Message(text = "🤖 Hello from ChatOps4s! This is a test message.")

    for {
      _ <- logger.info(s"Sending message to channel: $channelId")
      response <- outbound.sendToChannel(channelId, message)
      _ <- logger.info(s"✅ Message sent successfully! Message ID: ${response.messageId}")
    } yield ()
  }
}