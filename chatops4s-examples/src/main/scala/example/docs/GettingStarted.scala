package example.docs

import cats.effect.{IO, IOApp}
import chatops4s.slack.{SlackGateway, SlackSetup}
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

// start_minimal
object SendMessage extends IOApp.Simple {

  println(SlackSetup.manifest(appName = "MyApp"))

  private val token    = sys.env("SLACK_BOT_TOKEN")
  private val appToken = sys.env("SLACK_APP_TOKEN")
  private val channel  = sys.env("SLACK_CHANNEL")

  override def run: IO[Unit] =
    HttpClientFs2Backend.resource[IO]().use { backend =>
      for {
        slack <- SlackGateway.create(token, appToken, backend)
        _     <- slack.send(channel, "Hello from ChatOps4s!")
        _     <- slack.listen
      } yield ()
    }
}
// end_minimal

// start_buttons
object InteractiveButtons extends IOApp.Simple {

  private val token    = sys.env("SLACK_BOT_TOKEN")
  private val appToken = sys.env("SLACK_APP_TOKEN")
  private val channel  = sys.env("SLACK_CHANNEL")

  override def run: IO[Unit] =
    HttpClientFs2Backend.resource[IO]().use { backend =>
      for {
        slack      <- SlackGateway.create(token, appToken, backend)
        approveBtn <- slack.onButton[String] { (click, gw) =>
                        gw.update(click.messageId, s"Approved by <@${click.userId}>")
                          .void
                      }
        rejectBtn  <- slack.onButton[String] { (click, gw) =>
                        gw.update(click.messageId, s"Rejected by <@${click.userId}>")
                          .void
                      }
        _          <- slack.send(
                        channel,
                        "Deploy v1.2.3 to production?",
                        Seq(
                          approveBtn.toButton("Approve", "v1.2.3"),
                          rejectBtn.toButton("Reject", "v1.2.3"),
                        ),
                      )
        _          <- slack.listen
      } yield ()
    }
}
// end_buttons
