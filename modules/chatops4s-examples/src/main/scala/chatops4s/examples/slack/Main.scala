package chatops4s.examples.slack

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.*
import chatops4s.*
import chatops4s.slack.*
import chatops4s.slack.models.SlackConfig
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Main extends IOApp {

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] = {
    val config = SlackConfig(
      botToken = sys.env.getOrElse("SLACK_BOT_TOKEN", "xoxb-your-bot-token-here"),
      signingSecret = sys.env.getOrElse("SLACK_SIGNING_SECRET", "your-signing-secret-here"),
      port = sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(3000)
    )

    SlackGateway.create(config).use { case (outbound, inbound, server) =>
      for {
        _ <- logger.info(s"Starting Slack ChatOps server on port ${config.port}")
        _ <- logger.info("Server started! Send a test message...")

    
        _ <- runChatOpsExample(outbound, inbound)

        _ <- logger.info("ChatOps example completed. Server will keep running...")
        _ <- IO.never // Actually it help in keep my server runnning
      } yield ExitCode.Success
    }
  }

  private def runChatOpsExample(outbound: OutboundGateway, inbound: InboundGateway): IO[Unit] = {
    for {
      approveAction <- inbound.registerAction(ctx =>
        logger.info(s"✅ Approved by ${ctx.userId} in channel ${ctx.channelId}")
      )
      rejectAction <- inbound.registerAction(ctx =>
        logger.info(s"❌ Rejected by ${ctx.userId} in channel ${ctx.channelId}")
      )

      msg = Message(
        text = "🚀 Deploy to production?",
        interactions = Seq(
          approveAction.render("✅ Approve"),
          rejectAction.render("❌ Decline")
        )
      )

      channelId = sys.env.getOrElse("SLACK_CHANNEL_ID", "C1234567890")

      response <- outbound.sendToChannel(channelId, msg)
      _ <- logger.info(s"Message sent with ID: ${response.messageId}")

    
      _ <- outbound.sendToThread(
        response.messageId,
        Message("👆 Please click one of the buttons above to proceed")
      )
      _ <- logger.info("Follow-up thread message sent")

    } yield ()
  }
}