package chatops4s.slack

import cats.effect.kernel.{Async, Ref, Resource}
import cats.syntax.functor.*
import sttp.client4.Backend
import sttp.tapir.server.ServerEndpoint

// Public API models

case class MessageId(channel: String, ts: String)

case class ButtonId(value: String) extends AnyVal

case class Button(label: String, id: ButtonId)

case class ButtonClick(
    userId: String,
    messageId: MessageId,
    buttonId: ButtonId,
)

// Main gateway trait

trait SlackGateway[F[_]] {
  def send(channel: String, text: String, buttons: Seq[Button] = Seq.empty): F[MessageId]
  def reply(to: MessageId, text: String, buttons: Seq[Button] = Seq.empty): F[MessageId]
  def onButton(handler: ButtonClick => F[Unit]): F[ButtonId]
  def interactionEndpoint: ServerEndpoint[Any, F]
}

object SlackGateway {

  def create[F[_]: Async](
      token: String,
      signingSecret: String,
      backend: Backend[F],
  ): Resource[F, SlackGateway[F]] = {
    Resource.eval {
      Ref.of[F, Map[String, ButtonClick => F[Unit]]](Map.empty).map { handlersRef =>
        new SlackGatewayImpl[F](token, signingSecret, backend, handlersRef)
      }
    }
  }
}
