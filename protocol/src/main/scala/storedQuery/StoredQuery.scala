package protocol.storedQuery

/**
 * Created by henry on 9/18/15.
 */
case class StoredQuery(id: String = "", title: String = "", clauses: Map[Int, BoolClause] = Map.empty, tags: Set[String] = Set.empty)
