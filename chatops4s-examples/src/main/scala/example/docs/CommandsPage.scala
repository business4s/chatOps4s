package example.docs

import cats.effect.IO
import chatops4s.slack.{CommandArgCodec, CommandParser, CommandResponse, SlackGateway, SlackSetup}

private object CommandsPage {

  // start_basic_command
  def basicCommand(slack: SlackGateway[IO] & SlackSetup[IO]): IO[Unit] =
    slack.registerCommand[String]("status", "Check system status") { cmd =>
      IO.pure(CommandResponse.Ephemeral(s"All systems operational. You said: ${cmd.args}"))
    }
  // end_basic_command

  // start_derived_command
  case class ScaleArgs(service: String, replicas: Int) derives CommandParser

  def derivedCommand(slack: SlackGateway[IO] & SlackSetup[IO]): IO[Unit] =
    // /scale api 3 → parses to ScaleArgs("api", 3)
    slack.registerCommand[ScaleArgs]("scale", "Scale a service") { cmd =>
      IO.pure(
        CommandResponse.Ephemeral(
          s"Scaling *${cmd.args.service}* to *${cmd.args.replicas}* replicas.",
        ),
      )
    }
  // end_derived_command

  // start_parser_def
  opaque type ServiceName <: String = String

  object ServiceName {
    private val valid = Set("api", "web", "worker")

    def parse(text: String): Either[String, ServiceName] = {
      val name = text.trim.toLowerCase
      if (valid.contains(name)) Right(name)
      else Left(s"Unknown service '$text'. Valid: ${valid.mkString(", ")}")
    }
  }

  given CommandParser[ServiceName] with {
    def parse(text: String): Either[String, ServiceName] = ServiceName.parse(text)
    override def usageHint: String                       = "[service name]"
  }
  // end_parser_def

  // start_parser_usage
  def customParserCommand(slack: SlackGateway[IO] & SlackSetup[IO]): IO[Unit] =
    // /service-status api   → "Service api is healthy."
    // /service-status foo   → error: "Unknown service 'foo'. Valid: api, web, worker"
    slack.registerCommand[ServiceName]("service-status", "Check service status") { cmd =>
      IO.pure(CommandResponse.Ephemeral(s"Service *${cmd.args}* is healthy."))
    }
  // end_parser_usage

  // start_usage_hint
  def usageHint(slack: SlackGateway[IO] & SlackSetup[IO]): IO[Unit] =
    slack.registerCommand[String]("deploy", "Deploy a service", usageHint = "[service] [version]") { cmd =>
      IO.pure(CommandResponse.Ephemeral(s"Deploying ${cmd.args}"))
    }
  // end_usage_hint

  // start_command_context
  def commandContext(slack: SlackGateway[IO] & SlackSetup[IO]): IO[Unit] =
    slack.registerCommand[String]("deploy", "Deploy to production") { cmd =>
      val who     = cmd.userId    // UserId — who ran the command
      val where   = cmd.channelId // ChannelId — which channel
      val raw     = cmd.text      // String — raw command text
      val trigger = cmd.triggerId // TriggerId — for opening forms
      val args    = cmd.args      // T — parsed arguments
      IO.pure(CommandResponse.Ephemeral(s"$who $where $raw $trigger $args"))
    }
  // end_command_context
}
