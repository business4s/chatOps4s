package example.docs

import cats.effect.IO
import chatops4s.slack.api.{ChannelId, SlackApi, SlackBotToken, SlackConfigApi, SlackConfigToken, Timestamp, UserId, apps, chat, reactions, users}
import chatops4s.slack.api.manifest.SlackAppManifest
import sttp.client4.Backend

private object RawClientPage {

  def rawClientUsage(backend: Backend[IO], botToken: SlackBotToken): Unit = {
    // start_raw_client_usage
    val api = new SlackApi[IO](backend, botToken)

    // Send a message
    api.chat.postMessage(
      chat.PostMessageRequest(
        channel = "#general",
        text = "Hello from the raw client",
      ),
    ): Unit

    // Add a reaction
    api.reactions.add(
      reactions.AddRequest(
        channel = ChannelId("C1234567890"),
        timestamp = Timestamp("1234567890.123456"),
        name = "thumbsup",
      ),
    ): Unit

    // Look up a user
    api.users.info(users.InfoRequest(user = UserId("U1234567890"))): Unit
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

    configApi.apps.manifest.validate(apps.manifest.ValidateRequest(manifest = manifest)): Unit

    existingAppId match {
      case None        =>
        configApi.apps.manifest.create(apps.manifest.CreateRequest(manifest = manifest)): Unit
      case Some(appId) =>
        configApi.apps.manifest.update(apps.manifest.UpdateRequest(app_id = appId, manifest = manifest)): Unit
    }
    // end_manifest_automation
  }

}
