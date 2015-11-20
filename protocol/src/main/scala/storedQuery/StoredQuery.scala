package protocol.storedQuery

case class StoredQuery(id: String = "", title: String = "", clauses: Map[Int, BoolClause] = Map.empty, tags: Set[String] = Set.empty)