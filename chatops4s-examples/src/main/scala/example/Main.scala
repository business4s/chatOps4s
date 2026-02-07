package example

import cats.effect.{IO, IOApp}
import chatops4s.slack.{Button, SlackGateway, SlackSetup}
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

object Main extends IOApp.Simple {

  // Paste at https://api.slack.com/apps → Create New App → From an app manifest
  println(
    SlackSetup.manifest(
      appName = "DeployBot",
      botName = "deploybot",
    ),
  )

  val token    = sys.env.getOrElse("SLACK_BOT_TOKEN", "xoxb-your-token")
  val appToken = sys.env.getOrElse("SLACK_APP_TOKEN", "xapp-your-app-token")
  val channel  = sys.env.getOrElse("SLACK_CHANNEL", "C0123456789")

  override def run: IO[Unit] = {
    HttpClientFs2Backend.resource[IO]().use { backend =>
      for {
        slack      <- SlackGateway.create(token, appToken, backend)
        approve    <- slack.onButton { (click, gw) =>
                        gw.reply(click.messageId, s"Approved by <@${click.userId}>").void
                      }
        reject     <- slack.onButton { (click, gw) =>
                        gw.reply(click.messageId, s"Rejected by <@${click.userId}>").void
                      }
        slackFiber <- slack.listen.start
        _          <- slack.send(
                        channel,
                        "Deploy to production?",
                        Seq(
                          Button("Approve", approve),
                          Button("Reject", reject),
                        ),
                      )
        _          <- slackFiber.join
      } yield ()
    }
  }
}
