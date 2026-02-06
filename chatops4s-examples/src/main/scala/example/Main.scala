package example

import cats.effect.{IO, IOApp}
import chatops4s.slack.{Button, ButtonId, SlackGateway, SlackSetup}
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.tapir.server.http4s.Http4sServerInterpreter

object Main extends IOApp.Simple {

  // Paste at https://api.slack.com/apps → Create New App → From an app manifest
  println(
    SlackSetup.manifest(
      appName = "DeployBot",
      botName = "deploybot",
      interactionsUrl = "https://your-domain.com/slack/interactions",
    ),
  )

  val token         = sys.env.getOrElse("SLACK_BOT_TOKEN", "xoxb-your-token")
  val signingSecret = sys.env.getOrElse("SLACK_SIGNING_SECRET", "your-secret")
  val channel       = sys.env.getOrElse("SLACK_CHANNEL", "C0123456789")

  override def run: IO[Unit] = {
    val resources = for {
      backend <- HttpClientCatsBackend.resource[IO]()
      result  <- SlackGateway.create(token, signingSecret, backend) { setup =>
                   for {
                     approve <- setup.onButton { (click, slack) =>
                                  slack.reply(click.messageId, s"Approved by <@${click.userId}>").void
                                }
                     reject  <- setup.onButton { (click, slack) =>
                                  slack.reply(click.messageId, s"Rejected by <@${click.userId}>").void
                                }
                   } yield (approve, reject)
                 }
    } yield result

    resources.use { case (slack, (approve, reject)) =>
      for {
        _ <- slack.send(
               channel,
               "Deploy to production?",
               Seq(
                 Button("Approve", approve),
                 Button("Reject", reject),
               ),
             )

        routes = Http4sServerInterpreter[IO]().toRoutes(slack.interactionEndpoint)
        _     <- EmberServerBuilder
                   .default[IO]
                   .withHost(host"0.0.0.0")
                   .withPort(port"3000")
                   .withHttpApp(routes.orNotFound)
                   .build
                   .useForever
      } yield ()
    }
  }
}
