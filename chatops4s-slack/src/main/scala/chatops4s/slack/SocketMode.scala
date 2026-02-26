package chatops4s.slack

import chatops4s.slack.api.{SlackAppApi, SlackAppToken}
import chatops4s.slack.api.socket
import sttp.client4.*
import sttp.monad.MonadError
import sttp.monad.syntax.*
import chatops4s.slack.monadSyntax.*

import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

private[slack] object SocketMode {

  private val logger = LoggerFactory.getLogger("chatops4s.slack.SocketMode")

  def runLoop[F[_]](
      appToken: SlackAppToken,
      backend: WebSocketBackend[F],
      handler: socket.Envelope => F[Unit],
      retryDelay: Option[F[Unit]] = None,
      shutdownSignal: AtomicBoolean = new AtomicBoolean(false),
  ): F[Unit] = {
    given monad: MonadError[F] = backend.monad
    // TODO: Thread.sleep is intentional -- the library is effect-polymorphic via sttp MonadError[F]
    // and cannot depend on IO.sleep. Users can provide a custom retryDelay parameter.
    val delay                  = retryDelay.getOrElse(monad.blocking(Thread.sleep(2000)))

    monad.eval(shutdownSignal.get()).flatMap { stopped =>
      if (stopped) monad.blocking(logger.info("Shutdown requested, stopping socket loop"))
      else {
        val loop: F[socket.DisconnectReason] = for {
          url    <- openSocketUrl(appToken, backend)
          reason <- SlackAppApi.connectToSocket(url, backend) { envelope =>
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
                .flatMap { _ =>
                  if (shutdownSignal.get()) monad.blocking(logger.info("Shutdown during reconnect, stopping"))
                  else runLoop(appToken, backend, handler, Some(delay), shutdownSignal)
                }
          }
          .handleError { case e =>
            monad
              .blocking(logger.warn(s"Socket connection error, reconnecting after delay", e))
              .flatMap { _ =>
                if (shutdownSignal.get()) monad.blocking(logger.info("Shutdown during error recovery, stopping"))
                else delay >> runLoop(appToken, backend, handler, Some(delay), shutdownSignal)
              }
          }
      }
    }
  }

  private def openSocketUrl[F[_]](
      appToken: SlackAppToken,
      backend: Backend[F],
  ): F[String] = {
    given sttp.monad.MonadError[F] = backend.monad
    SlackAppApi(backend, appToken).apps.connections.open().map(_.okOrThrow.url)
  }
}
