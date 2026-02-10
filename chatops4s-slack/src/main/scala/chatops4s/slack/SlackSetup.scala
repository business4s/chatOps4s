package chatops4s.slack

trait SlackSetup[F[_]] {
  def registerButton[T <: String](handler: ButtonClick[T] => F[Unit]): F[ButtonId[T]]
  def registerCommand[T: CommandParser](name: String, description: String = "")(handler: Command[T] => F[CommandResponse]): F[Unit]
  def manifest(appName: String): F[String]
}
