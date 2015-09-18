package protocol.storedQuery

/**
 * Created by henry on 9/18/15.
 */
case class MatchBoolClause(query: String, field: String, operator: String, occurrence: String) extends Unallied
