package chatops4s.slack

import io.circe.Json
import scala.compiletime.{constValue, erasedValue, summonInline}
import scala.deriving.Mirror

enum FormFieldType {
  case PlainText
  case Integer
  case Decimal
  case Checkbox
}

case class FormFieldDef(
    id: String,
    label: String,
    fieldType: FormFieldType,
    optional: Boolean,
)

trait FieldCodec[T] {
  def fieldType: FormFieldType
  def optional: Boolean = false
  def parse(json: Json): Either[String, T]
}

object FieldCodec {

  private def fromStringValue[T](ft: FormFieldType)(convert: String => Either[String, T]): FieldCodec[T] =
    new FieldCodec[T] {
      def fieldType: FormFieldType = ft
      def parse(json: Json): Either[String, T] =
        json.hcursor.get[String]("value").left.map(_.getMessage).flatMap(convert)
    }

  given FieldCodec[String] = fromStringValue(FormFieldType.PlainText)(Right(_))

  given FieldCodec[Int] = fromStringValue(FormFieldType.Integer)(s =>
    s.toIntOption.toRight(s"'$s' is not a valid integer")
  )

  given FieldCodec[Long] = fromStringValue(FormFieldType.Integer)(s =>
    s.toLongOption.toRight(s"'$s' is not a valid long")
  )

  given FieldCodec[Double] = fromStringValue(FormFieldType.Decimal)(s =>
    s.toDoubleOption.toRight(s"'$s' is not a valid double")
  )

  given FieldCodec[Float] = fromStringValue(FormFieldType.Decimal)(s =>
    s.toFloatOption.toRight(s"'$s' is not a valid float")
  )

  given FieldCodec[BigDecimal] = fromStringValue(FormFieldType.Decimal)(s =>
    try Right(BigDecimal(s))
    catch { case _: NumberFormatException => Left(s"'$s' is not a valid decimal") }
  )

  given FieldCodec[Boolean] with {
    def fieldType: FormFieldType = FormFieldType.Checkbox
    override def optional: Boolean = true
    def parse(json: Json): Either[String, Boolean] = {
      val selected = json.hcursor.downField("selected_options").as[List[Json]]
      selected match {
        case Right(list) => Right(list.nonEmpty)
        case Left(_)     => Right(false)
      }
    }
  }

  given [T](using inner: FieldCodec[T]): FieldCodec[Option[T]] with {
    def fieldType: FormFieldType = inner.fieldType
    override def optional: Boolean = true
    def parse(json: Json): Either[String, Option[T]] = {
      if (json.isNull) Right(None)
      else {
        val valueField = json.hcursor.downField("value")
        if (valueField.succeeded && valueField.as[Json].exists(_.isNull)) Right(None)
        else if (!valueField.succeeded && inner.fieldType != FormFieldType.Checkbox) Right(None)
        else inner.parse(json).map(Some(_))
      }
    }
  }
}

trait FormDef[T] {
  def fields: List[FormFieldDef]
  def parse(values: Map[String, Map[String, Json]]): Either[String, T]
}

object FormDef {

  inline def derived[T](using m: Mirror.ProductOf[T]): FormDef[T] = {
    val fieldsAndParsers = buildFieldsAndParsers[m.MirroredElemTypes, m.MirroredElemLabels]

    new FormDef[T] {
      def fields: List[FormFieldDef] = fieldsAndParsers.map(_._1)
      def parse(values: Map[String, Map[String, Json]]): Either[String, T] = {
        val results = fieldsAndParsers.map { case (fieldDef, parseFn) =>
          val fieldValues = values.getOrElse(fieldDef.id, Map.empty)
          val elementJson = fieldValues.values.headOption.getOrElse(Json.Null)
          parseFn(elementJson)
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

  private inline def buildFieldsAndParsers[Types <: Tuple, Labels <: Tuple]: List[(FormFieldDef, Json => Either[String, Any])] =
    inline (erasedValue[Types], erasedValue[Labels]) match {
      case (_: EmptyTuple, _) => Nil
      case (_: (t *: ts), _: (l *: ls)) =>
        val codec = summonInline[FieldCodec[t]]
        val label = constValue[l].asInstanceOf[String]
        val humanLabel = label.replaceAll("([A-Z])", " $1").trim.capitalize
        val fieldDef = FormFieldDef(
          id = label,
          label = humanLabel,
          fieldType = codec.fieldType,
          optional = codec.optional,
        )
        val parser: Json => Either[String, Any] = json =>
          codec.parse(json).left.map(e => s"$label: $e")
        (fieldDef, parser) :: buildFieldsAndParsers[ts, ls]
    }
}

case class FormId[T](value: String)

case class FormSubmission[T](userId: String, values: T)
