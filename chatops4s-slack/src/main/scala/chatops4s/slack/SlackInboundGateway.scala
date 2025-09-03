package chatops4s.slack

import chatops4s.slack.models.*
import chatops4s.{Button, ButtonInteraction, InboundGateway, InteractionContext}
import com.typesafe.scalalogging.StrictLogging
import java.util.UUID
import scala.collection.mutable
import sttp.monad.MonadError

class SlackInboundGateway[F[_]](
    private val actionHandlers: mutable.Map[String, InteractionContext => F[Unit]],
    implicit val monad: MonadError[F],
) extends InboundGateway[F]
    with StrictLogging {

  override def registerAction(handler: InteractionContext => F[Unit]): F[ButtonInteraction] = {
    val actionId = UUID.randomUUID().toString
    logger.debug(s"Registering action with ID: $actionId")
    actionHandlers += (actionId -> handler)
    monad.unit(new SlackButtonInteraction(actionId))
  }

  def handleInteraction(payload: SlackInteractionPayload): F[Unit] = {
    logger.debug(s"Handling interaction: ${payload.`type`}")

    payload.actions match {
      case Some(actions) =>

        actions.foldLeft(monad.unit(())) { (acc, action) =>
          monad.flatMap(acc) { _ =>
            val context = InteractionContext(
              userId = payload.user.id,
              channelId = payload.channel.id,
              messageId = payload.container.message_ts.getOrElse(""),
            )

            actionHandlers.get(action.action_id) match {
              case Some(handler) =>
                logger.info(s"Executing action ${action.action_id} for user ${context.userId}")
                handler(context)
              case None          =>
                logger.warn(s"Unknown action ID: ${action.action_id}")
                monad.unit(())
            }
          }
        }
      case None => monad.unit(())
    }
  }
}

object SlackInboundGateway {
  def create[F[_]](implicit monad: MonadError[F]): F[SlackInboundGateway[F]] = {
    val actionHandlers = mutable.Map.empty[String, InteractionContext => F[Unit]]
    monad.unit(new SlackInboundGateway[F](actionHandlers, monad))
  }
}

class SlackButtonInteraction(actionId: String) extends ButtonInteraction {
  override def render(label: String): Button = Button(label, actionId)
}
