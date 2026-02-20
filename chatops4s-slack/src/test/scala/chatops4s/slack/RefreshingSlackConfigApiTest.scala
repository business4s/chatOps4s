package chatops4s.slack

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import chatops4s.slack.api.{SlackConfigToken, SlackRefreshToken}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.testing.BackendStub

import java.time.{Duration, Instant}

class RefreshingSlackConfigApiTest extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  private given monad: sttp.monad.MonadError[IO] = new sttp.client4.impl.cats.CatsMonadAsyncError[IO]

  private val initialConfig  = SlackConfigToken.unsafe("xoxe.xoxp-initial")
  private val initialRefresh = SlackRefreshToken.unsafe("xoxe-initial")
  private val rotatedConfig  = SlackConfigToken.unsafe("xoxe.xoxp-rotated")
  private val rotatedRefresh = SlackRefreshToken.unsafe("xoxe-rotated")
  private val rotatedExp     = 1700043200
  private val margin         = Duration.ofMinutes(5)

  private val rotateResponseJson =
    s"""{
       |  "ok": true,
       |  "token": "${rotatedConfig.value}",
       |  "refresh_token": "${rotatedRefresh.value}",
       |  "team_id": "T12345",
       |  "user_id": "U12345",
       |  "iat": 1700000000,
       |  "exp": $rotatedExp
       |}""".stripMargin

  private def stubBackend: BackendStub[IO] =
    BackendStub(monad)
      .whenRequestMatches(_.uri.toString().contains("tooling.tokens.rotate"))
      .thenRespondAdjust(rotateResponseJson)
      .whenAnyRequest
      .thenRespondAdjust("""{"ok": true, "errors": []}""")

  private val initialPair = ConfigTokenStore.TokenPair(initialConfig, initialRefresh)

  "RefreshingSlackConfigApi" - {

    "first call triggers rotation (no known expiry)" in {
      (for {
        store      <- ConfigTokenStore.inMemory[IO](initialPair)
        refreshing <- RefreshingSlackConfigApi.createWithClock(stubBackend, store, margin, () => Instant.ofEpochSecond(1700000000L))
        _          <- refreshing.withApi(api => monad.unit(()))
        pair       <- store.get
      } yield pair).asserting { pair =>
        pair.configToken shouldBe rotatedConfig
        pair.refreshToken shouldBe rotatedRefresh
      }
    }

    "fresh token does not trigger rotation" in {
      var rotateCount = 0
      val backend = BackendStub(monad)
        .whenRequestMatches { req =>
          val matches = req.uri.toString().contains("tooling.tokens.rotate")
          if matches then rotateCount += 1
          matches
        }
        .thenRespondAdjust(rotateResponseJson)
        .whenAnyRequest
        .thenRespondAdjust("""{"ok": true, "errors": []}""")

      (for {
        store      <- ConfigTokenStore.inMemory[IO](initialPair)
        // Clock well before expiry (exp=1700043200, margin=5min → threshold=1700042900)
        refreshing <- RefreshingSlackConfigApi.createWithClock(backend, store, margin, () => Instant.ofEpochSecond(1700000100L))
        // First call always rotates (no known exp)
        _          <- refreshing.withApi(api => monad.unit(()))
        // Second call should NOT rotate (token is fresh)
        _          <- refreshing.withApi(api => monad.unit(()))
      } yield rotateCount).asserting { count =>
        count shouldBe 1
      }
    }

    "near-expiry triggers rotation" in {
      var currentTime = Instant.ofEpochSecond(1700000000L)
      var rotateCount = 0
      val backend = BackendStub(monad)
        .whenRequestMatches { req =>
          val matches = req.uri.toString().contains("tooling.tokens.rotate")
          if matches then rotateCount += 1
          matches
        }
        .thenRespondAdjust(rotateResponseJson)
        .whenAnyRequest
        .thenRespondAdjust("""{"ok": true, "errors": []}""")

      (for {
        store      <- ConfigTokenStore.inMemory[IO](initialPair)
        refreshing <- RefreshingSlackConfigApi.createWithClock(backend, store, margin, () => currentTime)
        // First call rotates (no known exp)
        _          <- refreshing.withApi(api => monad.unit(()))
        _          <- IO { currentTime = Instant.ofEpochSecond(rotatedExp - 100L) } // within margin
        // Second call should rotate (near expiry)
        _          <- refreshing.withApi(api => monad.unit(()))
      } yield rotateCount).asserting { count =>
        count shouldBe 2
      }
    }

    "forceRotate always rotates" in {
      var rotateCount = 0
      val backend = BackendStub(monad)
        .whenRequestMatches { req =>
          val matches = req.uri.toString().contains("tooling.tokens.rotate")
          if matches then rotateCount += 1
          matches
        }
        .thenRespondAdjust(rotateResponseJson)

      (for {
        store      <- ConfigTokenStore.inMemory[IO](initialPair)
        refreshing <- RefreshingSlackConfigApi.createWithClock(backend, store, margin, () => Instant.ofEpochSecond(1700000000L))
        _          <- refreshing.forceRotate()
        pair       <- store.get
      } yield (pair, rotateCount)).asserting { case (pair, count) =>
        count shouldBe 1
        pair.configToken shouldBe rotatedConfig
        pair.refreshToken shouldBe rotatedRefresh
      }
    }
  }
}
