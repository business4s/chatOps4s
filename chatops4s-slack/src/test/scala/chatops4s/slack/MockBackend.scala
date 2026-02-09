package chatops4s.slack

import cats.effect.IO
import sttp.client4.testing.*
import sttp.model.StatusCode

object MockBackend {

  def create(): WebSocketBackendStub[IO] =
    WebSocketBackendStub(new sttp.client4.impl.cats.CatsMonadAsyncError[IO])

  def withPostMessage(responseBody: String, statusCode: StatusCode = StatusCode.Ok): WebSocketBackendStub[IO] =
    create()
      .whenRequestMatches(_.uri.toString().contains("chat.postMessage"))
      .thenRespondAdjust(responseBody, statusCode)

  def withPostMessageAndUpdate(
      postMessageResponse: String,
      updateResponse: String,
      statusCode: StatusCode = StatusCode.Ok,
  ): WebSocketBackendStub[IO] =
    create()
      .whenRequestMatches(_.uri.toString().contains("chat.postMessage"))
      .thenRespondAdjust(postMessageResponse, statusCode)
      .whenRequestMatches(_.uri.toString().contains("chat.update"))
      .thenRespondAdjust(updateResponse, statusCode)

  def withUpdate(responseBody: String, statusCode: StatusCode = StatusCode.Ok): WebSocketBackendStub[IO] =
    create()
      .whenRequestMatches(_.uri.toString().contains("chat.update"))
      .thenRespondAdjust(responseBody, statusCode)

  def withResponseUrl(statusCode: StatusCode = StatusCode.Ok): WebSocketBackendStub[IO] =
    create()
      .whenRequestMatches(_.uri.toString().contains("hooks.slack.com"))
      .thenRespondAdjust("ok", statusCode)

  private val okBody = """{"ok":true}"""

  def withOkApi(): WebSocketBackendStub[IO] =
    create()
      .whenAnyRequest
      .thenRespondAdjust(okBody)
}
