package chatops4s.slack

import chatops4s.slack.api.{SlackAppToken, SlackBotToken}
import chatops4s.slack.api.manifest.SlackAppManifest

trait SlackSetup[F[_]] {

  /** Register a button click handler and return a [[ButtonId]] for rendering buttons.
    * Handlers are registered once at startup and accumulate by design.
    */
  def registerButton[T <: String](handler: ButtonClick[T] => F[Unit]): F[ButtonId[T]]

  /** Register a slash command handler. The `CommandParser` resolves argument parsing from the command
    * text; parse failures are returned as ephemeral error messages to the user.
    */
  def registerCommand[T: CommandParser](name: String, description: String = "", usageHint: String = "")(
      handler: Command[T] => F[CommandResponse],
  ): F[Unit]

  /** Register a form submission handler and return a [[FormId]] for opening forms.
    * The `FormDef` type class drives both form rendering and submission parsing.
    */
  def registerForm[T: FormDef](handler: FormSubmission[T] => F[Unit]): F[FormId[T]]

  def manifest(appName: String): F[SlackAppManifest]
  def checkSetup(appName: String, manifestPath: String, modifier: SlackAppManifest => SlackAppManifest = identity): F[SetupVerification]
  def validateSetup(appName: String, manifestPath: String, modifier: SlackAppManifest => SlackAppManifest = identity): F[Unit]
  def withUserInfoCache(cache: UserInfoCache[F]): F[Unit]
  def withIdempotencyCheck(check: IdempotencyCheck[F]): F[Unit]

  /** Set a custom error handler for dispatch-level errors (button, command, and form handler failures).
    * The default handler logs the error. Replaces any previously set handler.
    */
  def onError(handler: Throwable => F[Unit]): F[Unit]

  /** Request graceful shutdown of the socket loop. The loop will stop after
    * the current envelope is processed. In-flight handlers will complete
    * naturally. Does not block -- returns immediately.
    */
  def shutdown(): F[Unit]

  /** Returns a summary of all registered button, command, and form handlers.
    * Useful for debugging and verifying that expected handlers are registered.
    */
  def listHandlers(): F[HandlerSummary]

  /** Start the Slack connection. If buttons, commands, or forms are registered, an app token
    * is required for Socket Mode. Call `shutdown()` to stop the socket loop.
    */
  def start(botToken: SlackBotToken, appToken: Option[SlackAppToken] = None): F[Unit]
}
