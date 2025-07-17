package enums

enum ContentType(val value: Int) {
  case ActionRow extends ContentType(1)
  case Button    extends ContentType(2)
}
