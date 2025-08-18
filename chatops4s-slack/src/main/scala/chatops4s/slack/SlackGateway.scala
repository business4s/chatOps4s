package chatops4s.slack

import cats.effect.IO
import chatops4s.{InboundGateway, OutboundGateway}
import chatops4s.slack.models.SlackConfig
import sttp.client4.Backend

object SlackGateway {
 //Resources eliminated
  def create(config: SlackConfig, backend: Backend[IO]): IO[(OutboundGateway, InboundGateway)] = {
    for {
      slackClient     <- IO.pure(new SlackClient(config, backend))
      outboundGateway <- SlackOutboundGateway.create(slackClient)
      inboundGateway  <- SlackInboundGateway.create
    } yield (outboundGateway, inboundGateway)
  }

  def createOutboundOnly(config: SlackConfig, backend: Backend[IO]): IO[OutboundGateway] = {
    for {
      slackClient <- IO.pure(new SlackClient(config, backend))
      gateway     <- SlackOutboundGateway.create(slackClient)
    } yield gateway
  }
}