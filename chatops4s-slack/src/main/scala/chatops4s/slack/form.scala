package chatops4s.slack

import chatops4s.slack.api.{ChannelId, ConversationId, UserId}
import chatops4s.slack.api.socket.{SelectedOption, ViewStateValue}
import chatops4s.slack.api.blocks.*
import io.circe.Json
import scala.compiletime.{constValue, erasedValue, summonInline}
import scala.deriving.Mirror
import java.time.{Instant, LocalDate, LocalTime}

opaque type Email = String
object Email {
  def apply(value: String): Email = value
  extension (e: Email) def value: String = e
}

opaque type Url = String
object Url {
  def apply(value: String): Url = value
  extension (u: Url) def value: String = u
}

case class FormFieldDef(
    id: String,
    label: String,
    optional: Boolean,
)

trait FieldCodec[T] {
  def optional: Boolean = false
  def buildElement(actionId: String, initialValues: List[String]): BlockElement
  def parse(value: ViewStateValue): Either[String, T]
  def isEmpty(value: ViewStateValue): Boolean
  def encodeInitial(value: T): List[String]
}

object FieldCodec {

  given FieldCodec[String] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      PlainTextInputElement(action_id = actionId, initial_value = initialValues.headOption)
    def parse(value: ViewStateValue): Either[String, String] =
      value.value.toRight("missing value")
    def isEmpty(value: ViewStateValue): Boolean =
      value.value.isEmpty
    def encodeInitial(value: String): List[String] = List(value)
  }

  given FieldCodec[Int] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      NumberInputElement(is_decimal_allowed = false, action_id = actionId, initial_value = initialValues.headOption)
    def parse(value: ViewStateValue): Either[String, Int] =
      value.value.toRight("missing value").flatMap(s => s.toIntOption.toRight(s"'$s' is not a valid integer"))
    def isEmpty(value: ViewStateValue): Boolean =
      value.value.isEmpty
    def encodeInitial(value: Int): List[String] = List(value.toString)
  }

  given FieldCodec[Long] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      NumberInputElement(is_decimal_allowed = false, action_id = actionId, initial_value = initialValues.headOption)
    def parse(value: ViewStateValue): Either[String, Long] =
      value.value.toRight("missing value").flatMap(s => s.toLongOption.toRight(s"'$s' is not a valid long"))
    def isEmpty(value: ViewStateValue): Boolean =
      value.value.isEmpty
    def encodeInitial(value: Long): List[String] = List(value.toString)
  }

  given FieldCodec[Double] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      NumberInputElement(is_decimal_allowed = true, action_id = actionId, initial_value = initialValues.headOption)
    def parse(value: ViewStateValue): Either[String, Double] =
      value.value.toRight("missing value").flatMap(s => s.toDoubleOption.toRight(s"'$s' is not a valid double"))
    def isEmpty(value: ViewStateValue): Boolean =
      value.value.isEmpty
    def encodeInitial(value: Double): List[String] = List(value.toString)
  }

  given FieldCodec[Float] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      NumberInputElement(is_decimal_allowed = true, action_id = actionId, initial_value = initialValues.headOption)
    def parse(value: ViewStateValue): Either[String, Float] =
      value.value.toRight("missing value").flatMap(s => s.toFloatOption.toRight(s"'$s' is not a valid float"))
    def isEmpty(value: ViewStateValue): Boolean =
      value.value.isEmpty
    def encodeInitial(value: Float): List[String] = List(value.toString)
  }

  given FieldCodec[BigDecimal] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      NumberInputElement(is_decimal_allowed = true, action_id = actionId, initial_value = initialValues.headOption)
    def parse(value: ViewStateValue): Either[String, BigDecimal] =
      value.value.toRight("missing value").flatMap(s =>
        try Right(BigDecimal(s))
        catch { case _: NumberFormatException => Left(s"'$s' is not a valid decimal") }
      )
    def isEmpty(value: ViewStateValue): Boolean =
      value.value.isEmpty
    def encodeInitial(value: BigDecimal): List[String] = List(value.toString)
  }

  given FieldCodec[Boolean] with {
    override def optional: Boolean = true
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      CheckboxesElement(
        action_id = actionId,
        options = List(BlockOption(text = PlainTextObject(text = "Yes"), value = "true")),
      )
    def parse(value: ViewStateValue): Either[String, Boolean] =
      Right(value.selected_options.exists(_.nonEmpty))
    def isEmpty(value: ViewStateValue): Boolean =
      value.selected_options.isEmpty
    def encodeInitial(value: Boolean): List[String] = if (value) List("true") else Nil
  }

  given FieldCodec[Email] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      EmailInputElement(action_id = actionId, initial_value = initialValues.headOption)
    def parse(value: ViewStateValue): Either[String, Email] =
      value.value.toRight("missing value").map(Email(_))
    def isEmpty(value: ViewStateValue): Boolean =
      value.value.isEmpty
    def encodeInitial(value: Email): List[String] = List(value.value)
  }

  given FieldCodec[Url] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      UrlInputElement(action_id = actionId, initial_value = initialValues.headOption)
    def parse(value: ViewStateValue): Either[String, Url] =
      value.value.toRight("missing value").map(Url(_))
    def isEmpty(value: ViewStateValue): Boolean =
      value.value.isEmpty
    def encodeInitial(value: Url): List[String] = List(value.value)
  }

  given FieldCodec[LocalDate] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      DatePickerElement(action_id = actionId, initial_date = initialValues.headOption)
    def parse(value: ViewStateValue): Either[String, LocalDate] =
      value.selected_date.toRight("missing date").flatMap(s =>
        try Right(LocalDate.parse(s))
        catch { case _: Exception => Left(s"'$s' is not a valid date") }
      )
    def isEmpty(value: ViewStateValue): Boolean =
      value.selected_date.isEmpty
    def encodeInitial(value: LocalDate): List[String] = List(value.toString)
  }

  given FieldCodec[LocalTime] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      TimePickerElement(action_id = actionId, initial_time = initialValues.headOption)
    def parse(value: ViewStateValue): Either[String, LocalTime] =
      value.selected_time.toRight("missing time").flatMap(s =>
        try Right(LocalTime.parse(s))
        catch { case _: Exception => Left(s"'$s' is not a valid time") }
      )
    def isEmpty(value: ViewStateValue): Boolean =
      value.selected_time.isEmpty
    def encodeInitial(value: LocalTime): List[String] = List(value.toString)
  }

  given FieldCodec[Instant] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      DatetimePickerElement(action_id = actionId, initial_date_time = initialValues.headOption.flatMap(_.toLongOption.map(_.toInt)))
    def parse(value: ViewStateValue): Either[String, Instant] =
      value.selected_date_time.toRight("missing datetime").map(epoch => Instant.ofEpochSecond(epoch.toLong))
    def isEmpty(value: ViewStateValue): Boolean =
      value.selected_date_time.isEmpty
    def encodeInitial(value: Instant): List[String] = List(value.getEpochSecond.toString)
  }

  given FieldCodec[UserId] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      UsersSelectElement(action_id = actionId, initial_user = initialValues.headOption)
    def parse(value: ViewStateValue): Either[String, UserId] =
      value.selected_user.toRight("missing user")
    def isEmpty(value: ViewStateValue): Boolean =
      value.selected_user.isEmpty
    def encodeInitial(value: UserId): List[String] = List(value.value)
  }

  given multiUserIdCodec: FieldCodec[List[UserId]] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      MultiUsersSelectElement(action_id = actionId, initial_users = if (initialValues.isEmpty) None else Some(initialValues))
    def parse(value: ViewStateValue): Either[String, List[UserId]] =
      Right(value.selected_users.getOrElse(Nil))
    def isEmpty(value: ViewStateValue): Boolean =
      value.selected_users.forall(_.isEmpty)
    def encodeInitial(value: List[UserId]): List[String] = value.map(_.value)
  }

  given FieldCodec[ChannelId] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      ChannelsSelectElement(action_id = actionId, initial_channel = initialValues.headOption)
    def parse(value: ViewStateValue): Either[String, ChannelId] =
      value.selected_channel.toRight("missing channel").map(ChannelId(_))
    def isEmpty(value: ViewStateValue): Boolean =
      value.selected_channel.isEmpty
    def encodeInitial(value: ChannelId): List[String] = List(value.value)
  }

  given multiChannelIdCodec: FieldCodec[List[ChannelId]] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      MultiChannelsSelectElement(action_id = actionId, initial_channels = if (initialValues.isEmpty) None else Some(initialValues))
    def parse(value: ViewStateValue): Either[String, List[ChannelId]] =
      Right(value.selected_channels.getOrElse(Nil).map(ChannelId(_)))
    def isEmpty(value: ViewStateValue): Boolean =
      value.selected_channels.forall(_.isEmpty)
    def encodeInitial(value: List[ChannelId]): List[String] = value.map(_.value)
  }

  given FieldCodec[ConversationId] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      ConversationsSelectElement(action_id = actionId, initial_conversation = initialValues.headOption)
    def parse(value: ViewStateValue): Either[String, ConversationId] =
      value.selected_conversation.toRight("missing conversation").map(ConversationId(_))
    def isEmpty(value: ViewStateValue): Boolean =
      value.selected_conversation.isEmpty
    def encodeInitial(value: ConversationId): List[String] = List(value.value)
  }

  given multiConversationIdCodec: FieldCodec[List[ConversationId]] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      MultiConversationsSelectElement(action_id = actionId, initial_conversations = if (initialValues.isEmpty) None else Some(initialValues))
    def parse(value: ViewStateValue): Either[String, List[ConversationId]] =
      Right(value.selected_conversations.getOrElse(Nil).map(ConversationId(_)))
    def isEmpty(value: ViewStateValue): Boolean =
      value.selected_conversations.forall(_.isEmpty)
    def encodeInitial(value: List[ConversationId]): List[String] = value.map(_.value)
  }

  given FieldCodec[Json] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      RichTextInputElement(action_id = actionId)
    def parse(value: ViewStateValue): Either[String, Json] =
      value.rich_text_value.toRight("missing rich text value")
    def isEmpty(value: ViewStateValue): Boolean =
      value.rich_text_value.isEmpty
    def encodeInitial(value: Json): List[String] = Nil
  }

  given fileListCodec: FieldCodec[List[Json]] with {
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      FileInputElement(action_id = actionId)
    def parse(value: ViewStateValue): Either[String, List[Json]] =
      Right(value.files.getOrElse(Nil))
    def isEmpty(value: ViewStateValue): Boolean =
      value.files.forall(_.isEmpty)
    def encodeInitial(value: List[Json]): List[String] = Nil
  }

  given [T](using inner: FieldCodec[T]): FieldCodec[Option[T]] with {
    override def optional: Boolean = true
    def buildElement(actionId: String, initialValues: List[String]): BlockElement =
      inner.buildElement(actionId, initialValues)
    def parse(value: ViewStateValue): Either[String, Option[T]] =
      if (inner.isEmpty(value)) Right(None) else inner.parse(value).map(Some(_))
    def isEmpty(value: ViewStateValue): Boolean =
      inner.isEmpty(value)
    def encodeInitial(value: Option[T]): List[String] =
      value.map(inner.encodeInitial).getOrElse(Nil)
  }

  def staticSelect[T](mappings: List[(BlockOption, T)]): FieldCodec[T] =
    new FieldCodec[T] {
      private val valueMap = mappings.map((opt, t) => opt.value -> t).toMap
      def buildElement(actionId: String, initialValues: List[String]): BlockElement =
        StaticSelectElement(
          action_id = actionId,
          options = Some(mappings.map(_._1)),
          initial_option = initialValues.headOption.flatMap(v => mappings.find(_._1.value == v).map(_._1)),
        )
      def parse(value: ViewStateValue): Either[String, T] =
        value.selected_option.toRight("missing selection").flatMap(opt =>
          valueMap.get(opt.value).toRight(s"unknown option: ${opt.value}")
        )
      def isEmpty(value: ViewStateValue): Boolean =
        value.selected_option.isEmpty
      def encodeInitial(value: T): List[String] =
        mappings.find(_._2 == value).map(_._1.value).toList
    }

  def multiStaticSelect[T](mappings: List[(BlockOption, T)]): FieldCodec[List[T]] =
    new FieldCodec[List[T]] {
      private val valueMap = mappings.map((opt, t) => opt.value -> t).toMap
      def buildElement(actionId: String, initialValues: List[String]): BlockElement =
        MultiStaticSelectElement(
          action_id = actionId,
          options = Some(mappings.map(_._1)),
          initial_options = {
            val matched = initialValues.flatMap(v => mappings.find(_._1.value == v).map(_._1))
            if (matched.isEmpty) None else Some(matched)
          },
        )
      def parse(value: ViewStateValue): Either[String, List[T]] =
        value.selected_options.getOrElse(Nil).traverse(opt =>
          valueMap.get(opt.value).toRight(s"unknown option: ${opt.value}")
        )
      def isEmpty(value: ViewStateValue): Boolean =
        value.selected_options.forall(_.isEmpty)
      def encodeInitial(value: List[T]): List[String] =
        value.flatMap(t => mappings.find(_._2 == t).map(_._1.value))
    }

  def externalSelect(minQueryLength: Option[Int] = None): FieldCodec[String] =
    new FieldCodec[String] {
      def buildElement(actionId: String, initialValues: List[String]): BlockElement =
        ExternalSelectElement(action_id = actionId, min_query_length = minQueryLength)
      def parse(value: ViewStateValue): Either[String, String] =
        value.selected_option.toRight("missing selection").map(_.value)
      def isEmpty(value: ViewStateValue): Boolean =
        value.selected_option.isEmpty
      def encodeInitial(value: String): List[String] = List(value)
    }

  def multiExternalSelect(minQueryLength: Option[Int] = None): FieldCodec[List[String]] =
    new FieldCodec[List[String]] {
      def buildElement(actionId: String, initialValues: List[String]): BlockElement =
        MultiExternalSelectElement(action_id = actionId, min_query_length = minQueryLength)
      def parse(value: ViewStateValue): Either[String, List[String]] =
        Right(value.selected_options.getOrElse(Nil).map(_.value))
      def isEmpty(value: ViewStateValue): Boolean =
        value.selected_options.forall(_.isEmpty)
      def encodeInitial(value: List[String]): List[String] = value
    }

  def radioButtons[T](mappings: List[(BlockOption, T)]): FieldCodec[T] =
    new FieldCodec[T] {
      private val valueMap = mappings.map((opt, t) => opt.value -> t).toMap
      def buildElement(actionId: String, initialValues: List[String]): BlockElement =
        RadioButtonGroupElement(
          action_id = actionId,
          options = mappings.map(_._1),
          initial_option = initialValues.headOption.flatMap(v => mappings.find(_._1.value == v).map(_._1)),
        )
      def parse(value: ViewStateValue): Either[String, T] =
        value.selected_option.toRight("missing selection").flatMap(opt =>
          valueMap.get(opt.value).toRight(s"unknown option: ${opt.value}")
        )
      def isEmpty(value: ViewStateValue): Boolean =
        value.selected_option.isEmpty
      def encodeInitial(value: T): List[String] =
        mappings.find(_._2 == value).map(_._1.value).toList
    }

  def checkboxes[T](mappings: List[(BlockOption, T)]): FieldCodec[List[T]] =
    new FieldCodec[List[T]] {
      private val valueMap = mappings.map((opt, t) => opt.value -> t).toMap
      def buildElement(actionId: String, initialValues: List[String]): BlockElement =
        CheckboxesElement(
          action_id = actionId,
          options = mappings.map(_._1),
          initial_options = {
            val matched = initialValues.flatMap(v => mappings.find(_._1.value == v).map(_._1))
            if (matched.isEmpty) None else Some(matched)
          },
        )
      def parse(value: ViewStateValue): Either[String, List[T]] =
        value.selected_options.getOrElse(Nil).traverse(opt =>
          valueMap.get(opt.value).toRight(s"unknown option: ${opt.value}")
        )
      def isEmpty(value: ViewStateValue): Boolean =
        value.selected_options.forall(_.isEmpty)
      def encodeInitial(value: List[T]): List[String] =
        value.flatMap(t => mappings.find(_._2 == t).map(_._1.value))
    }

  private implicit class ListEitherOps[A, B](list: List[Either[A, B]]) {
    def traverse(f: B => Either[A, B]): Either[A, List[B]] = sequence
    def sequence: Either[A, List[B]] = list.foldRight(Right(Nil): Either[A, List[B]]) { (elem, acc) =>
      for { a <- acc; e <- elem } yield e :: a
    }
  }

  extension [A](list: List[SelectedOption]) {
    private[FieldCodec] def traverse[B](f: SelectedOption => Either[String, B]): Either[String, List[B]] =
      list.foldRight(Right(Nil): Either[String, List[B]]) { (elem, acc) =>
        for { a <- acc; e <- f(elem) } yield e :: a
      }
  }
}

trait FormDef[T] {
  def fields: List[FormFieldDef]
  def parse(values: Map[String, Map[String, ViewStateValue]]): Either[String, T]
  private[slack] def buildBlocks(initialValues: Map[String, List[String]]): List[Block]
}

object FormDef {

  inline def derived[T](using m: Mirror.ProductOf[T]): FormDef[T] = {
    val fieldsAndCodecs = buildFieldsAndCodecs[m.MirroredElemTypes, m.MirroredElemLabels]

    new FormDef[T] {
      def fields: List[FormFieldDef] = fieldsAndCodecs.map(_._1)

      def parse(values: Map[String, Map[String, ViewStateValue]]): Either[String, T] = {
        val results = fieldsAndCodecs.map { case (fieldDef, parseFn, _) =>
          val fieldValues = values.getOrElse(fieldDef.id, Map.empty)
          val elementValue = fieldValues.values.headOption.getOrElse(ViewStateValue())
          parseFn(elementValue)
        }
        val errors = results.collect { case Left(e) => e }
        if (errors.nonEmpty) Left(errors.mkString(", "))
        else {
          val tuple = Tuple.fromArray(results.map(_.toOption.get).toArray)
          Right(m.fromTuple(tuple.asInstanceOf[m.MirroredElemTypes]))
        }
      }

      private[slack] def buildBlocks(initialValues: Map[String, List[String]]): List[Block] =
        fieldsAndCodecs.map { case (fieldDef, _, buildElementFn) =>
          val ivs = initialValues.getOrElse(fieldDef.id, Nil)
          val element = buildElementFn(fieldDef.id, ivs)
          InputBlock(
            block_id = Some(fieldDef.id),
            label = PlainTextObject(text = fieldDef.label),
            element = element,
            optional = if (fieldDef.optional) Some(true) else None,
          )
        }
    }
  }

  private inline def buildFieldsAndCodecs[Types <: Tuple, Labels <: Tuple]: List[(FormFieldDef, ViewStateValue => Either[String, Any], (String, List[String]) => BlockElement)] =
    inline (erasedValue[Types], erasedValue[Labels]) match {
      case (_: EmptyTuple, _) => Nil
      case (_: (t *: ts), _: (l *: ls)) =>
        val codec = summonInline[FieldCodec[t]]
        val label = constValue[l].asInstanceOf[String]
        val humanLabel = label.replaceAll("([A-Z])", " $1").trim.capitalize
        val fieldDef = FormFieldDef(
          id = label,
          label = humanLabel,
          optional = codec.optional,
        )
        val parser: ViewStateValue => Either[String, Any] = value =>
          codec.parse(value).left.map(e => s"$label: $e")
        val buildElementFn: (String, List[String]) => BlockElement = (actionId, ivs) =>
          codec.buildElement(actionId, ivs)
        (fieldDef, parser, buildElementFn) :: buildFieldsAndCodecs[ts, ls]
    }
}

case class FormId[T](value: String)

case class FormSubmission[T](userId: UserId, values: T)
