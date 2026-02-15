package chatops4s.slack

import chatops4s.slack.api.UserId
import sttp.client4.WebSocketBackend
import sttp.monad.syntax.*

trait SlackGateway[F[_]] {
  def send(channel: String, text: String, buttons: Seq[Button] = Seq.empty): F[MessageId]
  def reply(to: MessageId, text: String, buttons: Seq[Button] = Seq.empty): F[MessageId]
  def update(messageId: MessageId, text: String, buttons: Seq[Button] = Seq.empty): F[MessageId]
  def delete(messageId: MessageId): F[Unit]
  def addReaction(messageId: MessageId, emoji: String): F[Unit]
  def removeReaction(messageId: MessageId, emoji: String): F[Unit]
  def sendEphemeral(channel: String, userId: UserId, text: String): F[Unit]
  def openForm[T](triggerId: TriggerId, formId: FormId[T], title: String, submitLabel: String = "Submit", initialValues: InitialValues[T] = InitialValues.of[T]): F[Unit]
  def listen(appToken: String): F[Unit]
}

object SlackGateway {

  def create[F[_]](
      token: String,
      backend: WebSocketBackend[F],
  ): F[SlackGateway[F] & SlackSetup[F]] = {
    given sttp.monad.MonadError[F] = backend.monad
    for {
      handlersRef        <- Ref.of[F, Map[ButtonId[?], ErasedHandler[F]]](Map.empty)
      commandHandlersRef <- Ref.of[F, Map[CommandName, CommandEntry[F]]](Map.empty)
      formHandlersRef    <- Ref.of[F, Map[FormId[?], FormEntry[F]]](Map.empty)
    } yield {
      val client = new SlackClient[F](token, backend)
      val gateway = new SlackGatewayImpl[F](client, handlersRef, commandHandlersRef, formHandlersRef, backend)
      gateway: SlackGateway[F] & SlackSetup[F]
    }
  }
}
