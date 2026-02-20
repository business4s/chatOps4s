package chatops4s.slack

import chatops4s.slack.api.{SlackConfigApi, SlackToolingApi}
import sttp.client4.Backend
import sttp.monad.MonadError
import sttp.monad.syntax.*

import java.time.{Duration, Instant}

/** A wrapper around [[SlackConfigApi]] that automatically rotates the config token
  * when it is near expiry.
  *
  * Config tokens (`xoxe.xoxp-`) expire after 12 hours. This class tracks the `exp`
  * claim from the last `tooling.tokens.rotate` response and triggers a rotation when
  * the token is within `refreshMargin` of expiry (default: 5 minutes).
  *
  * On the very first call — when no expiry is known — a rotation is triggered to
  * establish the expiry baseline.
  *
  * Use [[withApi]] to obtain a [[SlackConfigApi]] with a fresh token:
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

  /** Obtain a [[SlackConfigApi]] with a valid (rotated if necessary) token and run `f`. */
  def withApi[A](f: SlackConfigApi[F] => F[A]): F[A] =
    for {
      _    <- rotateIfNeeded
      pair <- store.get
      a    <- f(new SlackConfigApi[F](backend, pair.configToken))
    } yield a

  /** Force an immediate token rotation regardless of expiry. */
  def forceRotate(): F[Unit] = rotate()

  private def rotateIfNeeded: F[Unit] =
    expRef.get.flatMap {
      case None      => rotate()
      case Some(exp) =>
        val now = clock()
        if !now.plus(refreshMargin).isBefore(exp) then rotate()
        else monad.unit(())
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
