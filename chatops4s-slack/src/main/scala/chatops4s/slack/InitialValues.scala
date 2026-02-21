package chatops4s.slack

import scala.quoted.*

class InitialValues[T] private[slack] (private[slack] val toMap: Map[String, Any]) {
  inline def set[V](inline selector: T => V, value: V): InitialValues[T] =
    InitialValues.create[T](toMap + (InitialValues.fieldName[T, V](selector) -> value))
}

object InitialValues {
  def of[T]: InitialValues[T] = new InitialValues[T](Map.empty)

  def create[T](map: Map[String, Any]): InitialValues[T] = new InitialValues[T](map)

  inline def fieldName[T, V](inline selector: T => V): String =
    ${ fieldNameImpl[T, V]('selector) }

  private def fieldNameImpl[T: Type, V: Type](selector: Expr[T => V])(using Quotes): Expr[String] = {
    import quotes.reflect.*

    def extractName(term: Term): String = term match {
      case Inlined(_, _, inner) => extractName(inner)
      case Block(List(DefDef(_, _, _, Some(body))), _) => extractFromBody(body)
      case _ => report.errorAndAbort("Expected a simple field selector like _.fieldName")
    }

    def extractFromBody(term: Term): String = term match {
      case Select(_, name) => name
      case Inlined(_, _, inner) => extractFromBody(inner)
      case _ => report.errorAndAbort("Expected a simple field selector like _.fieldName")
    }

    Expr(extractName(selector.asTerm))
  }
}
