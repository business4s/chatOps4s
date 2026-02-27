package chatops4s.slack

import chatops4s.slack.api.{SlackConfigApi, SlackToolingApi}
import sttp.client4.Backend
import sttp.monad.MonadError
import sttp.monad.syntax.*

import java.time.{Duration, Instant}
import java.util.concurrent.atomic.AtomicBoolean

/** Automatically rotates the config token when it is near expiry.
  * {{{
  * refreshing.withApi { api =>
  *   api.apps.manifest.validate(...)
  * }
  * }}}
  */
class RefreshingSlackConfigApi[F[_]] private (
    backend: Backend[F],
    store: ConfigTokenStore[F],
    refreshMargin: Duration,
    expRef: Ref[F, Option[Instant]],
    clock: () => Instant,
)(using monad: MonadError[F]) {

  private val rotating = new AtomicBoolean(false)

  /** Concurrent callers skip the rotation and proceed with the current (possibly stale) token. */
  def withApi[A](f: SlackConfigApi[F] => F[A]): F[A] =
    for {
      _    <- rotateIfNeeded
      pair <- store.get
      a    <- f(new SlackConfigApi[F](backend, pair.configToken))
    } yield a

  def forceRotate(): F[Unit] = guardedRotate()

  private def rotateIfNeeded: F[Unit] =
    expRef.get.flatMap {
      case None      => guardedRotate()
      case Some(exp) =>
        val now = clock()
        if !now.plus(refreshMargin).isBefore(exp) then guardedRotate()
        else monad.unit(())
    }

  private def guardedRotate(): F[Unit] =
    monad.eval(rotating.compareAndSet(false, true)).flatMap {
      case false => monad.unit(()) // Another call is already rotating, skip
      case true  =>
        rotate()
          .flatMap(_ => monad.eval(rotating.set(false)))
          .handleError { case e =>
            monad.eval(rotating.set(false)).flatMap(_ => monad.error(e))
          }
    }

  private def rotate(): F[Unit] =
    for {
      pair <- store.get
      resp <- new SlackToolingApi[F](backend, pair.refreshToken).tooling.tokens.rotate()
      r     = resp.okOrThrow
      _    <- store.set(ConfigTokenStore.TokenPair(r.token, r.refresh_token))
      _    <- expRef.update(_ => r.exp.map(e => Instant.ofEpochSecond(e.toLong)))
    } yield ()
}

object RefreshingSlackConfigApi {

  def create[F[_]](
      backend: Backend[F],
      store: ConfigTokenStore[F],
      refreshMargin: Duration = Duration.ofMinutes(5),
  )(using monad: MonadError[F]): F[RefreshingSlackConfigApi[F]] =
    createWithClock(backend, store, refreshMargin, () => Instant.now())

  private[slack] def createWithClock[F[_]](
      backend: Backend[F],
      store: ConfigTokenStore[F],
      refreshMargin: Duration,
      clock: () => Instant,
  )(using monad: MonadError[F]): F[RefreshingSlackConfigApi[F]] =
    for {
      expRef <- Ref.of[F, Option[Instant]](None)
    } yield new RefreshingSlackConfigApi[F](backend, store, refreshMargin, expRef, clock)
}
