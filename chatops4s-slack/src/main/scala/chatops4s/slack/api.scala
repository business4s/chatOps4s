package chatops4s.slack

import cats.effect.kernel.{Async, Ref, Resource}
import cats.syntax.all.*
import sttp.client4.Backend
import sttp.tapir.server.ServerEndpoint

case class MessageId(channel: String, ts: String)

case class ButtonId(value: String) extends AnyVal

case class Button(label: String, id: ButtonId)

case class ButtonClick(
    userId: String,
    messageId: MessageId,
    buttonId: ButtonId,
)

trait SlackSetup[F[_]] {
  def onButton(handler: (ButtonClick, SlackGateway[F]) => F[Unit]): F[ButtonId]
}

object SlackSetup {

  /** @param interactionsUrl must end with /slack/interactions to match the default endpoint */
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

trait SlackGateway[F[_]] {
  def send(channel: String, text: String, buttons: Seq[Button] = Seq.empty): F[MessageId]
  def reply(to: MessageId, text: String, buttons: Seq[Button] = Seq.empty): F[MessageId]
  def interactionEndpoint: ServerEndpoint[Any, F]
}

object SlackGateway {

  def create[F[_]: Async, A](
      token: String,
      signingSecret: String,
      backend: Backend[F],
  )(setup: SlackSetup[F] => F[A]): Resource[F, (SlackGateway[F], A)] = {
    Resource.eval {
      for {
        handlersRef <- Ref.of[F, Map[String, (ButtonClick, SlackGateway[F]) => F[Unit]]](Map.empty)
        setupInstance = new SlackSetupImpl[F](handlersRef)
        a <- setup(setupInstance)
        handlers <- handlersRef.get
        gateway = new SlackGatewayImpl[F](token, signingSecret, backend, handlers)
      } yield (gateway, a)
    }
  }
}
