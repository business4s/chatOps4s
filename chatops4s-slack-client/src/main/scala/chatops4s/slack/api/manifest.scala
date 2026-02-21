package chatops4s.slack.api

package manifest {

  import io.circe.{Codec, Encoder, Json, Printer}
  import io.circe.syntax.*

  /** @see
    *   [[https://docs.slack.dev/reference/app-manifest#fields Slack docs]]
    * @see
    *   [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java Java SDK]]
    */
  case class SlackAppManifest(
      _metadata: ManifestMetadata = ManifestMetadata(),
      display_information: DisplayInformation,
      features: Features = Features(),
      oauth_config: OauthConfig = OauthConfig(),
      settings: ManifestSettings = ManifestSettings(),
      outgoing_domains: Option[List[String]] = None,
      functions: Option[Map[String, ManifestFunction]] = None,
      workflows: Option[Map[String, ManifestWorkflow]] = None,
      datastores: Option[Map[String, Datastore]] = None,
      types: Option[Map[String, CustomType]] = None,
      metadata_events: Option[List[MetadataEvent]] = None,
      external_auth_providers: Option[List[ExternalAuthProvider]] = None,
      compliance: Option[Compliance] = None,
      app_directory: Option[AppDirectory] = None,
  ) derives Codec.AsObject {

    private val printer    = Printer.spaces2.copy(dropNullValues = true)
    def renderJson: String = printer.print(this.asJson)

    def addBotScopes(scopes: String*): SlackAppManifest = {
      val current     = oauth_config.scopes.getOrElse(OauthScopes()).bot.getOrElse(Nil)
      val withScopes  =
        copy(oauth_config = oauth_config.copy(scopes = Some(oauth_config.scopes.getOrElse(OauthScopes()).copy(bot = Some(current ++ scopes)))))
      val withBotUser =
        if withScopes.features.bot_user.isEmpty then withScopes.copy(features =
          withScopes.features.copy(bot_user = Some(BotUser(display_name = display_information.name))),
        )
        else withScopes
      withBotUser
    }

    def addUserScopes(scopes: String*): SlackAppManifest = {
      val current = oauth_config.scopes.getOrElse(OauthScopes()).user.getOrElse(Nil)
      copy(oauth_config = oauth_config.copy(scopes = Some(oauth_config.scopes.getOrElse(OauthScopes()).copy(user = Some(current ++ scopes)))))
    }

    def addBotEvents(events: String*): SlackAppManifest = {
      val current = settings.event_subscriptions.getOrElse(EventSubscriptions()).bot_events.getOrElse(Nil)
      copy(settings =
        settings.copy(event_subscriptions =
          Some(settings.event_subscriptions.getOrElse(EventSubscriptions()).copy(bot_events = Some(current ++ events))),
        ),
      )
    }

    def addOutgoingDomains(domains: String*): SlackAppManifest = {
      val current = outgoing_domains.getOrElse(Nil)
      copy(outgoing_domains = Some(current ++ domains))
    }
  }

  /** @see
    *   [[https://docs.slack.dev/reference/app-manifest#metadata Slack docs]]
    * @see
    *   [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Metadata]]
    */
  case class ManifestMetadata(
      major_version: Option[Int] = Some(1),
      minor_version: Option[Int] = Some(1),
  ) derives Codec.AsObject

  /** @see
    *   [[https://docs.slack.dev/reference/app-manifest#display Slack docs]]
    * @see
    *   [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.DisplayInformation]]
    */
  case class DisplayInformation(
      name: String,
      description: Option[String] = None,
      long_description: Option[String] = None,
      background_color: Option[String] = None,
  ) derives Codec.AsObject

  /** @see
    *   [[https://docs.slack.dev/reference/app-manifest#features Slack docs]]
    * @see
    *   [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Features]]
    */
  case class Features(
      app_home: Option[AppHome] = None,
      bot_user: Option[BotUser] = None,
      shortcuts: Option[List[Shortcut]] = None,
      slash_commands: Option[List[SlashCommand]] = None,
      unfurl_domains: Option[List[String]] = None,
      workflow_steps: Option[List[WorkflowStep]] = None,
      assistant_view: Option[AssistantView] = None,
      rich_previews: Option[RichPreviews] = None,
  ) derives Codec.AsObject

  /** @see
    *   [[https://docs.slack.dev/reference/app-manifest#features Slack docs]]
    * @see
    *   [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Features.AppHome]]
    */
  case class AppHome(
      home_tab_enabled: Option[Boolean] = None,
      messages_tab_enabled: Option[Boolean] = None,
      messages_tab_read_only_enabled: Option[Boolean] = None,
  ) derives Codec.AsObject

  /** @see
    *   [[https://docs.slack.dev/reference/app-manifest#features Slack docs]]
    * @see
    *   [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Features.BotUser]]
    */
  case class BotUser(
      display_name: String,
      always_online: Option[Boolean] = None,
  ) derives Codec.AsObject

  /** @see
    *   [[https://docs.slack.dev/reference/app-manifest#features Slack docs]]
    * @see
    *   [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Features.Shortcut]]
    */
  case class Shortcut(
      name: String,
      callback_id: String,
      description: String,
      `type`: String,
  ) derives Codec.AsObject

  /** @see
    *   [[https://docs.slack.dev/reference/app-manifest#features Slack docs]]
    * @see
    *   [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Features.SlashCommand]]
    */
  case class SlashCommand(
      command: String,
      description: String,
      should_escape: Option[Boolean] = None,
      url: Option[String] = None,
      usage_hint: Option[String] = None,
  ) derives Codec.AsObject

  /** @see [[https://docs.slack.dev/reference/app-manifest#features Slack docs]] */
  case class WorkflowStep(
      name: String,
      callback_id: String,
  ) derives Codec.AsObject

  /** @see
    *   [[https://docs.slack.dev/reference/app-manifest#oauth Slack docs]]
    * @see
    *   [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.OAuthConfig]]
    */
  case class OauthConfig(
      scopes: Option[OauthScopes] = None,
      redirect_urls: Option[List[String]] = None,
      token_management_enabled: Option[Boolean] = None,
  ) derives Codec.AsObject

  /** @see
    *   [[https://docs.slack.dev/reference/app-manifest#oauth Slack docs]]
    * @see
    *   [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.OAuthConfig.Scopes]]
    */
  case class OauthScopes(
      bot: Option[List[String]] = None,
      user: Option[List[String]] = None,
  ) derives Codec.AsObject

  /** @see
    *   [[https://docs.slack.dev/reference/app-manifest#settings Slack docs]]
    * @see
    *   [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Settings]]
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
      is_hosted: Option[Boolean] = None,
  ) derives Codec.AsObject

  /** @see
    *   [[https://docs.slack.dev/reference/app-manifest#settings Slack docs]]
    * @see
    *   [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Settings.EventSubscriptions]]
    */
  case class EventSubscriptions(
      request_url: Option[String] = None,
      bot_events: Option[List[String]] = None,
      user_events: Option[List[String]] = None,
      metadata_subscriptions: Option[List[MetadataSubscription]] = None,
  ) derives Codec.AsObject

  /** @see [[https://docs.slack.dev/reference/app-manifest#settings Slack docs]] */
  case class IncomingWebhooks(
      incoming_webhooks_enabled: Option[Boolean] = None,
  ) derives Codec.AsObject

  /** @see
    *   [[https://docs.slack.dev/reference/app-manifest#settings Slack docs]]
    * @see
    *   [[https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/manifest/AppManifest.java AppManifest.Settings.Interactivity]]
    */
  case class Interactivity(
      is_enabled: Option[Boolean] = None,
      request_url: Option[String] = None,
      message_menu_options_url: Option[String] = None,
  ) derives Codec.AsObject

  /** @see [[https://docs.slack.dev/reference/app-manifest#settings Slack docs]] */
  case class SiwsLinks(
      initiate_uri: Option[String] = None,
  ) derives Codec.AsObject

  // --- Features nested types ---

  case class AssistantView(
      assistant_description: Option[String] = None,
      suggested_prompts: Option[List[SuggestedPrompt]] = None,
  ) derives Codec.AsObject

  case class SuggestedPrompt(
      title: String,
      message: String,
  ) derives Codec.AsObject

  case class RichPreviews(
      is_active: Option[Boolean] = None,
      entity_types: Option[List[String]] = None,
  ) derives Codec.AsObject

  // --- Event subscriptions nested types ---

  case class MetadataSubscription(
      app_id: String,
      event_type: String,
  ) derives Codec.AsObject

  // --- Functions ---

  case class ManifestFunction(
      title: String,
      description: String,
      input_parameters: ParameterSet,
      output_parameters: ParameterSet,
  ) derives Codec.AsObject

  case class ParameterSet(
      properties: Map[String, ParameterProperty],
      required: Option[List[String]] = None,
  ) derives Codec.AsObject

  case class ParameterProperty(
      `type`: String,
      title: Option[String] = None,
      description: Option[String] = None,
      hint: Option[String] = None,
      name: Option[String] = None,
      is_required: Option[Boolean] = None,
      is_hidden: Option[Boolean] = None,
      default: Option[Json] = None,
      examples: Option[List[Json]] = None,
      nullable: Option[Boolean] = None,
      additionalProperties: Option[Boolean] = None,
      properties: Option[Map[String, ParameterProperty]] = None,
      choices: Option[List[ParameterChoice]] = None,
      items: Option[ParameterProperty] = None,
  ) derives Codec.AsObject

  case class ParameterChoice(
      value: Json,
      title: String,
      description: Option[String] = None,
      is_hidden: Option[Boolean] = None,
      hint: Option[String] = None,
  ) derives Codec.AsObject

  // --- Workflows ---

  case class ManifestWorkflow(
      title: String,
      description: String,
      input_parameters: Option[ParameterSet] = None,
      output_parameters: Option[ParameterSet] = None,
      steps: List[WorkflowStepDef],
      suggested_triggers: Option[List[SuggestedTrigger]] = None,
  ) derives Codec.AsObject

  case class WorkflowStepDef(
      id: String,
      function_id: String,
      inputs: Json,
      `type`: Option[String] = None,
  ) derives Codec.AsObject

  case class SuggestedTrigger(
      name: String,
      description: String,
      `type`: String,
      inputs: Json,
  ) derives Codec.AsObject

  // --- Datastores ---

  case class Datastore(
      primary_key: String,
      attributes: Map[String, DatastoreAttribute],
      time_to_live_attribute: Option[String] = None,
  ) derives Codec.AsObject

  case class DatastoreAttribute(
      `type`: String,
      items: Option[Json] = None,
      properties: Option[Json] = None,
  ) derives Codec.AsObject

  // --- Custom types ---

  case class CustomType(
      `type`: String,
      title: Option[String] = None,
      description: Option[String] = None,
      is_required: Option[Boolean] = None,
      is_hidden: Option[Boolean] = None,
      hint: Option[String] = None,
      properties: Option[Map[String, ParameterProperty]] = None,
  ) derives Codec.AsObject

  // --- Metadata events ---

  case class MetadataEvent(
      `type`: String,
      title: Option[String] = None,
      description: Option[String] = None,
      properties: Option[Map[String, ParameterProperty]] = None,
      required: Option[List[String]] = None,
  ) derives Codec.AsObject

  // --- External auth providers ---

  case class ExternalAuthProvider(
      provider_type: String,
      options: ExternalAuthOptions,
  ) derives Codec.AsObject

  case class ExternalAuthOptions(
      client_id: String,
      provider_name: Option[String] = None,
      authorization_url: Option[String] = None,
      token_url: Option[String] = None,
      scope: Option[List[String]] = None,
      authorization_url_extras: Option[Json] = None,
      identity_config: Option[IdentityConfig] = None,
      use_pkce: Option[Boolean] = None,
      token_url_config: Option[TokenUrlConfig] = None,
  ) derives Codec.AsObject

  case class IdentityConfig(
      url: String,
      account_identifier: String,
      headers: Option[Json] = None,
      body: Option[Json] = None,
      http_method_type: Option[String] = None,
  ) derives Codec.AsObject

  case class TokenUrlConfig(
      use_basic_auth_scheme: Option[Boolean] = None,
  ) derives Codec.AsObject

  // --- Compliance ---

  case class Compliance(
      fedramp_authorization: Option[String] = None,
      dod_srg_ilx: Option[String] = None,
      itar_compliant: Option[String] = None,
  ) derives Codec.AsObject

  // --- App directory ---

  case class AppDirectory(
      app_directory_categories: Option[List[String]] = None,
      use_direct_install: Option[Boolean] = None,
      direct_install_url: Option[String] = None,
      installation_landing_page: Option[String] = None,
      privacy_policy_url: Option[String] = None,
      support_url: Option[String] = None,
      support_email: Option[String] = None,
      supported_languages: Option[List[String]] = None,
      pricing: Option[String] = None,
  ) derives Codec.AsObject
}
