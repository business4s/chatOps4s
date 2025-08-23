package chatops4s.slack

import cats.effect.{Sync, Ref}
import cats.implicits.*
import chatops4s.slack.models.*
import chatops4s.{Button, ButtonInteraction, InboundGateway, InteractionContext}
import com.typesafe.scalalogging.StrictLogging

import java.util.UUID

class SlackInboundGateway[F[_]: Sync] private (
                                                actionHandlers: Ref[F, Map[String, InteractionContext => F[Unit]]],
                                              ) extends InboundGateway[F] with StrictLogging {

  override def registerAction(handler: InteractionContext => F[Unit]): F[ButtonInteraction] = {
    for {
      actionId <- Sync[F].pure(UUID.randomUUID().toString)
      _        <- Sync[F].delay(logger.debug(s"Registering action with ID: $actionId"))
      _        <- actionHandlers.update(_ + (actionId -> handler))
    } yield new SlackButtonInteraction(actionId)
  }

  def handleInteraction(payload: SlackInteractionPayload): F[Unit] = {
    Sync[F].delay(logger.debug(s"Handling interaction: ${payload.`type`}")) *>
      payload.actions.fold(Sync[F].unit) { actions =>
        actions.traverse_ { action =>
          val context = InteractionContext(
            userId = payload.user.id,
            channelId = payload.channel.id,
            messageId = payload.container.message_ts.getOrElse(""),
          )

          actionHandlers.get.flatMap { handlers =>
            handlers.get(action.action_id) match {
              case Some(handler) =>
                Sync[F].delay(logger.info(s"Executing action ${action.action_id} for user ${context.userId}")) *>
                  handler(context)
              case None          =>
                Sync[F].delay(logger.warn(s"Unknown action ID: ${action.action_id}")) *>
                  Sync[F].unit
            }
          }
        }
      }
  }
}

object SlackInboundGateway {
  def create[F[_]: Sync]: F[SlackInboundGateway[F]] = {
    Ref
      .of[F, Map[String, InteractionContext => F[Unit]]](Map.empty)
      .map(new SlackInboundGateway[F](_))
  }
}

class SlackButtonInteraction(actionId: String) extends ButtonInteraction {
  override def render(label: String): Button = Button(label, actionId)
}