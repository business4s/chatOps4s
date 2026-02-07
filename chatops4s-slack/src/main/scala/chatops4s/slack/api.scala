package chatops4s.slack

import cats.effect.kernel.{Async, Ref}
import cats.syntax.all.*
import sttp.client4.{Backend, WebSocketBackend}

case class MessageId(channel: String, ts: String)

case class ButtonId[T <: String](value: String) {
  def toButton(label: String, value: T): Button = Button(label, this, value)
}

case class Button private (label: String, actionId: String, value: String)

object Button {
  def apply[T <: String](label: String, id: ButtonId[T], value: T): Button =
    new Button(label, id.value, value)
}

case class ButtonClick[T <: String](
    userId: String,
    messageId: MessageId,
    value: T,
)

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

trait SlackGateway[F[_]] {
  def send(channel: String, text: String, buttons: Seq[Button] = Seq.empty): F[MessageId]
  def reply(to: MessageId, text: String, buttons: Seq[Button] = Seq.empty): F[MessageId]
  def update(messageId: MessageId, text: String, buttons: Seq[Button] = Seq.empty): F[MessageId]
  def listen: F[Unit]
}

object SlackGateway {

  private type ErasedHandler[F[_]] = (ButtonClick[String], SlackGateway[F]) => F[Unit]

  def create[F[_]: Async](
      token: String,
      appToken: String,
      backend: WebSocketBackend[F],
  ): F[SlackGateway[F] & SlackSetup[F]] = {
    Ref.of[F, Map[String, ErasedHandler[F]]](Map.empty).map { handlersRef =>
      val listenRef = new java.util.concurrent.atomic.AtomicReference[F[Unit]]()
      val gateway = new SlackGatewayImpl[F](
        token, backend, handlersRef,
        listen = Async[F].defer(listenRef.get()),
      )
      listenRef.set(runSocketLoop(appToken, backend, gateway))
      gateway: SlackGateway[F] & SlackSetup[F]
    }
  }

  private def runSocketLoop[F[_]: Async](
      appToken: String,
      backend: WebSocketBackend[F],
      gateway: SlackGatewayImpl[F],
  ): F[Unit] = {
    import cats.effect.kernel.Temporal
    import scala.concurrent.duration.*
    val loop: F[Unit] = for {
      url <- openSocketUrl(appToken, backend)
      _   <- connectAndHandle(url, backend, gateway)
    } yield ()

    loop.handleErrorWith { _ =>
      Temporal[F].sleep(2.seconds) >> runSocketLoop(appToken, backend, gateway)
    }
  }

  private def openSocketUrl[F[_]: Async](
      appToken: String,
      backend: Backend[F],
  ): F[String] = {
    import sttp.client4.*
    import sttp.client4.circe.*
    import SlackModels.ConnectionsOpenResponse

    val req = basicRequest
      .post(uri"https://slack.com/api/apps.connections.open")
      .header("Authorization", s"Bearer $appToken")
      .contentType("application/x-www-form-urlencoded")
      .response(asJson[ConnectionsOpenResponse])

    backend.send(req).flatMap { response =>
      response.body match {
        case Right(r) if r.ok => r.url match {
          case Some(url) => Async[F].pure(url)
          case None      => Async[F].raiseError(new RuntimeException("No URL in connections.open response"))
        }
        case Right(r) =>
          Async[F].raiseError(new RuntimeException(s"connections.open failed: ${r.error.getOrElse("unknown")}"))
        case Left(err) =>
          Async[F].raiseError(new RuntimeException(s"Failed to parse connections.open response: $err"))
      }
    }
  }

  private def connectAndHandle[F[_]: Async](
      url: String,
      backend: WebSocketBackend[F],
      gateway: SlackGatewayImpl[F],
  ): F[Unit] = {
    import sttp.client4.*
    import sttp.client4.ws.async.*
    import io.circe.parser
    import io.circe.syntax.*
    import SlackModels.{SocketEnvelope, SocketAck}

    basicRequest
      .get(uri"$url")
      .response(asWebSocket[F, Unit] { ws =>
        def loop: F[Unit] =
          ws.receiveText().flatMap { text =>
            parser.decode[SocketEnvelope](text) match {
              case Right(envelope) =>
                val ack = ws.sendText(SocketAck(envelope.envelope_id).asJson.noSpaces)
                val dispatch = envelope.`type` match {
                  case "interactive" =>
                    envelope.payload match {
                      case Some(json) =>
                        json.as[SlackModels.InteractionPayload] match {
                          case Right(payload) => gateway.handleInteractionPayload(payload).attempt.void
                          case Left(_)        => Async[F].unit
                        }
                      case None => Async[F].unit
                    }
                  case _ => Async[F].unit
                }
                ack >> dispatch >> loop
              case Left(_) => loop
            }
          }
        loop
      })
      .send(backend)
      .void
  }
}
