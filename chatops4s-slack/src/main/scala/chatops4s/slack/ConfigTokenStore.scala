package chatops4s.slack

import chatops4s.slack.api.{SlackConfigToken, SlackRefreshToken}
import sttp.monad.MonadError
import sttp.monad.syntax.*

/** Storage for the current config-token / refresh-token pair.
  *
  * == HA limitations ==
  *
  * Refresh tokens are '''single-use''': each call to `tooling.tokens.rotate` invalidates the
  * previous refresh token. The in-memory implementation is therefore single-process only.
  *
  * For high-availability deployments, implement this trait with atomic compare-and-swap
  * semantics (e.g. Redis `WATCH`/`MULTI` or a database row with a version column).
  * When two processes race to rotate, the loser's refresh token will already be invalid;
  * it should re-read the store to pick up the winner's tokens.
  */
trait ConfigTokenStore[F[_]] {
  def get: F[ConfigTokenStore.TokenPair]
  def set(pair: ConfigTokenStore.TokenPair): F[Unit]
}

object ConfigTokenStore {

  case class TokenPair(configToken: SlackConfigToken, refreshToken: SlackRefreshToken)

  def inMemory[F[_]](initial: TokenPair)(using monad: MonadError[F]): F[ConfigTokenStore[F]] =
    Ref.of[F, TokenPair](initial).map { ref =>
      new ConfigTokenStore[F] {
        def get: F[TokenPair] = ref.get
        def set(pair: TokenPair): F[Unit] = ref.update(_ => pair)
      }
    }
}
