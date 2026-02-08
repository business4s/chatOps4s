package example

import cats.effect.{IO, IOApp}
import chatops4s.slack.{ButtonClick, ButtonId, CommandDef, CommandParser, CommandResponse, SlackGateway, SlackSetup}
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

object Main extends IOApp.Simple {

  // Paste at https://api.slack.com/apps → Create New App → From an app manifest
  println(SlackSetup.manifest(
    appName = "ChatOps4sExample",
    commands = Seq(CommandDef("/deploy", "Deploy a version to production")),
  ))

  private val token    = sys.env.getOrElse("SLACK_BOT_TOKEN", "xoxb-your-token")
  private val appToken = sys.env.getOrElse("SLACK_APP_TOKEN", "xapp-your-app-token")
  private val channel  = sys.env.getOrElse("SLACK_CHANNEL", "#testing-slack-app")

  override def run: IO[Unit] = {
    HttpClientFs2Backend.resource[IO]().use { backend =>
      for {
        slack      <- SlackGateway.create(token, appToken, backend)
        approveBtn <- slack.onButton[Version](onApprove(slack))
        rejectBtn  <- slack.onButton[Version](onReject(slack))
        _          <- slack.onCommand[Version]("deploy")(onDeploy(slack, approveBtn, rejectBtn))
        slackFiber <- slack.listen.start
        _          <- slackFiber.join
      } yield ()
    }
  }

  opaque type Version <: String = String

  object Version {
    def apply(v: String): Version = v
  }

  given CommandParser[Version] with {
    def parse(text: String): Either[String, Version] =
      if (text.matches("""v?\d+(\.\d+)*""")) Right(Version(text))
      else Left(s"'$text' is not a valid version (expected e.g. v1.2.3)")
  }

  private def onApprove(slack: SlackGateway[IO])(click: ButtonClick[Version]): IO[Unit] =
    for {
      _ <- slack.update(
             click.messageId,
             s"""${prompt(click.value)}
                |:white_check_mark: *Approved* by <@${click.userId}>""".stripMargin,
           )
      _ <- slack.reply(click.messageId, "Deploying to production...")
    } yield ()

  private def onReject(slack: SlackGateway[IO])(click: ButtonClick[Version]): IO[Unit] =
    slack.update(
      click.messageId,
      s"""${prompt(click.value)}
         |:x: *Rejected* by <@${click.userId}>""".stripMargin,
    ).void

  private def onDeploy(
      slack: SlackGateway[IO],
      approveBtn: ButtonId[Version],
      rejectBtn: ButtonId[Version],
  )(cmd: chatops4s.slack.Command[Version]): IO[CommandResponse] =
    slack.send(
      cmd.channelId,
      prompt(cmd.args),
      Seq(
        approveBtn.toButton("Approve", cmd.args),
        rejectBtn.toButton("Reject", cmd.args),
      ),
    ).as(CommandResponse.Silent)

  private def prompt(v: Version): String = s"Deploy $v to production?"
}
