package chatops4s.slack

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import chatops4s.slack.api.{SlackConfigToken, SlackRefreshToken}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

class ConfigTokenStoreTest extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  private given sttp.monad.MonadError[IO] = new sttp.client4.impl.cats.CatsMonadAsyncError[IO]

  private val pair1 = ConfigTokenStore.TokenPair(
    SlackConfigToken.unsafe("xoxe.xoxp-token-1"),
    SlackRefreshToken.unsafe("xoxe-refresh-1"),
  )

  private val pair2 = ConfigTokenStore.TokenPair(
    SlackConfigToken.unsafe("xoxe.xoxp-token-2"),
    SlackRefreshToken.unsafe("xoxe-refresh-2"),
  )

  "ConfigTokenStore.inMemory" - {

    "get returns the initial pair" in {
      ConfigTokenStore.inMemory[IO](pair1).flatMap(_.get).asserting { result =>
        result shouldBe pair1
      }
    }

    "set then get returns the updated pair" in {
      (for {
        store  <- ConfigTokenStore.inMemory[IO](pair1)
        _      <- store.set(pair2)
        result <- store.get
      } yield result).asserting { result =>
        result shouldBe pair2
      }
    }
  }
}
