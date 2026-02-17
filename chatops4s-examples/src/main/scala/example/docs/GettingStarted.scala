package example.docs

import cats.effect.{IO, IOApp}
import chatops4s.slack.{CommandResponse, FormDef, SlackGateway, SlackSetup}
import chatops4s.slack.api.{SlackAppToken, SlackBotToken}
import sttp.client4.WebSocketBackend
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

// start_minimal
object SendMessage extends IOApp.Simple {

  override def run: IO[Unit] =
    HttpClientFs2Backend.resource[IO]().use { backend =>
      for {
        slack    <- SlackGateway.create(backend)
        _        <- slack.verifySetup("MyApp", "slack-manifest.yml")
        _        <- slack.start(
                      SlackBotToken.unsafe(sys.env("SLACK_BOT_TOKEN")),
                      sys.env.get("SLACK_APP_TOKEN").map(SlackAppToken.unsafe),
                    )
      } yield ()
    }
}
// end_minimal

// start_buttons
object InteractiveButtons extends IOApp.Simple {

  private val channel  = sys.env("SLACK_CHANNEL")

  override def run: IO[Unit] =
    HttpClientFs2Backend.resource[IO]().use { backend =>
      for {
        slack      <- SlackGateway.create(backend)
        approveBtn <- slack.registerButton[String] { click =>
                        slack.update(click.messageId, s"Approved by <@${click.userId}>")
                          .void
                      }
        rejectBtn  <- slack.registerButton[String] { click =>
                        slack.update(click.messageId, s"Rejected by <@${click.userId}>")
                          .void
                      }
        _          <- slack.registerCommand[String]("deploy", "Deploy to production") { cmd =>
                        slack.send(
                          channel,
                          "Deploy v1.2.3 to production?",
                          Seq(
                            approveBtn.render("Approve", "v1.2.3"),
                            rejectBtn.render("Reject", "v1.2.3"),
                          ),
                        ).as(CommandResponse.Silent)
                      }
        _          <- slack.verifySetup("InteractiveButtons", "slack-manifest.yml")
        _          <- slack.start(
                        SlackBotToken.unsafe(sys.env("SLACK_BOT_TOKEN")),
                        Some(SlackAppToken.unsafe(sys.env("SLACK_APP_TOKEN"))),
                      )
      } yield ()
    }
}
// end_buttons

// start_forms
object InteractiveForms extends IOApp.Simple {

  private val channel  = sys.env("SLACK_CHANNEL")

  case class DeployForm(service: String, version: String, dryRun: Boolean) derives FormDef

  override def run: IO[Unit] =
    HttpClientFs2Backend.resource[IO]().use { backend =>
      for {
        slack      <- SlackGateway.create(backend)
        // start_form_register
        deployForm <- slack.registerForm[DeployForm] { submission =>
                        val form = submission.values
                        slack.send(channel, s"Deploying ${form.service} ${form.version}")
                          .void
                      }
        _          <- slack.registerCommand[String]("deploy-form", "Open deployment form") { cmd =>
                        slack.openForm(cmd.triggerId, deployForm, "Deploy Service")
                          .as(CommandResponse.Silent)
                      }
        // end_form_register
        _          <- slack.verifySetup("InteractiveForms", "slack-manifest.yml")
        _          <- slack.start(
                        SlackBotToken.unsafe(sys.env("SLACK_BOT_TOKEN")),
                        Some(SlackAppToken.unsafe(sys.env("SLACK_APP_TOKEN"))),
                      )
      } yield ()
    }
}
// end_forms

private object HeroSnippet {
  val channel  = sys.env("SLACK_CHANNEL")
  val botToken = SlackBotToken.unsafe(sys.env("SLACK_BOT_TOKEN"))
  val appToken = SlackAppToken.unsafe(sys.env("SLACK_APP_TOKEN"))

  def run(backend: WebSocketBackend[IO]): IO[Unit] =
    // start_hero
    for {
      slack      <- SlackGateway.create(backend)
      approveBtn <- slack.registerButton[String] { click =>
                      slack.update(click.messageId, s"Approved by <@${click.userId}>").void
                    }
      rejectBtn  <- slack.registerButton[String] { click =>
                      slack.update(click.messageId, s"Rejected by <@${click.userId}>").void
                    }
      _          <- slack.registerCommand[String]("deploy", "Deploy to production") { cmd =>
                      slack.send(channel, "Deploy v1.2.3?", Seq(
                        approveBtn.render("Approve", "v1.2.3"),
                        rejectBtn.render("Reject", "v1.2.3"),
                      )).as(CommandResponse.Silent)
                    }
      _          <- slack.verifySetup("MyApp", "slack-manifest.yml")
      _          <- slack.start(botToken, Some(appToken))
    } yield ()
    // end_hero
}
