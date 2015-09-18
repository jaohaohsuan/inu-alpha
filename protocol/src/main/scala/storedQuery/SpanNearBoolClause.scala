package protocol.storedQuery

/**
 * Created by henry on 9/18/15.
 */
case class SpanNearBoolClause(terms: List[String], field: String, slop: Int, inOrder: Boolean, occurrence: String) extends Unallied
