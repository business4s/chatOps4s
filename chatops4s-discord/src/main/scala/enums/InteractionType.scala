package enums

import models.InteractionRequest

enum InteractionType(val value: Int):
  case Ping extends InteractionType(1)
  case Slash extends InteractionType(2)
  case AcceptDecline extends InteractionType(3)
  case Form extends InteractionType(5)
