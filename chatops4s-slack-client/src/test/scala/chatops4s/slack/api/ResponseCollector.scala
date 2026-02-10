package chatops4s.slack.api

import sttp.client4.*
import sttp.shared.Identity

import java.nio.file.{Files, Path, Paths}

object ResponseCollector {

  private val outputDir: Path =
    Paths.get("chatops4s-slack-client/src/test/resources/responses")

  def main(args: Array[String]): Unit = {
    val botToken = sys.env.getOrElse("SLACK_BOT_TOKEN", sys.error("SLACK_BOT_TOKEN is required"))
    val channel  = sys.env.getOrElse("SLACK_CHANNEL", sys.error("SLACK_CHANNEL is required"))
    val appToken = sys.env.get("SLACK_APP_TOKEN")
    val userId   = sys.env.get("SLACK_TEST_USER_ID")

    Files.createDirectories(outputDir)

    val backend = DefaultSyncBackend()
    val baseUrl = "https://slack.com/api"

    def postRaw(method: String, body: String, token: String = botToken, contentType: String = "application/json"): String =
      basicRequest
        .post(uri"$baseUrl/$method")
        .header("Authorization", s"Bearer $token")
        .contentType(contentType)
        .body(body)
        .response(asStringAlways)
        .send(backend)
        .body

    def save(filename: String, json: String): Unit = {
      val path = outputDir.resolve(filename)
      Files.writeString(path, json)
      println(s"  Wrote $path (${json.length} bytes)")
    }

    // apps.connections.open (optional)
    appToken.foreach { token =>
      println("Collecting apps.connections.open ...")
      val json = postRaw("apps.connections.open", "", token = token, contentType = "application/x-www-form-urlencoded")
      save("apps.connections.open.json", json)
    }

    // Join the channel first (needed for reactions)
    println("Joining channel ...")
    postRaw("conversations.join", s"""{"channel":"$channel"}""")

    // chat.postMessage
    println("Collecting chat.postMessage ...")
    val postMessageJson = postRaw("chat.postMessage", s"""{"channel":"$channel","text":"chatops4s ResponseCollector test message"}""")
    save("chat.postMessage.json", postMessageJson)

    val ts        = io.circe.parser
      .parse(postMessageJson)
      .toOption
      .flatMap(_.hcursor.get[String]("ts").toOption)
      .getOrElse(sys.error("Failed to extract ts from chat.postMessage response"))
    val channelId = io.circe.parser
      .parse(postMessageJson)
      .toOption
      .flatMap(_.hcursor.get[String]("channel").toOption)
      .getOrElse(sys.error("Failed to extract channel from chat.postMessage response"))

    // chat.update
    println("Collecting chat.update ...")
    val updateJson = postRaw("chat.update", s"""{"channel":"$channelId","ts":"$ts","text":"chatops4s ResponseCollector test message (updated)"}""")
    save("chat.update.json", updateJson)

    // reactions.add
    println("Collecting reactions.add ...")
    val reactionsAddJson = postRaw("reactions.add", s"""{"channel":"$channelId","timestamp":"$ts","name":"white_check_mark"}""")
    save("reactions.add.json", reactionsAddJson)

    // reactions.remove
    println("Collecting reactions.remove ...")
    val reactionsRemoveJson = postRaw("reactions.remove", s"""{"channel":"$channelId","timestamp":"$ts","name":"white_check_mark"}""")
    save("reactions.remove.json", reactionsRemoveJson)

    // chat.postEphemeral (optional)
    userId.foreach { uid =>
      println("Collecting chat.postEphemeral ...")
      val json = postRaw("chat.postEphemeral", s"""{"channel":"$channelId","user":"$uid","text":"chatops4s ResponseCollector ephemeral test"}""")
      save("chat.postEphemeral.json", json)
    }

    // chat.delete
    println("Collecting chat.delete ...")
    val deleteJson = postRaw("chat.delete", s"""{"channel":"$channelId","ts":"$ts"}""")
    save("chat.delete.json", deleteJson)

    // error response — delete the already-deleted message
    println("Collecting error response ...")
    val errorJson = postRaw("chat.delete", s"""{"channel":"$channelId","ts":"$ts"}""")
    save("error.json", errorJson)

    println("Done. Collected response fixtures in " + outputDir)
  }
}
