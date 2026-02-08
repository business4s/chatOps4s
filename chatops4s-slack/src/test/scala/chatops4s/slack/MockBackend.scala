package chatops4s.slack

import cats.effect.IO
import sttp.client4.testing.*
import sttp.model.StatusCode

object MockBackend {

  def create(): BackendStub[IO] =
    BackendStub(new sttp.client4.impl.cats.CatsMonadAsyncError[IO])

  def withPostMessage(responseBody: String, statusCode: StatusCode = StatusCode.Ok): BackendStub[IO] =
    create()
      .whenRequestMatches(_.uri.toString().contains("chat.postMessage"))
      .thenRespondAdjust(responseBody, statusCode)

  def withPostMessageAndUpdate(
      postMessageResponse: String,
      updateResponse: String,
      statusCode: StatusCode = StatusCode.Ok,
  ): BackendStub[IO] =
    create()
      .whenRequestMatches(_.uri.toString().contains("chat.postMessage"))
      .thenRespondAdjust(postMessageResponse, statusCode)
      .whenRequestMatches(_.uri.toString().contains("chat.update"))
      .thenRespondAdjust(updateResponse, statusCode)

  def withUpdate(responseBody: String, statusCode: StatusCode = StatusCode.Ok): BackendStub[IO] =
    create()
      .whenRequestMatches(_.uri.toString().contains("chat.update"))
      .thenRespondAdjust(responseBody, statusCode)

  def withResponseUrl(statusCode: StatusCode = StatusCode.Ok): BackendStub[IO] =
    create()
      .whenRequestMatches(_.uri.toString().contains("hooks.slack.com"))
      .thenRespondAdjust("ok", statusCode)
}
