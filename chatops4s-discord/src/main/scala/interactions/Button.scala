package interactions

import cats.effect.IO

case class Button(
   label: String,
   value: String
)

trait ButtonInteraction {
  def render(label: String): IO[Button]
}