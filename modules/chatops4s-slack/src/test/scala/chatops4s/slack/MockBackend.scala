package chatops4s.slack

import cats.effect.IO
import sttp.client4.impl.cats.implicits.*
import sttp.client4.testing.*
import sttp.model.StatusCode
import sttp.monad.MonadError

object MockBackend {
  def create(): BackendStub[IO] = {
    BackendStub(MonadError[IO])
  }

  def withResponse(backend: BackendStub[IO], urlPart: String, responseBody: String, statusCode: StatusCode = StatusCode.Ok): BackendStub[IO] = {
    backend.whenRequestMatches(_.uri.toString().contains(urlPart))
      .thenRespondAdjust(responseBody, statusCode)
  }
}