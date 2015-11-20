package protocol.storedQuery

object NameOfAggregate {

  val view = protocol.clustering.Name("stored-query-aggregate-root-view", "stored-query-aggregate-root-view-proxy")

  val root = protocol.clustering.Name("stored-query-aggregate-root", "stored-query-aggregate-root-proxy")

}