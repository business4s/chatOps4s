package enums

enum InteractionType(val value: Int) {
  case Ping extends InteractionType(1)
  case DeferredMessageUpdate extends InteractionType(6)
}