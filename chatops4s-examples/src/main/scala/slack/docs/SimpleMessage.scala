package chatops4s.examples.slack.docs

import cats.effect.{ExitCode, IO, IOApp}
import chatops4s.{Message, OutboundGateway}
import chatops4s.slack.SlackGateway
import chatops4s.slack.instances.given
import chatops4s.slack.models.SlackConfig
import com.typesafe.scalalogging.StrictLogging
import sttp.client4.httpclient.cats.HttpClientCatsBackend

object SimpleMessage extends IOApp with StrictLogging {
  override def run(args: List[String]): IO[ExitCode] = {
    // start_doc
    val config = SlackConfig(
      botToken = "xoxb-your-bot-token-here",
      signingSecret = "your-signing-secret-here",
    )

    HttpClientCatsBackend.resource[IO]().use { backend =>
      SlackGateway.createOutboundOnly[IO](config, backend).flatMap { outbound =>
        val channelId = "C1234567890" // Your channel ID
        val message   = Message(text = "🤖 Hello from ChatOps4s! This is a test message.")

        outbound.sendToChannel(channelId, message).map { response =>
          logger.info(s"✅ Message sent successfully! Message ID: ${response.messageId}")
          ExitCode.Success
        }
      }
    }
    // end_doc
  }
}
