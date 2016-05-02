package read.storedQuery

import akka.NotUsed
import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.persistence.query.EventEnvelope
import akka.stream.ClosedShape
import akka.stream.scaladsl._
import domain.StoredQueryRepoAggRoot
import domain.StoredQueryRepoAggRoot.StoredQueries2
import protocol.storedQuery._

import scala.language.implicitConversions

object StoredQueriesView {

  def props = Props[StoredQueriesView]
}

class StoredQueriesView extends read.MaterializeView {

  val source:  Source[EventEnvelope, NotUsed]  = readJournal.eventsByPersistenceId("storedq-agg", 0, Long.MaxValue)

  val states  = Flow[EventEnvelope].scan(StoredQueries2()){ (acc, el) => acc.update(el.event) }
  val changes = Flow[StoredQueries2].mapConcat { case StoredQueries2(items, _, x :: xs) => x.flatMap(items.get) }



/*  val docs: Sink[StoredQuery, Unit] = ???

  val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._
    val bcast = b.add(Broadcast[StoredQuery](2))

    changes ~> bcast.in
    bcast.out(0) ~> Flow[StoredQuery] ~> percolator
    bcast.out(1) ~> Flow[StoredQuery] ~> docs
    ClosedShape
  })
  */

  def receive: Receive = {
    case _ =>

      //source.via(states).via(changes)
      //repo.runWith(Sink.foreach())
  }
}
