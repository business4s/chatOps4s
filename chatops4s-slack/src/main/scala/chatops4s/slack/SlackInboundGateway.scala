package chatops4s.slack

import chatops4s.slack.models.*
import chatops4s.{Button, ButtonInteraction, InboundGateway, InteractionContext}
import com.typesafe.scalalogging.StrictLogging
import java.util.UUID
import scala.collection.mutable

trait Ref[F[_], A] {
  def get: F[A]
  def update(f: A => A): F[Unit]
}

object Ref {
  def of[F[_]: Monad, A](initial: A): F[Ref[F, A]] = {
    Monad[F].pure(new MutableRef[F, A](initial))
  }
}

class MutableRef[F[_]: Monad, A](private var value: A) extends Ref[F, A] {
  def get: F[A]                  = Monad[F].pure(value)
  def update(f: A => A): F[Unit] = {
    value = f(value)
    Monad[F].pure(())
  }
}

class SlackInboundGateway[F[_]: Monad] private (
    actionHandlers: Ref[F, Map[String, InteractionContext => F[Unit]]],
) extends InboundGateway[F]
    with StrictLogging {

  private val M = Monad[F]

  override def registerAction(handler: InteractionContext => F[Unit]): F[ButtonInteraction] = {
    M.flatMap(M.pure(UUID.randomUUID().toString)) { actionId =>
      logger.debug(s"Registering action with ID: $actionId")
      M.flatMap(actionHandlers.update(_ + (actionId -> handler))) { _ =>
        M.pure(new SlackButtonInteraction(actionId))
      }
    }
  }

  def handleInteraction(payload: SlackInteractionPayload): F[Unit] = {
    logger.debug(s"Handling interaction: ${payload.`type`}")

    payload.actions.fold(M.pure(())) { actions =>
      // Simple traverse implementation for F[_]
      def traverse[A, B](list: List[A])(f: A => F[B]): F[List[B]] = {
        list.foldLeft(M.pure(List.empty[B])) { (acc, item) =>
          M.flatMap(acc) { results =>
            M.flatMap(f(item)) { result =>
              M.pure(results :+ result)
            }
          }
        }
      }

      M.flatMap(traverse(actions) { action =>
        val context = InteractionContext(
          userId = payload.user.id,
          channelId = payload.channel.id,
          messageId = payload.container.message_ts.getOrElse(""),
        )

        M.flatMap(actionHandlers.get) { handlers =>
          handlers.get(action.action_id) match {
            case Some(handler) =>
              logger.info(s"Executing action ${action.action_id} for user ${context.userId}")
              handler(context)
            case None          =>
              logger.warn(s"Unknown action ID: ${action.action_id}")
              M.pure(())
          }
        }
      })(_ => M.pure(()))
    }
  }
}

object SlackInboundGateway {
  def create[F[_]: Monad]: F[SlackInboundGateway[F]] = {
    Monad[F].flatMap(Ref.of[F, Map[String, InteractionContext => F[Unit]]](Map.empty)) { ref =>
      Monad[F].pure(new SlackInboundGateway[F](ref))
    }
  }
}

class SlackButtonInteraction(actionId: String) extends ButtonInteraction {
  override def render(label: String): Button = Button(label, actionId)
}
