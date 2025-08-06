package chatops4s.slack

import cats.effect.IO
import sttp.client4.*
import sttp.model.StatusCode

class MockBackend extends Backend[IO] {
  private var responses: Map[String, (String, StatusCode)] = Map.empty

  def setResponse(urlPart: String, responseBody: String, statusCode: StatusCode = StatusCode.Ok): Unit = {
    responses = responses + (urlPart -> (responseBody, statusCode))
  }

  override def send[T](request: GenericRequest[T, ?]): IO[Response[T]] = {
    val url = request.uri.toString()
    val matchingKey = responses.keys.find(url.contains).getOrElse("")

    responses.get(matchingKey) match {
      case Some((body, code)) =>
        IO.pure(Response(
          body = body.asInstanceOf[T],
          code = code,
          statusText = code.toString,
          headers = Seq.empty,
          history = List.empty,
          request = request.onlyMetadata
        ))
      case None =>
        IO.pure(Response(
          body = "Not Found".asInstanceOf[T],
          code = StatusCode.NotFound,
          statusText = "Not Found",
          headers = Seq.empty,
          history = List.empty,
          request = request.onlyMetadata
        ))
    }
  }

  override def close(): IO[Unit] = IO.unit
}