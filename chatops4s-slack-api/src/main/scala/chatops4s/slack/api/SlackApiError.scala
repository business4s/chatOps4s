package chatops4s.slack.api

case class SlackApiError(error: String, details: List[String] = Nil)
    extends RuntimeException(s"Slack API error: $error${if (details.nonEmpty) s". ${details.mkString("; ")}" else ""}")
