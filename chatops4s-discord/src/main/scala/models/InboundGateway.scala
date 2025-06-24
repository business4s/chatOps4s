package models

import cats.effect.IO

trait InboundGateway {
  def registerAction(handler: InteractionContext => IO[Unit]): IO[ButtonInteraction]
}
