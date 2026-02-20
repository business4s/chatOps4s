package example.docs

import cats.effect.IO
import chatops4s.slack.api.{SlackApi, SlackBotToken, SlackConfigApi, SlackConfigToken, SlackRefreshToken, apps, chat, reactions, users, UserId, ChannelId, Timestamp}
import chatops4s.slack.api.manifest.SlackAppManifest
import chatops4s.slack.{ConfigTokenStore, RefreshingSlackConfigApi}
import sttp.client4.Backend

private object RawClientPage {

  def rawClientUsage(backend: Backend[IO], botToken: SlackBotToken): Unit = {
    // start_raw_client_usage
    val api = new SlackApi[IO](backend, botToken)

    // Send a message
    api.chat.postMessage(chat.PostMessageRequest(
      channel = "#general",
      text = "Hello from the raw client",
    ))

    // Add a reaction
    api.reactions.add(reactions.AddRequest(
      channel = ChannelId("C1234567890"),
      timestamp = Timestamp("1234567890.123456"),
      name = "thumbsup",
    ))

    // Look up a user
    api.users.info(users.InfoRequest(user = UserId("U1234567890")))
    // end_raw_client_usage
  }

  def manifestAutomation(
      backend: Backend[IO],
      configToken: SlackConfigToken,
      manifest: SlackAppManifest,
      existingAppId: Option[String],
  ): Unit = {
    // start_manifest_automation
    val configApi = new SlackConfigApi[IO](backend, configToken)

    configApi.apps.manifest.validate(apps.manifest.ValidateRequest(manifest = manifest))

    existingAppId match {
      case None        =>
        configApi.apps.manifest.create(apps.manifest.CreateRequest(manifest = manifest))
      case Some(appId) =>
        configApi.apps.manifest.update(apps.manifest.UpdateRequest(app_id = appId, manifest = manifest))
    }
    // end_manifest_automation
  }

  def tokenRotation(
      backend: Backend[IO],
      configToken: SlackConfigToken,
      refreshToken: SlackRefreshToken,
      manifest: SlackAppManifest,
  ): IO[Unit] = {
    given sttp.monad.MonadError[IO] = new sttp.client4.impl.cats.CatsMonadAsyncError[IO]

    // start_token_rotation
    val initial = ConfigTokenStore.TokenPair(configToken, refreshToken)

    for {
      store      <- ConfigTokenStore.inMemory[IO](initial)
      refreshing <- RefreshingSlackConfigApi.create[IO](backend, store)
      result     <- refreshing.withApi { api =>
                      api.apps.manifest.validate(apps.manifest.ValidateRequest(manifest = manifest))
                    }
    } yield ()
    // end_token_rotation
  }
}
