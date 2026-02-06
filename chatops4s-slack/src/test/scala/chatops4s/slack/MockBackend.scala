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
}
