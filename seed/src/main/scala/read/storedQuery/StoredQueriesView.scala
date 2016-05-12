package read.storedQuery

import akka.NotUsed
import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.persistence.query.EventEnvelope
import akka.stream.ClosedShape
import akka.stream.javadsl.RunnableGraph
import akka.stream.scaladsl._
import domain.StoredQueryRepoAggRoot
import domain.StoredQueryRepoAggRoot.StoredQueries2
import org.json4s.JValue
import protocol.storedQuery._

import scala.concurrent.Future
import scala.language.implicitConversions

object StoredQueriesView {

  def props = Props[StoredQueriesView]
}

class StoredQueriesView extends read.MaterializeView with PercolatorWriter {

  val source:  Source[EventEnvelope, NotUsed]  = readJournal.eventsByPersistenceId("storedq-agg", 0, Long.MaxValue)

  val states  = Flow[EventEnvelope].scan(StoredQueries2()){ (acc, el) => acc.update(el.event) }.filter({
    case StoredQueries2(_, _, Nil) => false
    case _ => true
  })
  val changes = Flow[StoredQueries2].mapConcat { case StoredQueries2(items, _, x :: xs) => x.flatMap(items.get) }

  val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] = Http().outgoingConnection("elasticsearch", 9200)

  val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._

    val out = Sink.ignore

    val bcast = builder.add(Broadcast[StoredQuery](2))
    val merge = builder.add(Merge[JValue](2))

    source ~> states ~> changes ~> bcast ~> query ~> merge ~> out
                                   bcast ~> keywords

    ClosedShape
  })

  def receive: Receive = {
    case _ =>
     // source.via(states).via(changes).via(percolators).runWith(Sink.foreach(println))
//     source.via(states)
//            .via(changes)
//            .via(marshalling)
//            .via(persistence)
//            .via(connectionFlow)
//            .runForeach{ r => println(s"$r") }

      //source.via(states).via(changes)
      //repo.runWith(Sink.foreach())
  }
}
