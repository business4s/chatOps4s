package chatops4s.slack

trait SlackSetup[F[_]] {
  def onButton[T <: String](handler: (ButtonClick[T], SlackGateway[F]) => F[Unit]): F[ButtonId[T]]
}

object SlackSetup {

  def manifest(
      appName: String,
  ): String =
    s"""_metadata:
       |  major_version: 1
       |  minor_version: 1
       |display_information:
       |  name: $appName
       |features:
       |  bot_user:
       |    display_name: $appName
       |    always_online: true
       |oauth_config:
       |  scopes:
       |    bot:
       |      - chat:write
       |      - chat:write.public
       |settings:
       |  interactivity:
       |    is_enabled: true
       |  org_deploy_enabled: false
       |  socket_mode_enabled: true
       |  token_rotation_enabled: false
       |""".stripMargin
}
