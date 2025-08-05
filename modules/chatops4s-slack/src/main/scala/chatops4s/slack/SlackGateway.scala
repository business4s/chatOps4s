package chatops4s.slack

import cats.effect.{IO, Resource}
import chatops4s.{InboundGateway, OutboundGateway}
import chatops4s.slack.models.SlackConfig
import sttp.client4.Backend

object SlackGateway {

  def create(config: SlackConfig, backend: Backend[IO]): Resource[IO, (OutboundGateway, InboundGateway)] = {
    for {
      slackClient      <- Resource.eval(IO.pure(new SlackClient(config, backend)))
      outboundGateway <- Resource.eval(SlackOutboundGateway.create(slackClient))
      inboundGateway  <- Resource.eval(SlackInboundGateway.create)
    } yield (outboundGateway, inboundGateway)
  }

  def createOutboundOnly(config: SlackConfig, backend: Backend[IO]): Resource[IO, OutboundGateway] = {
    Resource.eval {
      val slackClient = new SlackClient(config, backend)
      SlackOutboundGateway.create(slackClient)
    }
  }
}