package chatops4s.slack

import chatops4s.{InboundGateway, OutboundGateway}
import chatops4s.slack.models.SlackConfig
import sttp.client4.Backend
import sttp.monad.MonadError

object SlackGateway {

  def create[F[_]](config: SlackConfig, backend: Backend[F]): F[(OutboundGateway[F], InboundGateway[F])] = {
    implicit val monad: MonadError[F] = backend.monad

    val slackClient = new SlackClient[F](config, backend)

    monad.flatMap(SlackOutboundGateway.create[F](slackClient)) { outboundGateway =>
      monad.flatMap(SlackInboundGateway.create[F]) { inboundGateway =>
        monad.unit((outboundGateway: OutboundGateway[F], inboundGateway: InboundGateway[F]))
      }
    }
  }

  def createOutboundOnly[F[_]](config: SlackConfig, backend: Backend[F]): F[OutboundGateway[F]] = {
    implicit val monad: MonadError[F] = backend.monad

    val slackClient = new SlackClient[F](config, backend)

    monad.flatMap(SlackOutboundGateway.create[F](slackClient)) { gateway =>
      monad.unit(gateway: OutboundGateway[F])
    }
  }
}
