package chatops4s.slack.api

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json}

object blocks {

  // --- Blocks ---
  // Each case class corresponds to one documented block type.
  // The `type` field is set automatically by the codec.

  sealed trait Block

  // https://docs.slack.dev/reference/block-kit/blocks/section-block
  case class SectionBlock(
      text: Option[TextObject] = None,
      block_id: Option[String] = None,
      fields: Option[List[TextObject]] = None,
      accessory: Option[Json] = None,
      expand: Option[Boolean] = None,
  ) extends Block derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/blocks/actions-block
  case class ActionsBlock(
      elements: List[Json],
      block_id: Option[String] = None,
  ) extends Block derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/blocks/input-block
  case class InputBlock(
      label: TextObject,
      element: Json,
      block_id: Option[String] = None,
      optional: Option[Boolean] = None,
      dispatch_action: Option[Boolean] = None,
      hint: Option[TextObject] = None,
  ) extends Block derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/blocks/header-block
  case class HeaderBlock(
      text: TextObject,
      block_id: Option[String] = None,
  ) extends Block derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/blocks/context-block
  case class ContextBlock(
      elements: List[Json],
      block_id: Option[String] = None,
  ) extends Block derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/blocks/divider-block
  case class DividerBlock(
      block_id: Option[String] = None,
  ) extends Block derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/blocks/image-block
  case class ImageBlock(
      alt_text: String,
      image_url: Option[String] = None,
      slack_file: Option[Json] = None,
      title: Option[TextObject] = None,
      block_id: Option[String] = None,
  ) extends Block derives Codec.AsObject

  private val blockTypeMap: Map[String, Decoder[Block]] = Map(
    "section" -> Decoder[SectionBlock].map(identity[Block]),
    "actions" -> Decoder[ActionsBlock].map(identity[Block]),
    "input"   -> Decoder[InputBlock].map(identity[Block]),
    "header"  -> Decoder[HeaderBlock].map(identity[Block]),
    "context" -> Decoder[ContextBlock].map(identity[Block]),
    "divider" -> Decoder[DividerBlock].map(identity[Block]),
    "image"   -> Decoder[ImageBlock].map(identity[Block]),
  )

  private def blockTypeName(block: Block): String = block match {
    case _: SectionBlock => "section"
    case _: ActionsBlock => "actions"
    case _: InputBlock   => "input"
    case _: HeaderBlock  => "header"
    case _: ContextBlock => "context"
    case _: DividerBlock => "divider"
    case _: ImageBlock   => "image"
  }

  given Encoder.AsObject[Block] = Encoder.AsObject.instance { block =>
    val base = block match {
      case b: SectionBlock => Encoder.AsObject[SectionBlock].encodeObject(b)
      case b: ActionsBlock => Encoder.AsObject[ActionsBlock].encodeObject(b)
      case b: InputBlock   => Encoder.AsObject[InputBlock].encodeObject(b)
      case b: HeaderBlock  => Encoder.AsObject[HeaderBlock].encodeObject(b)
      case b: ContextBlock => Encoder.AsObject[ContextBlock].encodeObject(b)
      case b: DividerBlock => Encoder.AsObject[DividerBlock].encodeObject(b)
      case b: ImageBlock   => Encoder.AsObject[ImageBlock].encodeObject(b)
    }
    ("type" -> Json.fromString(blockTypeName(block))) +: base
  }

  given Decoder[Block] = Decoder.instance { cursor =>
    cursor.get[String]("type").flatMap { tpe =>
      blockTypeMap.get(tpe) match {
        case Some(decoder) => decoder(cursor)
        case None          => Left(DecodingFailure(s"Unknown block type: $tpe", cursor.history))
      }
    }
  }


  // --- Elements ---

  sealed trait BlockElement

  // https://docs.slack.dev/reference/block-kit/block-elements/button-element
  case class ButtonElement(
      text: TextObject,
      action_id: Option[String] = None,
      value: Option[String] = None,
      url: Option[String] = None,
      style: Option[String] = None,
      confirm: Option[Json] = None,
      accessibility_label: Option[String] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/plain-text-input-element
  case class PlainTextInputElement(
      action_id: Option[String] = None,
      initial_value: Option[String] = None,
      multiline: Option[Boolean] = None,
      min_length: Option[Int] = None,
      max_length: Option[Int] = None,
      dispatch_action_config: Option[Json] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/number-input-element
  case class NumberInputElement(
      is_decimal_allowed: Boolean,
      action_id: Option[String] = None,
      initial_value: Option[String] = None,
      min_value: Option[String] = None,
      max_value: Option[String] = None,
      dispatch_action_config: Option[Json] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/checkboxes-element
  case class CheckboxesElement(
      options: List[BlockOption],
      action_id: Option[String] = None,
      initial_options: Option[List[BlockOption]] = None,
      confirm: Option[Json] = None,
      focus_on_load: Option[Boolean] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/radio-button-group-element
  case class RadioButtonGroupElement(
      options: List[BlockOption],
      action_id: Option[String] = None,
      initial_option: Option[BlockOption] = None,
      confirm: Option[Json] = None,
      focus_on_load: Option[Boolean] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/select-menu-element
  case class StaticSelectElement(
      action_id: Option[String] = None,
      options: Option[List[BlockOption]] = None,
      option_groups: Option[List[Json]] = None,
      initial_option: Option[BlockOption] = None,
      confirm: Option[Json] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/multi-select-menu-element
  case class MultiStaticSelectElement(
      action_id: Option[String] = None,
      options: Option[List[BlockOption]] = None,
      option_groups: Option[List[Json]] = None,
      initial_options: Option[List[BlockOption]] = None,
      confirm: Option[Json] = None,
      max_selected_items: Option[Int] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/overflow-menu-element
  case class OverflowMenuElement(
      options: List[BlockOption],
      action_id: Option[String] = None,
      confirm: Option[Json] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/date-picker-element
  case class DatePickerElement(
      action_id: Option[String] = None,
      initial_date: Option[String] = None,
      confirm: Option[Json] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/time-picker-element
  case class TimePickerElement(
      action_id: Option[String] = None,
      initial_time: Option[String] = None,
      confirm: Option[Json] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
      timezone: Option[String] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/datetime-picker-element
  case class DatetimePickerElement(
      action_id: Option[String] = None,
      initial_date_time: Option[Int] = None,
      confirm: Option[Json] = None,
      focus_on_load: Option[Boolean] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/email-input-element
  case class EmailInputElement(
      action_id: Option[String] = None,
      initial_value: Option[String] = None,
      dispatch_action_config: Option[Json] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/url-input-element
  case class UrlInputElement(
      action_id: Option[String] = None,
      initial_value: Option[String] = None,
      dispatch_action_config: Option[Json] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/image-element
  case class ImageElement(
      alt_text: String,
      image_url: Option[String] = None,
      slack_file: Option[Json] = None,
  ) extends BlockElement derives Codec.AsObject

  private val elementTypeMap: Map[String, Decoder[BlockElement]] = Map(
    "button"              -> Decoder[ButtonElement].map(identity[BlockElement]),
    "plain_text_input"    -> Decoder[PlainTextInputElement].map(identity[BlockElement]),
    "number_input"        -> Decoder[NumberInputElement].map(identity[BlockElement]),
    "checkboxes"          -> Decoder[CheckboxesElement].map(identity[BlockElement]),
    "radio_buttons"       -> Decoder[RadioButtonGroupElement].map(identity[BlockElement]),
    "static_select"       -> Decoder[StaticSelectElement].map(identity[BlockElement]),
    "multi_static_select" -> Decoder[MultiStaticSelectElement].map(identity[BlockElement]),
    "overflow"            -> Decoder[OverflowMenuElement].map(identity[BlockElement]),
    "datepicker"          -> Decoder[DatePickerElement].map(identity[BlockElement]),
    "timepicker"          -> Decoder[TimePickerElement].map(identity[BlockElement]),
    "datetimepicker"      -> Decoder[DatetimePickerElement].map(identity[BlockElement]),
    "email_text_input"    -> Decoder[EmailInputElement].map(identity[BlockElement]),
    "url_text_input"      -> Decoder[UrlInputElement].map(identity[BlockElement]),
    "image"               -> Decoder[ImageElement].map(identity[BlockElement]),
  )

  private def elementTypeName(elem: BlockElement): String = elem match {
    case _: ButtonElement            => "button"
    case _: PlainTextInputElement    => "plain_text_input"
    case _: NumberInputElement       => "number_input"
    case _: CheckboxesElement        => "checkboxes"
    case _: RadioButtonGroupElement  => "radio_buttons"
    case _: StaticSelectElement      => "static_select"
    case _: MultiStaticSelectElement => "multi_static_select"
    case _: OverflowMenuElement      => "overflow"
    case _: DatePickerElement        => "datepicker"
    case _: TimePickerElement        => "timepicker"
    case _: DatetimePickerElement    => "datetimepicker"
    case _: EmailInputElement        => "email_text_input"
    case _: UrlInputElement          => "url_text_input"
    case _: ImageElement             => "image"
  }

  given Encoder.AsObject[BlockElement] = Encoder.AsObject.instance { elem =>
    val base = elem match {
      case e: ButtonElement            => Encoder.AsObject[ButtonElement].encodeObject(e)
      case e: PlainTextInputElement    => Encoder.AsObject[PlainTextInputElement].encodeObject(e)
      case e: NumberInputElement       => Encoder.AsObject[NumberInputElement].encodeObject(e)
      case e: CheckboxesElement        => Encoder.AsObject[CheckboxesElement].encodeObject(e)
      case e: RadioButtonGroupElement  => Encoder.AsObject[RadioButtonGroupElement].encodeObject(e)
      case e: StaticSelectElement      => Encoder.AsObject[StaticSelectElement].encodeObject(e)
      case e: MultiStaticSelectElement => Encoder.AsObject[MultiStaticSelectElement].encodeObject(e)
      case e: OverflowMenuElement      => Encoder.AsObject[OverflowMenuElement].encodeObject(e)
      case e: DatePickerElement        => Encoder.AsObject[DatePickerElement].encodeObject(e)
      case e: TimePickerElement        => Encoder.AsObject[TimePickerElement].encodeObject(e)
      case e: DatetimePickerElement    => Encoder.AsObject[DatetimePickerElement].encodeObject(e)
      case e: EmailInputElement        => Encoder.AsObject[EmailInputElement].encodeObject(e)
      case e: UrlInputElement          => Encoder.AsObject[UrlInputElement].encodeObject(e)
      case e: ImageElement             => Encoder.AsObject[ImageElement].encodeObject(e)
    }
    ("type" -> Json.fromString(elementTypeName(elem))) +: base
  }

  given Decoder[BlockElement] = Decoder.instance { cursor =>
    cursor.get[String]("type").flatMap { tpe =>
      elementTypeMap.get(tpe) match {
        case Some(decoder) => decoder(cursor)
        case None          => Left(DecodingFailure(s"Unknown element type: $tpe", cursor.history))
      }
    }
  }


  // --- Composition objects ---

  // https://docs.slack.dev/reference/block-kit/composition-objects/text-object
  case class TextObject(
      `type`: String,
      text: String,
      emoji: Option[Boolean] = None,
      verbatim: Option[Boolean] = None,
  ) derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/composition-objects/option-object
  case class BlockOption(
      text: TextObject,
      value: String,
      description: Option[TextObject] = None,
      url: Option[String] = None,
  ) derives Codec.AsObject

  // --- Views ---

  // https://docs.slack.dev/reference/views/modal-views
  case class View(
      `type`: String,
      title: TextObject,
      blocks: List[Block],
      callback_id: Option[String] = None,
      submit: Option[TextObject] = None,
      close: Option[TextObject] = None,
      private_metadata: Option[String] = None,
      clear_on_close: Option[Boolean] = None,
      notify_on_close: Option[Boolean] = None,
      external_id: Option[String] = None,
      submit_disabled: Option[Boolean] = None,
  ) derives Codec.AsObject
}
