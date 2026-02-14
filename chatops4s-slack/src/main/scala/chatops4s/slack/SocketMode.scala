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
    val delay = retryDelay.getOrElse(monad.blocking(Thread.sleep(2000)))

    val loop: F[Unit] = for {
      url <- openSocketUrl(appToken, backend)
      _ <- SlackApi.apps.connectToSocket(url, backend) { envelope =>
        handler(envelope).handleError { case e =>
          monad.blocking(logger.error("Handler error", e))
        }
      }
    } yield ()

    loop.handleError { case _ =>
      delay >> runLoop(appToken, backend, handler, Some(delay))
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
