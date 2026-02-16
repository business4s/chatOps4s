package example.docs

import cats.effect.{IO, IOApp}
import chatops4s.slack.{CommandResponse, FormDef, SlackGateway}
import chatops4s.slack.api.{SlackAppToken, SlackBotToken}
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

// start_minimal
object SendMessage extends IOApp.Simple {

  private val token    = SlackBotToken.unsafe(sys.env("SLACK_BOT_TOKEN"))
  private val appToken = SlackAppToken.unsafe(sys.env("SLACK_APP_TOKEN"))
  private val channel  = sys.env("SLACK_CHANNEL")

  override def run: IO[Unit] =
    HttpClientFs2Backend.resource[IO]().use { backend =>
      for {
        slack    <- SlackGateway.create(token, backend)
        _        <- slack.send(channel, "Hello from ChatOps4s!")
        manifest <- slack.manifest("MyApp")
        _        <- IO.println(manifest)
        _        <- slack.listen(appToken)
      } yield ()
    }
}
// end_minimal

// start_buttons
object InteractiveButtons extends IOApp.Simple {

  private val token    = SlackBotToken.unsafe(sys.env("SLACK_BOT_TOKEN"))
  private val appToken = SlackAppToken.unsafe(sys.env("SLACK_APP_TOKEN"))
  private val channel  = sys.env("SLACK_CHANNEL")

  override def run: IO[Unit] =
    HttpClientFs2Backend.resource[IO]().use { backend =>
      for {
        slack      <- SlackGateway.create(token, backend)
        approveBtn <- slack.registerButton[String] { click =>
                        slack.update(click.messageId, s"Approved by <@${click.userId}>")
                          .void
                      }
        rejectBtn  <- slack.registerButton[String] { click =>
                        slack.update(click.messageId, s"Rejected by <@${click.userId}>")
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
        _          <- slack.listen(appToken)
      } yield ()
    }
}
// end_buttons

// start_forms
object InteractiveForms extends IOApp.Simple {

  private val token    = SlackBotToken.unsafe(sys.env("SLACK_BOT_TOKEN"))
  private val appToken = SlackAppToken.unsafe(sys.env("SLACK_APP_TOKEN"))
  private val channel  = sys.env("SLACK_CHANNEL")

  case class DeployForm(service: String, version: String, dryRun: Boolean) derives FormDef

  override def run: IO[Unit] =
    HttpClientFs2Backend.resource[IO]().use { backend =>
      for {
        slack      <- SlackGateway.create(token, backend)
        deployForm <- slack.registerForm[DeployForm] { submission =>
                        val form = submission.values
                        slack.send(channel, s"Deploying ${form.service} ${form.version}")
                          .void
                      }
        _          <- slack.registerCommand[String]("deploy-form", "Open deployment form") { cmd =>
                        slack.openForm(cmd.triggerId, deployForm, "Deploy Service")
                          .as(CommandResponse.Silent)
                      }
        _          <- slack.listen(appToken)
      } yield ()
    }
}
// end_forms
