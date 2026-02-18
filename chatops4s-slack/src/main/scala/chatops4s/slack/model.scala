package chatops4s.slack

import chatops4s.slack.api.{ChannelId, Timestamp, TriggerId, UserId}
import chatops4s.slack.api.socket.{Action, InteractionPayload, SlashCommandPayload}
import scala.compiletime.{constValue, erasedValue, summonInline}
import scala.deriving.Mirror

case class MessageId(channel: ChannelId, ts: Timestamp)

opaque type IdempotencyKey = String
object IdempotencyKey {
  def apply(value: String): IdempotencyKey = value
  extension (k: IdempotencyKey) def value: String = k
}

case class ButtonId[T <: String](value: String) {
  def render(label: String, value: T): Button = Button(label, this, value)
  def render(label: String)(using ev: String =:= T): Button = Button(label, this, ev.apply(""))
}

case class Button private (label: String, actionId: String, value: String)

object Button {
  def apply[T <: String](label: String, id: ButtonId[T], value: T): Button =
    new Button(label, id.value, value)
}

case class ButtonClick[T <: String](
    payload: InteractionPayload,
    action: Action,
    value: T,
) {
  def userId: UserId = payload.user.id
  def triggerId: TriggerId = payload.trigger_id
  def messageId: MessageId = MessageId(
    channel = payload.channel.map(_.id).getOrElse(ChannelId("")),
    ts = payload.container.message_ts.getOrElse(Timestamp("")),
  )
  def threadId: Option[MessageId] =
    payload.message.flatMap(_.thread_ts).map(ts =>
      MessageId(payload.channel.map(_.id).getOrElse(ChannelId("")), ts)
    )
}

trait CommandArgCodec[T] {
  def parse(text: String): Either[String, T]
  def show(value: T): String
}

object CommandArgCodec {
  given CommandArgCodec[String] with {
    def parse(text: String): Either[String, String] = Right(text)
    def show(value: String): String = value
  }

  given CommandArgCodec[Int] with {
    def parse(text: String): Either[String, Int] =
      text.toIntOption.toRight(s"'$text' is not a valid integer")
    def show(value: Int): String = value.toString
  }

  given CommandArgCodec[Long] with {
    def parse(text: String): Either[String, Long] =
      text.toLongOption.toRight(s"'$text' is not a valid long")
    def show(value: Long): String = value.toString
  }

  given CommandArgCodec[Double] with {
    def parse(text: String): Either[String, Double] =
      text.toDoubleOption.toRight(s"'$text' is not a valid double")
    def show(value: Double): String = value.toString
  }

  given CommandArgCodec[Float] with {
    def parse(text: String): Either[String, Float] =
      text.toFloatOption.toRight(s"'$text' is not a valid float")
    def show(value: Float): String = value.toString
  }

  given CommandArgCodec[BigDecimal] with {
    def parse(text: String): Either[String, BigDecimal] =
      try Right(BigDecimal(text))
      catch { case _: NumberFormatException => Left(s"'$text' is not a valid decimal") }
    def show(value: BigDecimal): String = value.toString
  }

  given CommandArgCodec[Boolean] with {
    def parse(text: String): Either[String, Boolean] =
      text.toBooleanOption.toRight(s"'$text' is not a valid boolean")
    def show(value: Boolean): String = value.toString
  }

  given [T](using inner: CommandArgCodec[T]): CommandArgCodec[Option[T]] with {
    def parse(text: String): Either[String, Option[T]] =
      if (text.isEmpty) Right(None) else inner.parse(text).map(Some(_))
    def show(value: Option[T]): String = value.map(inner.show).getOrElse("")
  }
}

trait CommandParser[T] {
  def parse(text: String): Either[String, T]
  def usageHint: String = ""
}

object CommandParser {
  given CommandParser[String] with {
    def parse(text: String): Either[String, String] = Right(text)
  }

  inline def derived[T](using m: Mirror.ProductOf[T]): CommandParser[T] = {
    val fieldInfo = buildFieldInfo[m.MirroredElemTypes, m.MirroredElemLabels]
    val hint = fieldInfo.map((name, _) => s"[$name]").mkString(" ")

    new CommandParser[T] {
      override def usageHint: String = hint
      def parse(text: String): Either[String, T] = {
        val parts =
          if (text.trim.isEmpty) Array.empty[String]
          else text.trim.split("\\s+", fieldInfo.size)
        if (parts.length < fieldInfo.size)
          Left(s"Expected ${fieldInfo.size} argument(s): $hint")
        else {
          val results = fieldInfo.zip(parts).map { case ((name, parser), value) =>
            parser(value).left.map(e => s"$name: $e")
          }
          val errors = results.collect { case Left(e) => e }
          if (errors.nonEmpty) Left(errors.mkString(", "))
          else {
            val tuple = Tuple.fromArray(results.map(_.toOption.get).toArray)
            Right(m.fromTuple(tuple.asInstanceOf[m.MirroredElemTypes]))
          }
        }
      }
    }
  }

  private inline def buildFieldInfo[Types <: Tuple, Labels <: Tuple]: List[(String, String => Either[String, Any])] =
    inline (erasedValue[Types], erasedValue[Labels]) match {
      case (_: EmptyTuple, _) => Nil
      case (_: (t *: ts), _: (l *: ls)) =>
        val codec = summonInline[CommandArgCodec[t]]
        val label = constValue[l].asInstanceOf[String]
        val humanLabel = label.replaceAll("([A-Z])", " $1").trim.toLowerCase
        val parser: String => Either[String, Any] = s => codec.parse(s)
        (humanLabel, parser) :: buildFieldInfo[ts, ls]
    }
}

case class Command[T](
    payload: SlashCommandPayload,
    args: T,
) {
  def userId: UserId = payload.user_id
  def channelId: ChannelId = payload.channel_id
  def text: String = payload.text
  def triggerId: TriggerId = payload.trigger_id
}

sealed trait CommandResponse
object CommandResponse {
  case class Ephemeral(text: String) extends CommandResponse
  case class InChannel(text: String) extends CommandResponse
  case object Silent extends CommandResponse
}
