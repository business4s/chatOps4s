package chatops4s.slack

import cats.effect.kernel.{Async, Ref}
import cats.syntax.all.*
import sttp.client4.WebSocketBackend

trait SlackGateway[F[_]] {
  def send(channel: String, text: String, buttons: Seq[Button] = Seq.empty): F[MessageId]
  def reply(to: MessageId, text: String, buttons: Seq[Button] = Seq.empty): F[MessageId]
  def update(messageId: MessageId, text: String, buttons: Seq[Button] = Seq.empty): F[MessageId]
  def listen: F[Unit]
}

object SlackGateway {

  def create[F[_]: Async](
      token: String,
      appToken: String,
      backend: WebSocketBackend[F],
  ): F[SlackGateway[F] & SlackSetup[F]] = {
    for {
      handlersRef        <- Ref.of[F, Map[String, ErasedHandler[F]]](Map.empty)
      commandHandlersRef <- Ref.of[F, Map[String, ErasedCommandHandler[F]]](Map.empty)
    } yield {
      val client = new SlackClient[F](token, backend)
      val listenRef = new java.util.concurrent.atomic.AtomicReference[F[Unit]]()
      val gateway = new SlackGatewayImpl[F](
        client, handlersRef, commandHandlersRef,
        listen = Async[F].defer(listenRef.get()),
      )
      listenRef.set(SocketMode.runLoop(appToken, backend, gateway.handleInteractionPayload, gateway.handleSlashCommandPayload))
      gateway: SlackGateway[F] & SlackSetup[F]
    }
  }
}
