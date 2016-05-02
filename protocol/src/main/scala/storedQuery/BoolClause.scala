package protocol.storedQuery

sealed trait BoolClause {
  val occurrence: String
}

object BoolClause {

  def unapply(arg: BoolClause): Option[String] = Some(arg.occurrence)

}

sealed trait Unallied extends BoolClause

final case class MatchBoolClause(query: String, field: String, operator: String, occurrence: String) extends Unallied

final case class NamedBoolClause(storedQueryId: String, title: String, occurrence: String, clauses: Map[Int, BoolClause] = Map.empty) extends BoolClause

final case class SpanNearBoolClause(terms: List[String], field: String, slop: Int, inOrder: Boolean, occurrence: String) extends Unallied {

  lazy val fields: Seq[String] = {
    """(agent|customer)""".r.findFirstIn(field) match {
      case Some(prefix) =>  (0 to 2).map { n => s"$prefix$n" }
      case None => Seq("dialogs")
    }
  }

}