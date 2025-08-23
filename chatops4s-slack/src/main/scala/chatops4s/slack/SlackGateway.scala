package chatops4s.slack

import chatops4s.{InboundGateway, OutboundGateway}
import chatops4s.slack.models.SlackConfig
import sttp.client4.Backend

object SlackGateway {

  def create[F[_]: Monad](config: SlackConfig, backend: Backend[F]): F[(OutboundGateway[F], InboundGateway[F])] = {
    val M = Monad[F]
    M.flatMap(M.pure(new SlackClient[F](config, backend))) { slackClient =>
      M.flatMap(SlackOutboundGateway.create[F](slackClient)) { outboundGateway =>
        M.flatMap(SlackInboundGateway.create[F]) { inboundGateway =>
          M.pure((outboundGateway: OutboundGateway[F], inboundGateway: InboundGateway[F]))
        }
      }
    }
  }

  def createOutboundOnly[F[_]: Monad](config: SlackConfig, backend: Backend[F]): F[OutboundGateway[F]] = {
    val M = Monad[F]
    M.flatMap(M.pure(new SlackClient[F](config, backend))) { slackClient =>
      M.flatMap(SlackOutboundGateway.create[F](slackClient)) { gateway =>
        M.pure(gateway: OutboundGateway[F])
      }
    }
  }
}
