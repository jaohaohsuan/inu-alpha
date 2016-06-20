package com.inu.protocol.storedquery.messages

case class StoredQuery(id: String = "", title: String = "", clauses: Map[Int, BoolClause] = Map.empty, tags: Set[String] = Set.empty)

case class StoredQueries(items: Map[String, StoredQuery] = Map.empty,
                         paths: Map[(String, String), Int] = Map.empty,
                         changes: List[List[String]] = Nil) {
  lazy val newItemId = {
    def generateNewItemId: String = {
      val id = scala.math.abs(scala.util.Random.nextInt()).toString
      if (items.keys.exists(_ == id)) generateNewItemId else id
    }
    generateNewItemId
  }
}

trait BoolClause extends Serializable {
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

  val Occurrences = Seq("must", "must_not", "should")
  val OccurrenceRegex = """^must$|^must_not$|^should$""".r

  def unapply(arg: BoolClause): Option[String] = Some(arg.occurrence)

  def singleField(field: String) = """\s+""".r.findFirstIn(field).isEmpty
  def queryFieldConstrain(field: String) = field.matches("""^dialogs$|^agent\*$|^customer\*$""")
}

import BoolClause._

case class NamedClause(storedQueryId: String, storedQueryTitle: String, occurrence: String, clauses: Option[Map[Int,BoolClause]] = None) extends BoolClause {
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

//case object SearchTags
//
//case class StoredQueryItem(title: String, tags: Option[String]) {
//  require( title.nonEmpty )
//}




