package read.storedQuery

import akka.NotUsed
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.Source
import domain.StoredQueryRepoAggRoot
import domain.StoredQueryRepoAggRoot.StoredQueries2
import protocol.storedQuery._
import domain.algorithm.TopologicalSort._

/**
  * Created by henry on 4/30/16.
  */
class StoredQueriesView extends read.MaterializeView {

  val source: Source[EventEnvelope, NotUsed] = readJournal.eventsByPersistenceId(StoredQueryRepoAggRoot.persistenceId, 0, Long.MaxValue)

  source.scan(StoredQueries2()){ (acc, el) =>
    acc.update(el.event)
  }

  def materialize(entity: StoredQuery)(implicit repo: Map[String, StoredQuery]): StoredQuery =
    entity.clauses.foldLeft(entity) { case (acc, (k, v)) =>
      v match {
        case clause@NamedBoolClause(refId, _, _ , _) =>
          acc.copy(clauses = acc.clauses + (k -> clause.copy(clauses = materialize(repo(refId)).clauses)))
        case _ => acc
      }
    }




  /*def cascadingUpdate(from: String) = {

    import domain.algorithm.TopologicalSort

    //def withVer(id: String) = id -> state.changes(id)

    val paths = domain.algorithm.TopologicalSort.collectPaths(from)(TopologicalSort.toPredecessor(state.dependencies.keys))
    val (latestItems, changes) = paths.flatten.foldLeft((state.items, Map(withVer(from)))) { case ((repo, changes), (provider: String, consumer: String)) =>

      val clauseId = state.dependencies(consumer, provider) // invert path
    val updatedClause = clauseId -> repo(consumer).clauses(clauseId).asInstanceOf[NamedBoolClause].copy(clauses = repo(provider).clauses)
      val updatedItem = consumer -> repo(consumer).copy(clauses = repo(consumer).clauses + updatedClause)

      (repo + updatedItem, changes + withVer(consumer))
    }
    //ItemsChanged(changes.map { case (id, _) => id -> latestItems(id).materialize(latestItems) }.toSeq, changes, state.dependencies)
  }*/

  def receive: Receive = {
    case _ =>
  }
}
