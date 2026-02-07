package chatops4s.slack

import cats.effect.kernel.{Async, Ref}
import cats.syntax.all.*
import io.circe.{Codec, Json}
import io.circe.syntax.*
import sttp.client4.*
import sttp.client4.circe.*
import java.util.UUID

private[slack] object SlackModels {

  case class PostMessageRequest(
      channel: String,
      text: String,
      blocks: Option[List[Block]] = None,
      thread_ts: Option[String] = None,
  ) derives Codec.AsObject

  case class ResponseMetadata(
      messages: Option[List[String]] = None,
  ) derives Codec.AsObject

  case class PostMessageResponse(
      ok: Boolean,
      channel: Option[String] = None,
      ts: Option[String] = None,
      error: Option[String] = None,
      response_metadata: Option[ResponseMetadata] = None,
  ) derives Codec.AsObject

  case class Block(
      `type`: String,
      text: Option[TextObject] = None,
      elements: Option[List[BlockElement]] = None,
  ) derives Codec.AsObject

  case class TextObject(
      `type`: String,
      text: String,
  ) derives Codec.AsObject

  case class BlockElement(
      `type`: String,
      text: Option[TextObject] = None,
      action_id: Option[String] = None,
      value: Option[String] = None,
  ) derives Codec.AsObject

  case class InteractionPayload(
      `type`: String,
      user: User,
      channel: Channel,
      container: Container,
      actions: Option[List[Action]] = None,
  ) derives Codec.AsObject

  case class User(id: String) derives Codec.AsObject
  case class Channel(id: String) derives Codec.AsObject
  case class Container(message_ts: Option[String] = None) derives Codec.AsObject
  case class Action(action_id: String, value: Option[String] = None) derives Codec.AsObject

  case class ConnectionsOpenResponse(
      ok: Boolean,
      url: Option[String] = None,
      error: Option[String] = None,
  ) derives Codec.AsObject

  case class SocketEnvelope(
      envelope_id: String,
      `type`: String,
      payload: Option[Json] = None,
  ) derives Codec.AsObject

  case class SocketAck(
      envelope_id: String,
  ) derives Codec.AsObject
}

private[slack] type ErasedHandler[F[_]] = (ButtonClick[String], SlackGateway[F]) => F[Unit]

private[slack] class SlackGatewayImpl[F[_]: Async](
    token: String,
    backend: Backend[F],
    handlersRef: Ref[F, Map[String, ErasedHandler[F]]],
    val listen: F[Unit],
) extends SlackGateway[F] with SlackSetup[F] {

  import SlackModels.*

  private val baseUrl = "https://slack.com/api"

  override def onButton[T <: String](handler: (ButtonClick[T], SlackGateway[F]) => F[Unit]): F[ButtonId[T]] = {
    val id = ButtonId[T](UUID.randomUUID().toString)
    val erased = handler.asInstanceOf[ErasedHandler[F]]
    handlersRef.update(_ + (id.value -> erased)).as(id)
  }

  override def send(channel: String, text: String, buttons: Seq[Button]): F[MessageId] =
    postMessage(channel, text, buttons, threadTs = None)

  override def reply(to: MessageId, text: String, buttons: Seq[Button]): F[MessageId] =
    postMessage(to.channel, text, buttons, threadTs = Some(to.ts))

  private[slack] def handleInteractionPayload(payload: InteractionPayload): F[Unit] = {
    handlersRef.get.flatMap { handlers =>
      val messageId = MessageId(
        channel = payload.channel.id,
        ts = payload.container.message_ts.getOrElse(""),
      )

      payload.actions.getOrElse(Nil).traverse_ { action =>
        val click = ButtonClick[String](
          userId = payload.user.id,
          messageId = messageId,
          value = action.value.getOrElse(""),
        )

        handlers.get(action.action_id).traverse_(handler => handler(click, this))
      }
    }
  }

  private def postMessage(
      channel: String,
      text: String,
      buttons: Seq[Button],
      threadTs: Option[String],
  ): F[MessageId] = {
    val blocks = if (buttons.nonEmpty) {
      Some(List(
        Block(
          `type` = "section",
          text = Some(TextObject(`type` = "mrkdwn", text = text)),
        ),
        Block(
          `type` = "actions",
          elements = Some(buttons.map(buttonToElement).toList),
        ),
      ))
    } else None

    val request = PostMessageRequest(
      channel = channel,
      text = text,
      blocks = blocks,
      thread_ts = threadTs,
    )

    val req = basicRequest
      .post(uri"$baseUrl/chat.postMessage")
      .header("Authorization", s"Bearer $token")
      .contentType("application/json")
      .body(request.asJson.deepDropNullValues.noSpaces)
      .response(asJson[PostMessageResponse])

    backend.send(req).flatMap { response =>
      response.body match {
        case Right(slackResp) if slackResp.ok =>
          slackResp.ts match {
            case Some(ts0) => Async[F].pure(MessageId(channel, ts0))
            case None      => Async[F].raiseError(new RuntimeException("No timestamp in response"))
          }
        case Right(slackResp) =>
          val details = slackResp.response_metadata.flatMap(_.messages).getOrElse(Nil).mkString("; ")
          Async[F].raiseError(new RuntimeException(s"Slack API error: ${slackResp.error.getOrElse("unknown")}. $details"))
        case Left(err) =>
          Async[F].raiseError(new RuntimeException(s"Failed to parse response: $err"))
      }
    }
  }

  private def buttonToElement(button: Button): BlockElement =
    BlockElement(
      `type` = "button",
      text = Some(TextObject(`type` = "plain_text", text = button.label)),
      action_id = Some(button.actionId),
      value = Some(button.value),
    )
}
