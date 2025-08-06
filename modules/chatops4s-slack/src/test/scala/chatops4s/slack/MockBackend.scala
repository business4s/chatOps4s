package chatops4s.slack

import cats.effect.IO
import sttp.client4.*
import sttp.client4.testing.*
import sttp.model.StatusCode
import sttp.monad.MonadAsyncError

object MockBackend {
  def create(): BackendStub[IO] = {
    given MonadAsyncError[IO] = cats.effect.IO.asyncForIO
    BackendStub[IO]
  }

  def withResponse(backend: BackendStub[IO], urlPart: String, responseBody: String, statusCode: StatusCode = StatusCode.Ok): BackendStub[IO] = {
    backend.whenRequestMatches(_.uri.toString().contains(urlPart))
      .thenRespondAdjust(responseBody, statusCode)
  }
}