package chatops4s.slack

import cats.effect.kernel.{Async, Ref}
import cats.syntax.all.*
import sttp.client4.WebSocketBackend

trait SlackGateway[F[_]] {
  def send(channel: String, text: String, buttons: Seq[Button] = Seq.empty): F[MessageId]
  def reply(to: MessageId, text: String, buttons: Seq[Button] = Seq.empty): F[MessageId]
  def update(messageId: MessageId, text: String, buttons: Seq[Button] = Seq.empty): F[MessageId]
  def delete(messageId: MessageId): F[Unit]
  def addReaction(messageId: MessageId, emoji: String): F[Unit]
  def removeReaction(messageId: MessageId, emoji: String): F[Unit]
  def sendEphemeral(channel: String, userId: String, text: String): F[Unit]
  def listen(appToken: String): F[Unit]
}

object SlackGateway {

  def create[F[_]: Async](
      token: String,
      backend: WebSocketBackend[F],
  ): F[SlackGateway[F] & SlackSetup[F]] = {
    for {
      handlersRef        <- Ref.of[F, Map[String, ErasedHandler[F]]](Map.empty)
      commandHandlersRef <- Ref.of[F, Map[String, CommandEntry[F]]](Map.empty)
    } yield {
      val client = new SlackClient[F](token, backend)
      val gateway = new SlackGatewayImpl[F](client, handlersRef, commandHandlersRef, backend)
      gateway: SlackGateway[F] & SlackSetup[F]
    }
  }
}
