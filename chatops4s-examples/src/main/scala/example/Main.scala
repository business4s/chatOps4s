package example

import cats.effect.{IO, IOApp}
import chatops4s.slack.{Button, SlackGateway, SlackSetup}
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

object Main extends IOApp.Simple {

  // Paste at https://api.slack.com/apps → Create New App → From an app manifest
  println(
    SlackSetup.manifest(
      appName = "ChatOps4sExample",
      botName = "ChatOps4sExample",
    ),
  )

  scala.io.StdIn.readLine("Press enter to start...")

  val token    = sys.env.getOrElse("SLACK_BOT_TOKEN", "xoxb-your-token")
  val appToken = sys.env.getOrElse("SLACK_APP_TOKEN", "xapp-your-app-token")
  val channel  = sys.env.getOrElse("SLACK_CHANNEL", "C0123456789")

  type DeployAction = "approve" | "reject"

  override def run: IO[Unit] = {
    HttpClientFs2Backend.resource[IO]().use { backend =>
      for {
        slack     <- SlackGateway.create(token, appToken, backend)
        deployBtn <- slack.onButton[DeployAction] { (click, gw) =>
                       click.value match {
                         case "approve" => gw.reply(click.messageId, s"Approved by <@${click.userId}>").void
                         case "reject"  => gw.reply(click.messageId, s"Rejected by <@${click.userId}>").void
                       }
                     }
        slackFiber <- slack.listen.start
        _          <- slack.send(
                        channel,
                        "Deploy to production?",
                        Seq(
                          Button("Approve", deployBtn, "approve"),
                          Button("Reject", deployBtn, "reject"),
                        ),
                      )
        _          <- slackFiber.join
      } yield ()
    }
  }
}
