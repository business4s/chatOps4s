package chatops4s.slack

import cats.effect.{IO, Ref}
import cats.implicits.*
import chatops4s.{Button, ButtonInteraction, InboundGateway, InteractionContext}
import chatops4s.slack.models.*
import java.util.UUID

class SlackInboundGateway private (
    actionHandlers: Ref[IO, Map[String, InteractionContext => IO[Unit]]],
) extends InboundGateway {

  override def registerAction(handler: InteractionContext => IO[Unit]): IO[ButtonInteraction] = {
    for {
      actionId <- IO(UUID.randomUUID().toString)
      _        <- actionHandlers.update(_ + (actionId -> handler))
    } yield new SlackButtonInteraction(actionId)
  }

  def handleInteraction(payload: SlackInteractionPayload): IO[Unit] = {
    payload.actions match {
      case Some(actions) =>
        actions.traverse_ { action =>
          val context = InteractionContext(
            userId = payload.user.id,
            channelId = payload.channel.id,
            messageId = payload.container.message_ts.getOrElse(""),
          )

          actionHandlers.get.flatMap { handlers =>
            handlers.get(action.action_id) match {
              case Some(handler) => handler(context)
              case None          => IO.unit // Unknown action, ignore
            }
          }
        }
      case None          => IO.unit
    }
  }
}

object SlackInboundGateway {
  def create: IO[SlackInboundGateway] = {
    Ref
      .of[IO, Map[String, InteractionContext => IO[Unit]]](Map.empty)
      .map(new SlackInboundGateway(_))
  }
}

class SlackButtonInteraction(actionId: String) extends ButtonInteraction {
  override def render(label: String): Button = Button(label, actionId)
}
