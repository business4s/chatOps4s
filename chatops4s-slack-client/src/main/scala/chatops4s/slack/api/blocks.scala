package chatops4s.slack.api

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}

object blocks {

  // --- Composition objects ---
  // Defined first as blocks and elements reference them.

  // https://docs.slack.dev/reference/block-kit/composition-objects/text-object
  sealed trait ContextBlockElement

  sealed trait TextObject extends ContextBlockElement {
    def text: String
  }

  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/composition/PlainTextObject.java
  case class PlainTextObject(
      text: String,
      emoji: Option[Boolean] = None,
  ) extends TextObject derives Codec.AsObject

  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/composition/MarkdownTextObject.java
  case class MarkdownTextObject(
      text: String,
      verbatim: Option[Boolean] = None,
  ) extends TextObject derives Codec.AsObject

  given Encoder.AsObject[TextObject] = Encoder.AsObject.instance {
    case p: PlainTextObject =>
      ("type" -> Json.fromString("plain_text")) +: Encoder.AsObject[PlainTextObject].encodeObject(p)
    case m: MarkdownTextObject =>
      ("type" -> Json.fromString("mrkdwn")) +: Encoder.AsObject[MarkdownTextObject].encodeObject(m)
  }

  given Decoder[TextObject] = Decoder.instance { cursor =>
    cursor.get[String]("type").flatMap {
      case "plain_text" => cursor.as[PlainTextObject]
      case "mrkdwn"     => cursor.as[MarkdownTextObject]
      case other        => Left(DecodingFailure(s"Unknown text type: $other", cursor.history))
    }
  }

  // https://docs.slack.dev/reference/block-kit/composition-objects/option-object
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/composition/OptionObject.java
  case class BlockOption(
      text: TextObject,
      value: String,
      description: Option[TextObject] = None,
      url: Option[String] = None,
  ) derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/composition-objects/confirmation-dialog-object
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/composition/ConfirmationDialogObject.java
  case class ConfirmationDialogObject(
      title: TextObject,
      text: TextObject,
      confirm: TextObject,
      deny: TextObject,
      style: Option[String] = None,
  ) derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/composition-objects/dispatch-action-configuration-object
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/composition/DispatchActionConfig.java
  case class DispatchActionConfig(
      trigger_actions_on: Option[List[String]] = None,
  ) derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/composition-objects/option-group-object
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/composition/OptionGroupObject.java
  case class OptionGroupObject(
      label: TextObject,
      options: List[BlockOption],
  ) derives Codec.AsObject

  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/composition/SlackFileObject.java
  case class SlackFileObject(
      id: Option[String] = None,
      url: Option[String] = None,
  ) derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/composition-objects/conversation-filter-object
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/ConversationsSelectElement.java
  case class ConversationsFilter(
      include: Option[List[String]] = None,
      exclude_external_shared_channels: Option[Boolean] = None,
      exclude_bot_users: Option[Boolean] = None,
  ) derives Codec.AsObject

  // --- Elements ---

  sealed trait BlockElement

  // https://docs.slack.dev/reference/block-kit/block-elements/button-element
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/ButtonElement.java
  case class ButtonElement(
      text: TextObject,
      action_id: String,
      value: Option[String] = None,
      url: Option[String] = None,
      style: Option[String] = None,
      confirm: Option[ConfirmationDialogObject] = None,
      accessibility_label: Option[String] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/plain-text-input-element
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/PlainTextInputElement.java
  case class PlainTextInputElement(
      action_id: String,
      initial_value: Option[String] = None,
      multiline: Option[Boolean] = None,
      min_length: Option[Int] = None,
      max_length: Option[Int] = None,
      dispatch_action_config: Option[DispatchActionConfig] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/number-input-element
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/NumberInputElement.java
  case class NumberInputElement(
      is_decimal_allowed: Boolean,
      action_id: String,
      initial_value: Option[String] = None,
      min_value: Option[String] = None,
      max_value: Option[String] = None,
      dispatch_action_config: Option[DispatchActionConfig] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/checkboxes-element
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/CheckboxesElement.java
  case class CheckboxesElement(
      options: List[BlockOption],
      action_id: String,
      initial_options: Option[List[BlockOption]] = None,
      confirm: Option[ConfirmationDialogObject] = None,
      focus_on_load: Option[Boolean] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/radio-button-group-element
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/RadioButtonsElement.java
  case class RadioButtonGroupElement(
      options: List[BlockOption],
      action_id: String,
      initial_option: Option[BlockOption] = None,
      confirm: Option[ConfirmationDialogObject] = None,
      focus_on_load: Option[Boolean] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/select-menu-element
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/StaticSelectElement.java
  case class StaticSelectElement(
      action_id: String,
      options: Option[List[BlockOption]] = None,
      option_groups: Option[List[OptionGroupObject]] = None,
      initial_option: Option[BlockOption] = None,
      confirm: Option[ConfirmationDialogObject] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/multi-select-menu-element
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/MultiStaticSelectElement.java
  case class MultiStaticSelectElement(
      action_id: String,
      options: Option[List[BlockOption]] = None,
      option_groups: Option[List[OptionGroupObject]] = None,
      initial_options: Option[List[BlockOption]] = None,
      confirm: Option[ConfirmationDialogObject] = None,
      max_selected_items: Option[Int] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/overflow-menu-element
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/OverflowMenuElement.java
  case class OverflowMenuElement(
      options: List[BlockOption],
      action_id: String,
      confirm: Option[ConfirmationDialogObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/date-picker-element
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/DatePickerElement.java
  case class DatePickerElement(
      action_id: String,
      initial_date: Option[String] = None,
      confirm: Option[ConfirmationDialogObject] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/time-picker-element
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/TimePickerElement.java
  case class TimePickerElement(
      action_id: String,
      initial_time: Option[String] = None,
      confirm: Option[ConfirmationDialogObject] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
      timezone: Option[String] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/datetime-picker-element
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/DatetimePickerElement.java
  case class DatetimePickerElement(
      action_id: String,
      initial_date_time: Option[Int] = None,
      confirm: Option[ConfirmationDialogObject] = None,
      focus_on_load: Option[Boolean] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/email-input-element
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/EmailTextInputElement.java
  case class EmailInputElement(
      action_id: String,
      initial_value: Option[String] = None,
      dispatch_action_config: Option[DispatchActionConfig] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/url-input-element
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/URLTextInputElement.java
  case class UrlInputElement(
      action_id: String,
      initial_value: Option[String] = None,
      dispatch_action_config: Option[DispatchActionConfig] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/image-element
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/ImageElement.java
  case class ImageElement(
      alt_text: String,
      image_url: Option[String] = None,
      slack_file: Option[SlackFileObject] = None,
      fallback: Option[String] = None,
      image_width: Option[Int] = None,
      image_height: Option[Int] = None,
      image_bytes: Option[Int] = None,
  ) extends BlockElement with ContextBlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/select-menu-element#conversations_select
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/ConversationsSelectElement.java
  case class ConversationsSelectElement(
      action_id: String,
      initial_conversation: Option[String] = None,
      default_to_current_conversation: Option[Boolean] = None,
      confirm: Option[ConfirmationDialogObject] = None,
      response_url_enabled: Option[Boolean] = None,
      filter: Option[ConversationsFilter] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/select-menu-element#channels_select
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/ChannelsSelectElement.java
  case class ChannelsSelectElement(
      action_id: String,
      initial_channel: Option[String] = None,
      confirm: Option[ConfirmationDialogObject] = None,
      response_url_enabled: Option[Boolean] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/select-menu-element#users_select
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/UsersSelectElement.java
  case class UsersSelectElement(
      action_id: String,
      initial_user: Option[String] = None,
      confirm: Option[ConfirmationDialogObject] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/select-menu-element#external_select
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/ExternalSelectElement.java
  case class ExternalSelectElement(
      action_id: String,
      initial_option: Option[BlockOption] = None,
      min_query_length: Option[Int] = None,
      confirm: Option[ConfirmationDialogObject] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/multi-select-menu-element#users_multi_select
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/MultiUsersSelectElement.java
  case class MultiUsersSelectElement(
      action_id: String,
      initial_users: Option[List[String]] = None,
      confirm: Option[ConfirmationDialogObject] = None,
      max_selected_items: Option[Int] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/multi-select-menu-element#channel_multi_select
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/MultiChannelsSelectElement.java
  case class MultiChannelsSelectElement(
      action_id: String,
      initial_channels: Option[List[String]] = None,
      confirm: Option[ConfirmationDialogObject] = None,
      max_selected_items: Option[Int] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/multi-select-menu-element#conversation_multi_select
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/MultiConversationsSelectElement.java
  case class MultiConversationsSelectElement(
      action_id: String,
      initial_conversations: Option[List[String]] = None,
      default_to_current_conversation: Option[Boolean] = None,
      confirm: Option[ConfirmationDialogObject] = None,
      max_selected_items: Option[Int] = None,
      filter: Option[ConversationsFilter] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/multi-select-menu-element#external_multi_select
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/MultiExternalSelectElement.java
  case class MultiExternalSelectElement(
      action_id: String,
      initial_options: Option[List[BlockOption]] = None,
      min_query_length: Option[Int] = None,
      confirm: Option[ConfirmationDialogObject] = None,
      max_selected_items: Option[Int] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/rich-text-input-element
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/RichTextInputElement.java
  case class RichTextInputElement(
      action_id: String,
      initial_value: Option[Json] = None,
      dispatch_action_config: Option[DispatchActionConfig] = None,
      focus_on_load: Option[Boolean] = None,
      placeholder: Option[TextObject] = None,
  ) extends BlockElement derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/block-elements/file-input-element
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/element/FileInputElement.java
  case class FileInputElement(
      action_id: String,
      filetypes: Option[List[String]] = None,
      max_files: Option[Int] = None,
  ) extends BlockElement derives Codec.AsObject

  case class UnknownBlockElement(raw: Json) extends BlockElement

  private val elementTypeMap: Map[String, Decoder[BlockElement]] = Map(
    "button"                       -> Decoder[ButtonElement].map(identity[BlockElement]),
    "plain_text_input"             -> Decoder[PlainTextInputElement].map(identity[BlockElement]),
    "number_input"                 -> Decoder[NumberInputElement].map(identity[BlockElement]),
    "checkboxes"                   -> Decoder[CheckboxesElement].map(identity[BlockElement]),
    "radio_buttons"                -> Decoder[RadioButtonGroupElement].map(identity[BlockElement]),
    "static_select"                -> Decoder[StaticSelectElement].map(identity[BlockElement]),
    "multi_static_select"          -> Decoder[MultiStaticSelectElement].map(identity[BlockElement]),
    "overflow"                     -> Decoder[OverflowMenuElement].map(identity[BlockElement]),
    "datepicker"                   -> Decoder[DatePickerElement].map(identity[BlockElement]),
    "timepicker"                   -> Decoder[TimePickerElement].map(identity[BlockElement]),
    "datetimepicker"               -> Decoder[DatetimePickerElement].map(identity[BlockElement]),
    "email_text_input"             -> Decoder[EmailInputElement].map(identity[BlockElement]),
    "url_text_input"               -> Decoder[UrlInputElement].map(identity[BlockElement]),
    "image"                        -> Decoder[ImageElement].map(identity[BlockElement]),
    "conversations_select"         -> Decoder[ConversationsSelectElement].map(identity[BlockElement]),
    "channels_select"              -> Decoder[ChannelsSelectElement].map(identity[BlockElement]),
    "users_select"                 -> Decoder[UsersSelectElement].map(identity[BlockElement]),
    "external_select"              -> Decoder[ExternalSelectElement].map(identity[BlockElement]),
    "multi_users_select"           -> Decoder[MultiUsersSelectElement].map(identity[BlockElement]),
    "multi_channels_select"        -> Decoder[MultiChannelsSelectElement].map(identity[BlockElement]),
    "multi_conversations_select"   -> Decoder[MultiConversationsSelectElement].map(identity[BlockElement]),
    "multi_external_select"        -> Decoder[MultiExternalSelectElement].map(identity[BlockElement]),
    "rich_text_input"              -> Decoder[RichTextInputElement].map(identity[BlockElement]),
    "file_input"                   -> Decoder[FileInputElement].map(identity[BlockElement]),
  )

  private def elementTypeName(elem: BlockElement): String = elem match {
    case _: ButtonElement                    => "button"
    case _: PlainTextInputElement            => "plain_text_input"
    case _: NumberInputElement               => "number_input"
    case _: CheckboxesElement                => "checkboxes"
    case _: RadioButtonGroupElement          => "radio_buttons"
    case _: StaticSelectElement              => "static_select"
    case _: MultiStaticSelectElement         => "multi_static_select"
    case _: OverflowMenuElement              => "overflow"
    case _: DatePickerElement                => "datepicker"
    case _: TimePickerElement                => "timepicker"
    case _: DatetimePickerElement            => "datetimepicker"
    case _: EmailInputElement                => "email_text_input"
    case _: UrlInputElement                  => "url_text_input"
    case _: ImageElement                     => "image"
    case _: ConversationsSelectElement       => "conversations_select"
    case _: ChannelsSelectElement            => "channels_select"
    case _: UsersSelectElement               => "users_select"
    case _: ExternalSelectElement            => "external_select"
    case _: MultiUsersSelectElement          => "multi_users_select"
    case _: MultiChannelsSelectElement       => "multi_channels_select"
    case _: MultiConversationsSelectElement  => "multi_conversations_select"
    case _: MultiExternalSelectElement       => "multi_external_select"
    case _: RichTextInputElement             => "rich_text_input"
    case _: FileInputElement                 => "file_input"
    case _: UnknownBlockElement              => ""
  }

  given Encoder.AsObject[BlockElement] = Encoder.AsObject.instance {
    case e: UnknownBlockElement =>
      e.raw.asObject.getOrElse(JsonObject.empty)
    case elem =>
      val base = elem match {
        case e: ButtonElement                   => Encoder.AsObject[ButtonElement].encodeObject(e)
        case e: PlainTextInputElement           => Encoder.AsObject[PlainTextInputElement].encodeObject(e)
        case e: NumberInputElement              => Encoder.AsObject[NumberInputElement].encodeObject(e)
        case e: CheckboxesElement               => Encoder.AsObject[CheckboxesElement].encodeObject(e)
        case e: RadioButtonGroupElement         => Encoder.AsObject[RadioButtonGroupElement].encodeObject(e)
        case e: StaticSelectElement             => Encoder.AsObject[StaticSelectElement].encodeObject(e)
        case e: MultiStaticSelectElement        => Encoder.AsObject[MultiStaticSelectElement].encodeObject(e)
        case e: OverflowMenuElement             => Encoder.AsObject[OverflowMenuElement].encodeObject(e)
        case e: DatePickerElement               => Encoder.AsObject[DatePickerElement].encodeObject(e)
        case e: TimePickerElement               => Encoder.AsObject[TimePickerElement].encodeObject(e)
        case e: DatetimePickerElement           => Encoder.AsObject[DatetimePickerElement].encodeObject(e)
        case e: EmailInputElement               => Encoder.AsObject[EmailInputElement].encodeObject(e)
        case e: UrlInputElement                 => Encoder.AsObject[UrlInputElement].encodeObject(e)
        case e: ImageElement                    => Encoder.AsObject[ImageElement].encodeObject(e)
        case e: ConversationsSelectElement      => Encoder.AsObject[ConversationsSelectElement].encodeObject(e)
        case e: ChannelsSelectElement           => Encoder.AsObject[ChannelsSelectElement].encodeObject(e)
        case e: UsersSelectElement              => Encoder.AsObject[UsersSelectElement].encodeObject(e)
        case e: ExternalSelectElement           => Encoder.AsObject[ExternalSelectElement].encodeObject(e)
        case e: MultiUsersSelectElement         => Encoder.AsObject[MultiUsersSelectElement].encodeObject(e)
        case e: MultiChannelsSelectElement      => Encoder.AsObject[MultiChannelsSelectElement].encodeObject(e)
        case e: MultiConversationsSelectElement => Encoder.AsObject[MultiConversationsSelectElement].encodeObject(e)
        case e: MultiExternalSelectElement      => Encoder.AsObject[MultiExternalSelectElement].encodeObject(e)
        case e: RichTextInputElement            => Encoder.AsObject[RichTextInputElement].encodeObject(e)
        case e: FileInputElement                => Encoder.AsObject[FileInputElement].encodeObject(e)
        case _: UnknownBlockElement             => JsonObject.empty
      }
      ("type" -> Json.fromString(elementTypeName(elem))) +: base
  }

  given Decoder[BlockElement] = Decoder.instance { cursor =>
    cursor.get[String]("type").flatMap { tpe =>
      elementTypeMap.get(tpe) match {
        case Some(decoder) => decoder(cursor)
        case None          => cursor.as[Json].map(UnknownBlockElement(_))
      }
    }
  }

  // --- ContextBlockElement codecs ---

  given Encoder.AsObject[ContextBlockElement] = Encoder.AsObject.instance {
    case p: PlainTextObject =>
      ("type" -> Json.fromString("plain_text")) +: Encoder.AsObject[PlainTextObject].encodeObject(p)
    case m: MarkdownTextObject =>
      ("type" -> Json.fromString("mrkdwn")) +: Encoder.AsObject[MarkdownTextObject].encodeObject(m)
    case i: ImageElement =>
      ("type" -> Json.fromString("image")) +: Encoder.AsObject[ImageElement].encodeObject(i)
  }

  given Decoder[ContextBlockElement] = Decoder.instance { cursor =>
    cursor.get[String]("type").flatMap {
      case "plain_text" => cursor.as[PlainTextObject]
      case "mrkdwn"     => cursor.as[MarkdownTextObject]
      case "image"      => cursor.as[ImageElement]
      case other        => Left(DecodingFailure(s"Unknown context element type: $other", cursor.history))
    }
  }

  // --- Blocks ---

  sealed trait Block

  // https://docs.slack.dev/reference/block-kit/blocks/section-block
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/SectionBlock.java
  case class SectionBlock(
      text: Option[TextObject] = None,
      block_id: Option[String] = None,
      fields: Option[List[TextObject]] = None,
      accessory: Option[BlockElement] = None,
      expand: Option[Boolean] = None,
  ) extends Block derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/blocks/actions-block
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/ActionsBlock.java
  case class ActionsBlock(
      elements: List[BlockElement],
      block_id: Option[String] = None,
  ) extends Block derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/blocks/input-block
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/InputBlock.java
  case class InputBlock(
      label: TextObject,
      element: BlockElement,
      block_id: Option[String] = None,
      optional: Option[Boolean] = None,
      dispatch_action: Option[Boolean] = None,
      hint: Option[TextObject] = None,
  ) extends Block derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/blocks/header-block
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/HeaderBlock.java
  case class HeaderBlock(
      text: TextObject,
      block_id: Option[String] = None,
  ) extends Block derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/blocks/context-block
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/ContextBlock.java
  case class ContextBlock(
      elements: List[ContextBlockElement],
      block_id: Option[String] = None,
  ) extends Block derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/blocks/divider-block
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/DividerBlock.java
  case class DividerBlock(
      block_id: Option[String] = None,
  ) extends Block derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/blocks/image-block
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/ImageBlock.java
  case class ImageBlock(
      alt_text: String,
      image_url: Option[String] = None,
      slack_file: Option[SlackFileObject] = None,
      title: Option[TextObject] = None,
      block_id: Option[String] = None,
      fallback: Option[String] = None,
      image_width: Option[Int] = None,
      image_height: Option[Int] = None,
      image_bytes: Option[Int] = None,
      is_animated: Option[Boolean] = None,
  ) extends Block derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/blocks/rich-text-block
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/RichTextBlock.java
  case class RichTextBlock(
      elements: List[Json],
      block_id: Option[String] = None,
  ) extends Block derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/blocks/file-block
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/FileBlock.java
  case class FileBlock(
      external_id: String,
      source: Option[String] = None,
      block_id: Option[String] = None,
      file_id: Option[String] = None,
      file: Option[Json] = None,
  ) extends Block derives Codec.AsObject

  // https://docs.slack.dev/reference/block-kit/blocks/video-block
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/block/VideoBlock.java
  case class VideoBlock(
      alt_text: String,
      video_url: String,
      thumbnail_url: String,
      title: TextObject,
      title_url: Option[String] = None,
      description: Option[TextObject] = None,
      author_name: Option[String] = None,
      provider_name: Option[String] = None,
      provider_icon_url: Option[String] = None,
      block_id: Option[String] = None,
  ) extends Block derives Codec.AsObject

  case class UnknownBlock(raw: Json) extends Block

  private val blockTypeMap: Map[String, Decoder[Block]] = Map(
    "section"   -> Decoder[SectionBlock].map(identity[Block]),
    "actions"   -> Decoder[ActionsBlock].map(identity[Block]),
    "input"     -> Decoder[InputBlock].map(identity[Block]),
    "header"    -> Decoder[HeaderBlock].map(identity[Block]),
    "context"   -> Decoder[ContextBlock].map(identity[Block]),
    "divider"   -> Decoder[DividerBlock].map(identity[Block]),
    "image"     -> Decoder[ImageBlock].map(identity[Block]),
    "rich_text" -> Decoder[RichTextBlock].map(identity[Block]),
    "file"      -> Decoder[FileBlock].map(identity[Block]),
    "video"     -> Decoder[VideoBlock].map(identity[Block]),
  )

  private def blockTypeName(block: Block): String = block match {
    case _: SectionBlock  => "section"
    case _: ActionsBlock  => "actions"
    case _: InputBlock    => "input"
    case _: HeaderBlock   => "header"
    case _: ContextBlock  => "context"
    case _: DividerBlock  => "divider"
    case _: ImageBlock    => "image"
    case _: RichTextBlock => "rich_text"
    case _: FileBlock     => "file"
    case _: VideoBlock    => "video"
    case _: UnknownBlock  => ""
  }

  given Encoder.AsObject[Block] = Encoder.AsObject.instance {
    case b: UnknownBlock =>
      b.raw.asObject.getOrElse(JsonObject.empty)
    case block =>
      val base = block match {
        case b: SectionBlock  => Encoder.AsObject[SectionBlock].encodeObject(b)
        case b: ActionsBlock  => Encoder.AsObject[ActionsBlock].encodeObject(b)
        case b: InputBlock    => Encoder.AsObject[InputBlock].encodeObject(b)
        case b: HeaderBlock   => Encoder.AsObject[HeaderBlock].encodeObject(b)
        case b: ContextBlock  => Encoder.AsObject[ContextBlock].encodeObject(b)
        case b: DividerBlock  => Encoder.AsObject[DividerBlock].encodeObject(b)
        case b: ImageBlock    => Encoder.AsObject[ImageBlock].encodeObject(b)
        case b: RichTextBlock => Encoder.AsObject[RichTextBlock].encodeObject(b)
        case b: FileBlock     => Encoder.AsObject[FileBlock].encodeObject(b)
        case b: VideoBlock    => Encoder.AsObject[VideoBlock].encodeObject(b)
        case _: UnknownBlock  => JsonObject.empty
      }
      ("type" -> Json.fromString(blockTypeName(block))) +: base
  }

  given Decoder[Block] = Decoder.instance { cursor =>
    cursor.get[String]("type").flatMap { tpe =>
      blockTypeMap.get(tpe) match {
        case Some(decoder) => decoder(cursor)
        case None          => cursor.as[Json].map(UnknownBlock(_))
      }
    }
  }

  // --- Views ---

  // https://docs.slack.dev/reference/views/modal-views
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/view/View.java
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
