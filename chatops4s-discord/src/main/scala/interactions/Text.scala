package interactions

case class Text(
  content: String
)

object TextInteraction {
  def render(context: String): Text = {
    Text(
      content = context
    )
  }
}