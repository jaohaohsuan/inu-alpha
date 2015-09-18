package protocol.storedQuery

/**
 * Created by henry on 9/18/15.
 */
case class NamedBoolClause(storedQueryId: String, title: String, occurrence: String, clauses: Map[Int, BoolClause] = Map.empty) extends BoolClause
