package read.storedQuery

import akka.NotUsed
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.{Sink, Source}
import domain.StoredQueryRepoAggRoot
import domain.StoredQueryRepoAggRoot.StoredQueries2
import protocol.storedQuery._
import domain.algorithm.TopologicalSort._

/**
  * Created by henry on 4/30/16.
  */
class StoredQueriesView extends read.MaterializeView {

  val source: Source[EventEnvelope, NotUsed] = readJournal.eventsByPersistenceId(StoredQueryRepoAggRoot.persistenceId, 0, Long.MaxValue)
  val repo = source.scan(StoredQueries2()){ (acc, el) => acc.update(el.event) }



  def receive: Receive = {
    case _ =>
      //repo.runWith(Sink.foreach())
  }
}
