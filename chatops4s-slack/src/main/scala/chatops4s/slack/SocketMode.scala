package chatops4s.slack

import chatops4s.slack.api.SlackApi
import chatops4s.slack.api.socket
import sttp.client4.*
import sttp.monad.MonadError
import sttp.monad.syntax.*
import chatops4s.slack.monadSyntax.*

import org.slf4j.LoggerFactory

private[slack] object SocketMode {

  private val logger = LoggerFactory.getLogger("chatops4s.slack.SocketMode")

  def runLoop[F[_]](
      appToken: String,
      backend: WebSocketBackend[F],
      handler: socket.Envelope => F[Unit],
      retryDelay: Option[F[Unit]] = None,
  ): F[Unit] = {
    given monad: MonadError[F] = backend.monad
    val delay                  = retryDelay.getOrElse(monad.blocking(Thread.sleep(2000)))

    val loop: F[socket.DisconnectReason] = for {
      url    <- openSocketUrl(appToken, backend)
      reason <- SlackApi.apps.connectToSocket(url, backend) { envelope =>
                  handler(envelope).handleError { case e =>
                    monad.blocking(logger.error("Handler error", e))
                  }
                }
    } yield reason

    loop
      .flatMap {
        case socket.DisconnectReason.LinkDisabled =>
          monad.blocking(logger.warn("Slack socket link disabled, stopping"))
        case reason                               =>
          monad
            .blocking(logger.info(s"Slack socket disconnect: $reason, reconnecting"))
            .flatMap(_ => runLoop(appToken, backend, handler, Some(delay)))
      }
      .handleError { case e =>
        monad
          .blocking(logger.warn(s"Socket connection error, reconnecting after delay", e))
          .flatMap(_ => delay >> runLoop(appToken, backend, handler, Some(delay)))
      }
  }

  private def openSocketUrl[F[_]](
      appToken: String,
      backend: Backend[F],
  ): F[String] = {
    given sttp.monad.MonadError[F] = backend.monad
    SlackApi.apps.connectionsOpen(backend, appToken).map(_.okOrThrow.url)
  }
}
