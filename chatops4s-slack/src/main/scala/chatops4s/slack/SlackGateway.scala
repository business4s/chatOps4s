package chatops4s.slack

import cats.effect.Sync
import chatops4s.{InboundGateway, OutboundGateway}
import chatops4s.slack.models.SlackConfig
import sttp.client4.Backend

object SlackGateway {

  def create[F[_]: Sync](config: SlackConfig, backend: Backend[F]): F[(OutboundGateway[F], InboundGateway[F])] = {
    for {
      slackClient     <- Sync[F].pure(new SlackClient[F](config, backend))
      outboundGateway <- SlackOutboundGateway.create[F](slackClient)
      inboundGateway  <- SlackInboundGateway.create[F]
    } yield (outboundGateway, inboundGateway)
  }

  def createOutboundOnly[F[_]: Sync](config: SlackConfig, backend: Backend[F]): F[OutboundGateway[F]] = {
    for {
      slackClient <- Sync[F].pure(new SlackClient[F](config, backend))
      gateway     <- SlackOutboundGateway.create[F](slackClient)
    } yield gateway
  }
}