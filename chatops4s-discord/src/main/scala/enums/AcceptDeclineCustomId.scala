package enums

import models.InteractionRequest

enum AcceptDeclineCustomId(val value: String):
  case Accept extends AcceptDeclineCustomId("ACCEPT")
  case Decline extends AcceptDeclineCustomId("DECLINE")
