package chatops4s.slack

import cats.effect.IO
import sttp.client4.*
import sttp.model.{Header, StatusCode}

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
        
        val responseBody = request.response match {
          case ResponseAsString(_, _) => body.asInstanceOf[T]
          case _ => body.asInstanceOf[T]
        }

        IO.pure(Response(
          body = responseBody,
          code = code,
          statusText = code.toString,
          headers = Seq.empty[Header],
          history = List.empty,
          request = request.onlyMetadata
        ))
      case None =>
        val notFoundBody = request.response match {
          case ResponseAsString(_, _) => "Not Found".asInstanceOf[T]
          case _ => "Not Found".asInstanceOf[T]
        }

        IO.pure(Response(
          body = notFoundBody,
          code = StatusCode.NotFound,
          statusText = "Not Found",
          headers = Seq.empty[Header],
          history = List.empty,
          request = request.onlyMetadata
        ))
    }
  }

  override def close(): IO[Unit] = IO.unit
}