package chatops4s.slack

private[slack] object SlackManifest {

  def generate(
      appName: String,
      commands: Map[String, (String, String)],
      hasInteractivity: Boolean,
  ): String = {
    val commandScopes = if (commands.nonEmpty) "\n      - commands" else ""

    val slashCommands = if (commands.nonEmpty) {
      val entries = commands.toList.sortBy(_._1).map { case (name, (desc, hint)) =>
        val usageHintLine = if (hint.nonEmpty) s"\n      usage_hint: $hint" else ""
        s"""    - command: /$name
           |      description: $desc$usageHintLine
           |      should_escape: false""".stripMargin
      }.mkString("\n")
      s"""
         |  slash_commands:
         |$entries""".stripMargin
    } else ""

    val interactivity = if (hasInteractivity) {
      s"""
         |  interactivity:
         |    is_enabled: true""".stripMargin
    } else ""

    s"""_metadata:
       |  major_version: 1
       |  minor_version: 1
       |display_information:
       |  name: $appName
       |features:
       |  bot_user:
       |    display_name: $appName
       |    always_online: true$slashCommands
       |oauth_config:
       |  scopes:
       |    bot:
       |      - chat:write
       |      - chat:write.public
       |      - reactions:write$commandScopes
       |settings:$interactivity
       |  org_deploy_enabled: false
       |  socket_mode_enabled: true
       |  token_rotation_enabled: false
       |""".stripMargin
  }
}
