package example

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import chatops4s.slack.{ButtonClick, ButtonId, CommandParser, CommandResponse, SlackGateway}
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

object Main extends IOApp.Simple {

  private val token    = sys.env.getOrElse("SLACK_BOT_TOKEN", "xoxb-your-token")
  private val appToken = sys.env.getOrElse("SLACK_APP_TOKEN", "xapp-your-app-token")
  private val channel  = sys.env.getOrElse("SLACK_CHANNEL", "#testing-slack-app")

  override def run: IO[Unit] = {
    HttpClientFs2Backend.resource[IO]().use { backend =>
      for {
        slack      <- SlackGateway.create(token, backend)
        approveBtn <- slack.onButton[Version](onApprove(slack))
        rejectBtn  <- slack.onButton[Version](onReject(slack))
        _          <- slack.onCommand[Version]("deploy", "Deploy a version to production")(onDeploy(slack, approveBtn, rejectBtn))
        manifest   <- slack.manifest("ChatOps4sExample")
        _          <- IO.println(manifest)
        slackFiber <- slack.listen(appToken).start
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
    click.threadId.traverse_ { parent =>
      for {
        _ <- slack.update(click.messageId, s":white_check_mark: *Approved* by <@${click.userId}>")
        _ <- slack.removeReaction(parent, "hourglass_flowing_sand")
        _ <- slack.addReaction(parent, "white_check_mark")
        _ <- slack.reply(parent, "Deploying to production...")
      } yield ()
    }

  private def onReject(slack: SlackGateway[IO])(click: ButtonClick[Version]): IO[Unit] =
    click.threadId.traverse_ { parent =>
      for {
        _ <- slack.update(click.messageId, s":x: *Rejected* by <@${click.userId}>")
        _ <- slack.removeReaction(parent, "hourglass_flowing_sand")
        _ <- slack.addReaction(parent, "x")
      } yield ()
    }

  private def onDeploy(
      slack: SlackGateway[IO],
      approveBtn: ButtonId[Version],
      rejectBtn: ButtonId[Version],
  )(cmd: chatops4s.slack.Command[Version]): IO[CommandResponse] =
    for {
      msg <- slack.send(cmd.channelId, s"Deploying *${cmd.args}* to production")
      _   <- slack.addReaction(msg, "hourglass_flowing_sand")
      _   <- slack.reply(
               msg,
               s"Approve deployment of *${cmd.args}*?",
               Seq(
                 approveBtn.toButton("Approve", cmd.args),
                 rejectBtn.toButton("Reject", cmd.args),
               ),
             )
    } yield CommandResponse.Silent
}
