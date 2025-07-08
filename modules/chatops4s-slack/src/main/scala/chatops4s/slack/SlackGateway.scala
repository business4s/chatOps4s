package chatops4s.slack

import cats.effect.{IO, Resource}
import chatops4s.{InboundGateway, OutboundGateway}
import chatops4s.slack.models.SlackConfig
import org.http4s.server.Server
import sttp.client4.cats.CatsBackend
import sttp.client4.httpclient.cats.HttpClientCatsBackend

object SlackGateway {

  def create(config: SlackConfig): Resource[IO, (OutboundGateway, InboundGateway, Server)] = {
    for {
      backend <- HttpClientCatsBackend.resource[IO]()
      slackClient = new SlackClient(config, backend)
      outboundGateway = new SlackOutboundGateway(slackClient)
      inboundGateway = new SlackInboundGateway()
      server <- new SlackServer(config, inboundGateway).start
    } yield (outboundGateway, inboundGateway, server)
  }

  def createOutboundOnly(config: SlackConfig): Resource[IO, OutboundGateway] = {
    HttpClientCatsBackend.resource[IO]().map { backend =>
      val slackClient = new SlackClient(config, backend)
      new SlackOutboundGateway(slackClient)
    }
  }
}