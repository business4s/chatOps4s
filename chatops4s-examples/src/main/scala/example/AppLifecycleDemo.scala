package example

import cats.effect.{IO, IOApp}
import chatops4s.slack.api.*
import chatops4s.slack.api.manifest.{DisplayInformation, OauthConfig, SlackAppManifest}
import chatops4s.slack.{ConfigTokenStore, RefreshingSlackConfigApi}
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

import java.net.{InetSocketAddress, URI}
import java.util.concurrent.atomic.AtomicReference

/** Self-contained demo of the full Slack app lifecycle:
  * create app → OAuth install → send/update messages → delete app.
  *
  * Required env vars:
  *   - SLACK_CONFIG_TOKEN  — a config token (xoxe.xoxp-...)
  *   - SLACK_REFRESH_TOKEN — the matching refresh token (xoxe-...)
  *   - SLACK_CHANNEL       — channel to post to (e.g. #test)
  */
object AppLifecycleDemo extends IOApp.Simple {

  private lazy val configToken  = SlackConfigToken.unsafe(sys.env("SLACK_CONFIG_TOKEN"))
  private lazy val refreshToken = SlackRefreshToken.unsafe(sys.env("SLACK_REFRESH_TOKEN"))
  private lazy val channel      = sys.env("SLACK_CHANNEL")

  private val appName = "chatops4s-lifecycle-demo"

  private def manifest(name: String) = SlackAppManifest(
    display_information = DisplayInformation(
      name = name,
      description = Some("Temporary app for chatops4s lifecycle demo"),
    ),
    oauth_config = OauthConfig(
      redirect_urls = Some(List("http://localhost:8080/callback")),
    ),
  ).addBotScopes("chat:write", "chat:write.public")

  override def run: IO[Unit] =
    HttpClientFs2Backend.resource[IO]().use { backend =>
      given sttp.monad.MonadError[IO] = backend.monad
      val initial = ConfigTokenStore.TokenPair(configToken, refreshToken)

      for {
        store      <- ConfigTokenStore.inMemory[IO](initial)
        refreshing <- RefreshingSlackConfigApi.create[IO](backend, store)

        // 1. Create app
        _          <- IO.println("Creating app...")
        createResp <- refreshing.withApi { api =>
                        api.apps.manifest.create(apps.manifest.CreateRequest(manifest = manifest(appName)))
                      }
        created     = createResp.okOrThrow
        appId       = created.app_id
        creds       = created.credentials.getOrElse(throw new RuntimeException("No credentials in create response"))
        oauthUrl    = created.oauth_authorize_url.getOrElse(throw new RuntimeException("No oauth_authorize_url in create response"))
        _          <- IO.println(s"App created: $appId")
        _          <- refreshing.forceRotate()

        // 2. Start local HTTP server for OAuth callback
        code       <- captureOAuthCode(oauthUrl)
        _          <- IO.println("OAuth code received, exchanging for token...")

        // 3. Exchange code for bot token
        oauthResp  <- SlackOAuth.exchangeCode(
                        backend,
                        oauth.AccessRequest(
                          code = code,
                          client_id = creds.client_id,
                          client_secret = creds.client_secret,
                          redirect_uri = Some("http://localhost:8080/callback"),
                        ),
                      )
        accessResp  = oauthResp.okOrThrow
        botToken    = SlackBotToken.unsafe(accessResp.access_token)
        _          <- IO.println(s"Bot token obtained for team: ${accessResp.team.flatMap(_.name).getOrElse("unknown")}")

        // 4. Send initial message
        slackApi    = new SlackApi[IO](backend, botToken)
        postResp   <- slackApi.chat.postMessage(
                        chat.PostMessageRequest(channel = channel, text = "App created, running lifecycle demo..."),
                      )
        posted      = postResp.okOrThrow
        msgTs       = posted.ts
        msgChannel  = posted.channel
        _          <- IO.println(s"Message posted: ${msgTs.value}")

        // 5. Update manifest — rename app
        _          <- IO.println("Updating app manifest...")
        _          <- refreshing.withApi { api =>
                        api.apps.manifest.update(
                          apps.manifest.UpdateRequest(app_id = appId, manifest = manifest(s"$appName (updated)")),
                        )
                      }
        _          <- IO.println("Manifest updated.")

        // 6. Update the message
        _          <- slackApi.chat.update(
                        chat.UpdateRequest(channel = msgChannel, ts = msgTs, text = Some("App manifest updated.")),
                      )
        _          <- IO.println("Message updated.")

        // 7. Send farewell message
        _          <- slackApi.chat.postMessage(
                        chat.PostMessageRequest(channel = channel, text = "Lifecycle demo complete, deleting app..."),
                      )

        // 8. Delete app
        _          <- IO.println("Deleting app...")
        _          <- refreshing.withApi { api =>
                        api.apps.manifest.delete(apps.manifest.DeleteRequest(app_id = appId))
                      }
        _          <- IO.println("App deleted. Demo complete.")
      } yield ()
    }

  /** Start a local HTTP server, open the OAuth URL in a browser, and wait for the callback with the authorization code. */
  private def captureOAuthCode(oauthUrl: String): IO[String] = IO.async_ { cb =>
    val server  = HttpServer.create(new InetSocketAddress(8080), 0)
    val codeRef = new AtomicReference[String](null)

    server.createContext(
      "/callback",
      (exchange: HttpExchange) => {
        val query  = exchange.getRequestURI.getQuery
        val params = query.split("&").map(_.split("=", 2)).collect { case Array(k, v) => k -> v }.toMap

        params.get("code") match {
          case Some(code) =>
            val response = "Authorization received! You can close this tab."
            exchange.sendResponseHeaders(200, response.length)
            exchange.getResponseBody.write(response.getBytes)
            exchange.getResponseBody.close()
            codeRef.set(code)
            server.stop(0)
            cb(Right(code))

          case None =>
            val error    = params.getOrElse("error", "unknown")
            val response = s"OAuth error: $error"
            exchange.sendResponseHeaders(400, response.length)
            exchange.getResponseBody.write(response.getBytes)
            exchange.getResponseBody.close()
            server.stop(0)
            cb(Left(new RuntimeException(s"OAuth callback error: $error")))
        }
      },
    )

    server.start()
    println(s"Local server listening on http://localhost:8080/callback")
    println(s"Opening browser for OAuth authorization...")

    try java.awt.Desktop.getDesktop.browse(new URI(oauthUrl))
    catch {
      case _: Exception =>
        println(s"Could not open browser automatically. Please visit:\n$oauthUrl")
    }
  }
}
