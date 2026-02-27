package chatops4s.slack

import chatops4s.slack.api.{SlackAppToken, SlackBotToken}
import chatops4s.slack.api.manifest.SlackAppManifest

trait SlackSetup[F[_]] {

  /** Handlers accumulate by design -- registered once at startup. */
  def registerButton[T <: String](handler: ButtonClick[T] => F[Unit]): F[ButtonId[T]]

  /** Parse failures are returned as ephemeral error messages to the user. */
  def registerCommand[T: CommandParser](name: String, description: String = "", usageHint: String = "")(
      handler: Command[T] => F[CommandResponse],
  ): F[Unit]

  def registerForm[T: FormDef, M: MetadataCodec](handler: FormSubmission[T, M] => F[Unit]): F[FormId[T, M]]

  def manifest(appName: String): F[SlackAppManifest]
  def checkSetup(appName: String, manifestPath: String, modifier: SlackAppManifest => SlackAppManifest = identity): F[SetupVerification]
  def validateSetup(appName: String, manifestPath: String, modifier: SlackAppManifest => SlackAppManifest = identity): F[Unit]
  def withUserInfoCache(cache: UserInfoCache[F]): F[Unit]
  def withIdempotencyCheck(check: IdempotencyCheck[F]): F[Unit]

  /** Replaces any previously set handler. */
  def onError(handler: Throwable => F[Unit]): F[Unit]

  def shutdown(): F[Unit]

  def listHandlers(): F[HandlerSummary]

  /** App token required for Socket Mode when handlers are registered. */
  def start(botToken: SlackBotToken, appToken: Option[SlackAppToken] = None): F[Unit]
}
