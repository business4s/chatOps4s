package chatops4s.slack

import cats.effect.{IO, IOApp}
import chatops4s.slack.api.SlackApi
import io.circe.parser
import io.circe.syntax.*
import sttp.client4.*
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.client4.ws.async.*

import java.nio.file.{Files, Path, Paths}

import SlackModels.*

/** Connects to Slack's SocketMode WebSocket and saves raw frames as test fixtures.
  *
  * Usage:
  *   SLACK_APP_TOKEN=xapp-... SLACK_BOT_TOKEN=xoxb-... SLACK_CHANNEL=#test \
  *     sbt "chatops4s-slack/Test/runMain chatops4s.slack.SocketModeCollector"
  *
  * The collector posts a message with a test button, then listens on the WebSocket.
  * Click the button in Slack to capture an interactive event.
  * Type a registered slash command to capture a slash_commands event.
  * Press Ctrl+C to stop.
  */
object SocketModeCollector extends IOApp.Simple {

  private val outputDir: Path =
    Paths.get("chatops4s-slack/src/test/resources/ws-events")

  override def run: IO[Unit] = {
    val appToken = sys.env.getOrElse("SLACK_APP_TOKEN", sys.error("SLACK_APP_TOKEN is required"))
    val botToken = sys.env.getOrElse("SLACK_BOT_TOKEN", sys.error("SLACK_BOT_TOKEN is required"))
    val channel = sys.env.getOrElse("SLACK_CHANNEL", sys.error("SLACK_CHANNEL is required"))

    HttpClientCatsBackend.resource[IO]().use { backend =>
      val client = new SlackClient[IO](botToken, backend)
      for {
        _ <- IO(Files.createDirectories(outputDir))
        _ <- sendTestMessage(client, channel)
        resp <- SlackApi.apps.connectionsOpen(backend, appToken)
        url = resp.okOrThrow.url
        _ <- IO.println(s"Listening on WebSocket. Click the button above or type a slash command.")
        _ <- IO.println("Press Ctrl+C to stop.\n")
        _ <- connectAndCapture(url, backend)
      } yield ()
    }
  }

  private def sendTestMessage(client: SlackClient[IO], channel: String): IO[Unit] = {
    val blocks = List(
      Block(
        `type` = "section",
        text = Some(TextObject(`type` = "mrkdwn", text = "SocketModeCollector — click the button to capture an interactive event")),
      ),
      Block(
        `type` = "actions",
        elements = Some(List(
          BlockElement(
            `type` = "button",
            text = Some(TextObject(`type` = "plain_text", text = "Test Button")),
            action_id = Some("collector-test-btn"),
            value = Some("test-value"),
          ),
        )),
      ),
    )
    client.postMessage(channel, "SocketModeCollector test", Some(blocks), threadTs = None).flatMap { msgId =>
      IO.println(s"Posted test message to $channel (ts=${msgId.ts})")
    }
  }

  private def connectAndCapture(url: String, backend: WebSocketBackend[IO]): IO[Unit] =
    basicRequest
      .get(uri"$url")
      .response(asWebSocketAlways[IO, Unit] { ws =>
        def loop: IO[Unit] =
          ws.receiveText().flatMap { text =>
            val json = parser.parse(text).getOrElse(io.circe.Json.Null)
            val eventType = json.hcursor.get[String]("type").getOrElse("unknown")
            val envelopeId = json.hcursor.get[String]("envelope_id").toOption

            val save = IO {
              val filename = s"$eventType.json"
              val path = outputDir.resolve(filename)
              Files.writeString(path, text)
              println(s"  [$eventType] Saved $path (${text.length} bytes)")
            }

            val ack = envelopeId match {
              case Some(id) =>
                ws.sendText(SocketAck(id).asJson.noSpaces) *>
                  IO.println(s"  [$eventType] Acked envelope $id")
              case None => IO.unit
            }

            save *> ack *> loop
          }

        loop
      })
      .send(backend)
      .void
}
