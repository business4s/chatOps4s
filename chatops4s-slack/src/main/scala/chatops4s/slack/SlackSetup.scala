package chatops4s.slack

trait SlackSetup[F[_]] {
  def onButton[T <: String](handler: ButtonClick[T] => F[Unit]): F[ButtonId[T]]
  def onCommand[T: CommandParser](name: String, description: String = "")(handler: Command[T] => F[CommandResponse]): F[Unit]
  def manifest(appName: String): F[String]
}
