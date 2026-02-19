package chatops4s.slack.manifest

import io.circe.{Encoder, Printer}
import io.circe.syntax.*

/** Slack app manifest.
  * @see [[https://docs.slack.dev/reference/app-manifest#fields Slack docs]]
  * @see [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java Java SDK]]
  */
case class SlackAppManifest(
    _metadata: ManifestMetadata = ManifestMetadata(),
    display_information: DisplayInformation,
    features: Features = Features(),
    oauth_config: OauthConfig = OauthConfig(),
    settings: ManifestSettings = ManifestSettings(),
    outgoing_domains: Option[List[String]] = None,
) derives Encoder.AsObject {

  private val printer = Printer.spaces2.copy(dropNullValues = true)
  def renderJson: String = printer.print(this.asJson)

  def addBotScopes(scopes: String*): SlackAppManifest = {
    val current = oauth_config.scopes.getOrElse(OauthScopes()).bot.getOrElse(Nil)
    copy(oauth_config = oauth_config.copy(scopes = Some(oauth_config.scopes.getOrElse(OauthScopes()).copy(bot = Some(current ++ scopes)))))
  }

  def addUserScopes(scopes: String*): SlackAppManifest = {
    val current = oauth_config.scopes.getOrElse(OauthScopes()).user.getOrElse(Nil)
    copy(oauth_config = oauth_config.copy(scopes = Some(oauth_config.scopes.getOrElse(OauthScopes()).copy(user = Some(current ++ scopes)))))
  }

  def addBotEvents(events: String*): SlackAppManifest = {
    val current = settings.event_subscriptions.getOrElse(EventSubscriptions()).bot_events.getOrElse(Nil)
    copy(settings = settings.copy(event_subscriptions = Some(settings.event_subscriptions.getOrElse(EventSubscriptions()).copy(bot_events = Some(current ++ events)))))
  }

  def addOutgoingDomains(domains: String*): SlackAppManifest = {
    val current = outgoing_domains.getOrElse(Nil)
    copy(outgoing_domains = Some(current ++ domains))
  }
}

/** @see [[https://docs.slack.dev/reference/app-manifest#metadata Slack docs]]
  * @see [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Metadata]]
  */
case class ManifestMetadata(
    major_version: Option[Int] = Some(1),
    minor_version: Option[Int] = Some(1),
) derives Encoder.AsObject

/** @see [[https://docs.slack.dev/reference/app-manifest#display Slack docs]]
  * @see [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.DisplayInformation]]
  */
case class DisplayInformation(
    name: String,
    description: Option[String] = None,
    long_description: Option[String] = None,
    background_color: Option[String] = None,
) derives Encoder.AsObject

/** @see [[https://docs.slack.dev/reference/app-manifest#features Slack docs]]
  * @see [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Features]]
  */
case class Features(
    app_home: Option[AppHome] = None,
    bot_user: Option[BotUser] = None,
    shortcuts: Option[List[Shortcut]] = None,
    slash_commands: Option[List[SlashCommand]] = None,
    unfurl_domains: Option[List[String]] = None,
    workflow_steps: Option[List[WorkflowStep]] = None,
) derives Encoder.AsObject

/** @see [[https://docs.slack.dev/reference/app-manifest#features Slack docs]]
  * @see [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Features.AppHome]]
  */
case class AppHome(
    home_tab_enabled: Option[Boolean] = None,
    messages_tab_enabled: Option[Boolean] = None,
    messages_tab_read_only_enabled: Option[Boolean] = None,
) derives Encoder.AsObject

/** @see [[https://docs.slack.dev/reference/app-manifest#features Slack docs]]
  * @see [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Features.BotUser]]
  */
case class BotUser(
    display_name: String,
    always_online: Option[Boolean] = None,
) derives Encoder.AsObject

/** @see [[https://docs.slack.dev/reference/app-manifest#features Slack docs]]
  * @see [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Features.Shortcut]]
  */
case class Shortcut(
    name: String,
    callback_id: String,
    description: String,
    `type`: String,
) derives Encoder.AsObject

/** @see [[https://docs.slack.dev/reference/app-manifest#features Slack docs]]
  * @see [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Features.SlashCommand]]
  */
case class SlashCommand(
    command: String,
    description: String,
    should_escape: Option[Boolean] = None,
    url: Option[String] = None,
    usage_hint: Option[String] = None,
) derives Encoder.AsObject

/** @see [[https://docs.slack.dev/reference/app-manifest#features Slack docs]] */
case class WorkflowStep(
    name: String,
    callback_id: String,
) derives Encoder.AsObject

/** @see [[https://docs.slack.dev/reference/app-manifest#oauth Slack docs]]
  * @see [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.OAuthConfig]]
  */
case class OauthConfig(
    scopes: Option[OauthScopes] = None,
    redirect_urls: Option[List[String]] = None,
    token_management_enabled: Option[Boolean] = None,
) derives Encoder.AsObject

/** @see [[https://docs.slack.dev/reference/app-manifest#oauth Slack docs]]
  * @see [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.OAuthConfig.Scopes]]
  */
case class OauthScopes(
    bot: Option[List[String]] = None,
    user: Option[List[String]] = None,
) derives Encoder.AsObject

/** @see [[https://docs.slack.dev/reference/app-manifest#settings Slack docs]]
  * @see [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Settings]]
  */
case class ManifestSettings(
    allowed_ip_address_ranges: Option[List[String]] = None,
    event_subscriptions: Option[EventSubscriptions] = None,
    incoming_webhooks: Option[IncomingWebhooks] = None,
    interactivity: Option[Interactivity] = None,
    org_deploy_enabled: Option[Boolean] = None,
    socket_mode_enabled: Option[Boolean] = None,
    token_rotation_enabled: Option[Boolean] = None,
    function_runtime: Option[String] = None,
    siws_links: Option[SiwsLinks] = None,
) derives Encoder.AsObject

/** @see [[https://docs.slack.dev/reference/app-manifest#settings Slack docs]]
  * @see [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Settings.EventSubscriptions]]
  */
case class EventSubscriptions(
    request_url: Option[String] = None,
    bot_events: Option[List[String]] = None,
    user_events: Option[List[String]] = None,
) derives Encoder.AsObject

/** @see [[https://docs.slack.dev/reference/app-manifest#settings Slack docs]] */
case class IncomingWebhooks(
    incoming_webhooks_enabled: Option[Boolean] = None,
) derives Encoder.AsObject

/** @see [[https://docs.slack.dev/reference/app-manifest#settings Slack docs]]
  * @see [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Settings.Interactivity]]
  */
case class Interactivity(
    is_enabled: Option[Boolean] = None,
    request_url: Option[String] = None,
    message_menu_options_url: Option[String] = None,
) derives Encoder.AsObject

/** @see [[https://docs.slack.dev/reference/app-manifest#settings Slack docs]] */
case class SiwsLinks(
    initiate_uri: Option[String] = None,
) derives Encoder.AsObject
