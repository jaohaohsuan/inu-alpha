package protocol.storedQuery

import protocol.elastics.boolQuery.OccurrenceRegex

/**
 * Created by henry on 9/18/15.
 */

trait BoolClause {
  val occurrence: String

  lazy val shortName = {
    this match {
      case c: MatchClause => "match"
      case c: SpanNearClause => "near"
      case c: NamedClause => "named"
      case _ => "unknown"
    }
  }
}

object BoolClause {

  def unapply(arg: BoolClause): Option[String] = Some(arg.occurrence)

  def singleField(field: String) = """\s+""".r.findFirstIn(field).isEmpty
  def queryFieldConstrain(field: String) = field.matches("""^dialogs$|^agent\*$|^customer\*$""")
}

import BoolClause._

case class NamedClause(storedQueryId: String, storedQueryTitle: String, occurrence: String, clauses: Map[Int,BoolClause] = Map.empty) extends BoolClause {
  require(test)
  def test = occurrence.matches(OccurrenceRegex.toString())
}

case class MatchClause(query: String, field: String, operator: String, occurrence: String) extends BoolClause {
  require(test)
  require(singleField(field), s"single field only")
  require(queryFieldConstrain(field), s"field only can be 'dialogs' or 'agent*' or 'customer*'")
  def test = operator.matches("^[oO][rR]$|^[Aa][Nn][Dd]$") && occurrence.matches(OccurrenceRegex.toString()) && !query.trim.isEmpty

}

case class SpanNearClause(query: String, field: String, slop: Int, inOrder: Boolean, occurrence: String) extends BoolClause {

  val matchSubSpanMinimumSize: Boolean = 1 < query.trim.split("""\s+""").size

  require(matchSubSpanMinimumSize, s"""The query must contain at least two words""")
  require(test)
  require(field.nonEmpty)
  require(singleField(field), s"single field only")
  require(queryFieldConstrain(field), s"field only can be 'dialogs' or 'agent*' or 'customer*'")
  def test = occurrence.matches(OccurrenceRegex.toString())

  lazy val fields: Seq[String] = {
    """(agent|customer)""".r.findFirstIn(field) match {
      case Some(prefix) =>  (0 to 2).map { n => s"$prefix$n" }
      case None => Seq("dialogs")
    }
  }
}

case object SearchTags

case class StoredQueryItem(title: String, tags: Option[String]) {
  require( title.nonEmpty )
}




