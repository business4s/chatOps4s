package example

import cats.effect.{IO, IOApp}
import chatops4s.slack.{ButtonClick, ButtonId, SlackGateway, SlackSetup}
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

object Main extends IOApp.Simple {

  // Paste at https://api.slack.com/apps → Create New App → From an app manifest
  println(SlackSetup.manifest(appName = "ChatOps4sExample"))

  private val token    = sys.env.getOrElse("SLACK_BOT_TOKEN", "xoxb-your-token")
  private val appToken = sys.env.getOrElse("SLACK_APP_TOKEN", "xapp-your-app-token")
  private val channel  = sys.env.getOrElse("SLACK_CHANNEL", "#testing-slack-app")

  override def run: IO[Unit] = {
    HttpClientFs2Backend.resource[IO]().use { backend =>
      for {
        slack      <- SlackGateway.create(token, appToken, backend)
        approveBtn <- slack.onButton[Version](onApprove)
        rejectBtn  <- slack.onButton[Version](onReject)
        slackFiber <- slack.listen.start
        _          <- requestApproval(Version("1.2.3"))(slack, approveBtn, rejectBtn)
        _          <- slackFiber.join
      } yield ()
    }
  }

  private def requestApproval(version: Version)(
      slack: SlackGateway[IO],
      approveBtn: ButtonId[Version],
      rejectBtn: ButtonId[Version],
  ): IO[Unit] = {
    slack
      .send(
        channel,
        prompt(version),
        Seq(
          approveBtn.toButton("Approve", version),
          rejectBtn.toButton("Reject", version),
        ),
      )
      .void
  }

  opaque type Version <: String = String

  object Version {
    def apply(v: String): Version = v
  }

  private def onApprove(click: ButtonClick[Version], gw: SlackGateway[IO]): IO[Unit] =
    for {
      _ <- gw.update(
             click.messageId,
             s"""${prompt(click.value)}
                |:white_check_mark: *Approved* by <@${click.userId}>""".stripMargin,
           )
      _ <- gw.reply(click.messageId, "Deploying to production...").void
    } yield ()

  private def onReject(click: ButtonClick[Version], gw: SlackGateway[IO]): IO[Unit] =
    gw.update(
      click.messageId,
      s"""${prompt(click.value)}
         |:x: *Rejected* by <@${click.userId}>""".stripMargin,
    ).void

  private def prompt(v: Version): String = s"Deploy $v to production?"
}
