package example.docs

import cats.effect.IO
import chatops4s.slack.api.{SlackApi, SlackBotToken, chat, reactions, users, UserId, ChannelId, Timestamp}
import sttp.client4.Backend

private object RawClientPage {

  def rawClientUsage(backend: Backend[IO], botToken: SlackBotToken): Unit = {
    // start_raw_client_usage
    val api = new SlackApi[IO](backend, botToken)

    // Send a message
    api.chat.postMessage(chat.PostMessageRequest(
      channel = "#general",
      text = "Hello from the raw client",
    ))

    // Add a reaction
    api.reactions.add(reactions.AddRequest(
      channel = ChannelId("C1234567890"),
      timestamp = Timestamp("1234567890.123456"),
      name = "thumbsup",
    ))

    // Look up a user
    api.users.info(users.InfoRequest(user = UserId("U1234567890")))
    // end_raw_client_usage
  }
}
