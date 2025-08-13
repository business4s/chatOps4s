package chatops4s.slack

import cats.effect.{IO, Resource}
import chatops4s.{InboundGateway, OutboundGateway}
import chatops4s.slack.models.SlackConfig
import sttp.client4.Backend

object SlackGateway {

  def create(config: SlackConfig, backend: Backend[IO]): Resource[IO, (OutboundGateway, InboundGateway)] = {
    Resource.eval {
      for {
        slackClient     <- IO.pure(new SlackClient(config, backend))
        outboundGateway <- SlackOutboundGateway.create(slackClient)
        inboundGateway  <- SlackInboundGateway.create
      } yield (outboundGateway, inboundGateway)
    }
  }

  def createOutboundOnly(config: SlackConfig, backend: Backend[IO]): Resource[IO, OutboundGateway] = {
    Resource.eval {
      for {
        slackClient <- IO.pure(new SlackClient(config, backend))
        gateway     <- SlackOutboundGateway.create(slackClient)
      } yield gateway
    }
  }
}
