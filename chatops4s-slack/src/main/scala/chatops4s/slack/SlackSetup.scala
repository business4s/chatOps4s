package chatops4s.slack

trait SlackSetup[F[_]] {
  def onButton[T <: String](handler: (ButtonClick[T], SlackGateway[F]) => F[Unit]): F[ButtonId[T]]
  def onCommand[T: CommandParser](name: String)(handler: Command[T] => F[CommandResponse]): F[Unit]
}

object SlackSetup {

  def manifest(
      appName: String,
      commands: Seq[CommandDef] = Seq.empty,
  ): String = {
    val commandScopes = if (commands.nonEmpty) "\n      - commands" else ""
    val slashCommands = if (commands.nonEmpty) {
      val entries = commands.map { c =>
        s"""    - command: ${c.command}
           |      description: ${c.description}
           |      should_escape: false""".stripMargin
      }.mkString("\n")
      s"""
         |  slash_commands:
         |$entries""".stripMargin
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
       |      - chat:write.public$commandScopes
       |settings:
       |  interactivity:
       |    is_enabled: true
       |  org_deploy_enabled: false
       |  socket_mode_enabled: true
       |  token_rotation_enabled: false
       |""".stripMargin
  }
}
