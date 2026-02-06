package chatops4s.slack

object SlackSetup {

  /** Generates a Slack App Manifest YAML for an app using chatops4s.
    *
    * The generated manifest includes:
    *   - bot user with the given display name
    *   - interactivity enabled, pointing to your interactions URL
    *   - OAuth scopes: chat:write, chat:write.public
    *
    * Usage: paste the output into https://api.slack.com/apps → "Create New App" → "From an app manifest"
    *
    * @param appName
    *   display name for the Slack app
    * @param botName
    *   display name for the bot user
    * @param interactionsUrl
    *   public URL where Slack will POST interaction payloads (must end with /slack/interactions to match the default
    *   endpoint)
    */
  def manifest(
      appName: String,
      botName: String,
      interactionsUrl: String,
  ): String =
    s"""_metadata:
       |  major_version: 1
       |  minor_version: 1
       |display_information:
       |  name: $appName
       |features:
       |  bot_user:
       |    display_name: $botName
       |    always_online: true
       |oauth_config:
       |  scopes:
       |    bot:
       |      - chat:write
       |      - chat:write.public
       |settings:
       |  interactivity:
       |    is_enabled: true
       |    request_url: $interactionsUrl
       |  org_deploy_enabled: false
       |  socket_mode_enabled: false
       |  token_rotation_enabled: false
       |""".stripMargin
}
