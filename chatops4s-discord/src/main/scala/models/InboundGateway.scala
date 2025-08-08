// TODO models is not a good package for this. It could be moved to the top level (chatops4s.discord)
package models

import cats.effect.IO

trait InboundGateway {
  def registerAction(handler: InteractionContext => IO[Unit]): ButtonInteraction
}
