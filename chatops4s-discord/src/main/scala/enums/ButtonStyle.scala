package enums

enum ButtonStyle(val value: Int) {
  case Primary extends ButtonStyle(1)
  case Secondary extends ButtonStyle(2)
  case Success extends ButtonStyle(3)
  case Danger extends ButtonStyle(4)
  case Link extends ButtonStyle(5)
  case Premium extends ButtonStyle(6)
}