package protocol.storedFilter

/**
 * Created by henry on 11/12/15.
 */
object NameOfAggregate {

  val root = protocol.clustering.Name("stored-filter-aggregate-root", "stored-filter-aggregate-root-proxy")

  val view = protocol.clustering.Name("stored-filter-aggregate-root-view", "stored-filter-aggregate-root-view-proxy")

}
