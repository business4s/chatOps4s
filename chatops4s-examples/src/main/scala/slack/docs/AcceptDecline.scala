package chatops4s.examples.slack.docs

import cats.effect.{ExitCode, IO, IOApp}
import chatops4s.{InboundGateway, Message, OutboundGateway}
import chatops4s.slack.SlackGateway
import chatops4s.slack.models.SlackConfig
import com.typesafe.scalalogging.StrictLogging
import sttp.client4.httpclient.cats.HttpClientCatsBackend

object AcceptDecline extends IOApp with StrictLogging {
  override def run(args: List[String]): IO[ExitCode] = {
    // start_doc
    val config = SlackConfig(
      botToken = "",
      signingSecret = "",
    )

    HttpClientCatsBackend.resource[IO]().use { backend =>
      SlackGateway.create[IO](config, backend).flatMap { case (outbound, inbound) =>
        for {
          acceptAction  <- inbound.registerAction(_ => IO.println("You pressed accept!"))
          declineAction <- inbound.registerAction(_ => IO.println("You pressed decline!"))

          message = Message(
                      text = "Deploy to production?",
                      interactions = Seq(
                        acceptAction.render("Accept"),
                        declineAction.render("Decline"),
                      ),
                    )

          _ <- outbound.sendToChannel("", message)
        } yield ExitCode.Success
      }
    }
    // end_doc
  }
}
