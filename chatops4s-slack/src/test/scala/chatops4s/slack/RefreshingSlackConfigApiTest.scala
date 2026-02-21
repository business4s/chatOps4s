package chatops4s.slack

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import chatops4s.slack.api.{SlackConfigApi, SlackConfigToken, SlackOAuth, SlackRefreshToken, apps, oauth}
import chatops4s.slack.api.manifest.{DisplayInformation, SlackAppManifest}
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
        _          <- refreshing.withApi(_ => monad.unit(()))
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
        _          <- refreshing.withApi(_ => monad.unit(()))
        // Second call should NOT rotate (token is fresh)
        _          <- refreshing.withApi(_ => monad.unit(()))
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
        _          <- refreshing.withApi(_ => monad.unit(()))
        _          <- IO { currentTime = Instant.ofEpochSecond(rotatedExp - 100L) } // within margin
        // Second call should rotate (near expiry)
        _          <- refreshing.withApi(_ => monad.unit(()))
      } yield rotateCount).asserting { count =>
        count shouldBe 2
      }
    }

    "rotation sends refresh token as form-urlencoded body" in {
      var capturedAuthHeader: Option[String] = None
      var capturedContentType: Option[String] = None
      var capturedBody: Option[String] = None
      val backend: BackendStub[IO] = BackendStub(monad)
        .whenRequestMatches { req =>
          val matches = req.uri.toString().contains("tooling.tokens.rotate")
          if matches then {
            capturedAuthHeader = req.header("Authorization")
            capturedContentType = req.contentType
            capturedBody = req.body match {
              case sttp.client4.StringBody(s, _, _) => Some(s)
              case _                                => None
            }
          }
          matches
        }
        .thenRespondAdjust(rotateResponseJson)

      (for {
        store      <- ConfigTokenStore.inMemory[IO](initialPair)
        refreshing <- RefreshingSlackConfigApi.createWithClock(backend, store, margin, () => Instant.ofEpochSecond(1700000000L))
        _          <- refreshing.forceRotate()
      } yield (capturedAuthHeader, capturedContentType, capturedBody)).asserting { case (authHeader, contentType, body) =>
        authHeader shouldBe Some(s"Bearer ${initialRefresh.value}")
        contentType shouldBe Some("application/x-www-form-urlencoded")
        body.get should include("refresh_token=" + java.net.URLEncoder.encode(initialRefresh.value, "UTF-8"))
      }
    }

    "SlackConfigApi sends manifest as a JSON string, not a nested object" in {
      var capturedBody: Option[String] = None
      val backend: BackendStub[IO] = BackendStub(monad)
        .whenRequestMatches { req =>
          val matches = req.uri.toString().contains("apps.manifest.create")
          if matches then {
            capturedBody = req.body match {
              case sttp.client4.StringBody(s, _, _) => Some(s)
              case _                                => None
            }
          }
          matches
        }
        .thenRespondAdjust("""{"ok": true, "app_id": "A123", "credentials": {"client_id": "c", "client_secret": "s", "verification_token": "v", "signing_secret": "ss"}}""")

      val manifest = SlackAppManifest(display_information = DisplayInformation(name = "TestApp"))
      val api      = new SlackConfigApi[IO](backend, initialConfig)

      api.apps.manifest.create(apps.manifest.CreateRequest(manifest = manifest)).asserting { _ =>
        val json = io.circe.parser.parse(capturedBody.get).toOption.get
        val manifestField = json.hcursor.get[io.circe.Json]("manifest").toOption.get
        // must be a JSON string (stringified manifest), not a nested JSON object
        manifestField.isString shouldBe true
        // the string content should be valid JSON containing the app name
        val inner = io.circe.parser.parse(manifestField.asString.get).toOption.get
        inner.hcursor.downField("display_information").get[String]("name").toOption shouldBe Some("TestApp")
      }
    }

    "SlackOAuth sends Basic auth and form-urlencoded body" in {
      var capturedAuthHeader: Option[String] = None
      var capturedContentType: Option[String] = None
      var capturedBody: Option[String] = None
      val backend: BackendStub[IO] = BackendStub(monad)
        .whenRequestMatches { req =>
          val matches = req.uri.toString().contains("oauth.v2.access")
          if matches then {
            capturedAuthHeader = req.header("Authorization")
            capturedContentType = req.contentType
            capturedBody = req.body match {
              case sttp.client4.StringBody(s, _, _) => Some(s)
              case _                                => None
            }
          }
          matches
        }
        .thenRespondAdjust("""{"ok": true, "access_token": "xoxb-test", "token_type": "bot"}""")

      val req = oauth.AccessRequest(
        code = "test-code",
        client_id = "123.456",
        client_secret = "secret",
        redirect_uri = Some("http://localhost:8080/callback"),
      )
      SlackOAuth.exchangeCode(backend, req).asserting { _ =>
        capturedAuthHeader shouldBe defined
        capturedAuthHeader.get should startWith("Basic ")
        val decoded = new String(java.util.Base64.getDecoder.decode(capturedAuthHeader.get.stripPrefix("Basic ")))
        decoded shouldBe "123.456:secret"
        capturedContentType shouldBe Some("application/x-www-form-urlencoded")
        capturedBody.get should include("code=test-code")
        capturedBody.get should include("redirect_uri=")
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
