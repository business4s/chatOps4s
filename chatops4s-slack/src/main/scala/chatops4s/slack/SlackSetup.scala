package chatops4s.slack

import chatops4s.slack.api.{SlackAppToken, SlackBotToken}
import chatops4s.slack.api.manifest.SlackAppManifest

trait SlackSetup[F[_]] {
  def registerButton[T <: String](handler: ButtonClick[T] => F[Unit]): F[ButtonId[T]]
  def registerCommand[T: CommandParser](name: String, description: String = "", usageHint: String = "")(
      handler: Command[T] => F[CommandResponse],
  ): F[Unit]
  def registerForm[T: FormDef](handler: FormSubmission[T] => F[Unit]): F[FormId[T]]
  def manifest(appName: String): F[SlackAppManifest]
  def checkSetup(appName: String, manifestPath: String, modifier: SlackAppManifest => SlackAppManifest = identity): F[SetupVerification]
  def validateSetup(appName: String, manifestPath: String, modifier: SlackAppManifest => SlackAppManifest = identity): F[Unit]
  def withUserInfoCache(cache: UserInfoCache[F]): F[Unit]
  def withIdempotencyCheck(check: IdempotencyCheck[F]): F[Unit]
  def onError(handler: Throwable => F[Unit]): F[Unit]

  /** Request graceful shutdown of the socket loop. The loop will stop after
    * the current envelope is processed. In-flight handlers will complete
    * naturally. Does not block -- returns immediately.
    */
  def shutdown(): F[Unit]

  def start(botToken: SlackBotToken, appToken: Option[SlackAppToken] = None): F[Unit]
}
