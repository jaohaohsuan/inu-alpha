package com.inu.cluster.storedquery

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, SourceShape}
import com.inu.cluster.storedquery.elasticsearch.PercolatorWriter
import com.inu.protocol.storedquery.messages._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.language.implicitConversions

object StoredQueryRepoView {

  def props = Props[StoredQueryRepoView]
}

class StoredQueryRepoView extends Actor with PercolatorWriter with LazyLogging {

  import StoredQueryRepoAggRoot._
  import akka.http.scaladsl.model.StatusCodes._

  val config = ConfigFactory.load()

  implicit val system: ActorSystem = context.system
  implicit val mat = ActorMaterializer()
  implicit val executor = context.dispatcher


  val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  val address = config.getString("elasticsearch.client-address")
  val port = config.getInt("elasticsearch.client-http")
  val connectionFlow = Http().cachedHostConnectionPool[String](address, port)

  val source = readJournal.eventsByPersistenceId("StoredQueryRepoAggRoot", 0, Long.MaxValue)

  val states = Flow[EventEnvelope].scan(StoredQueries()){
    case (acc, EventEnvelope(_, _, _, evt: Event)) => acc.update(evt)
    case (acc, _) => acc
  }.filter {
    case StoredQueries(_, _, Nil) => false
    case _ => true
  }

  val changes = Flow[StoredQueries].mapConcat { case StoredQueries(items, _, changes@x :: xs) =>

    x.reverse.flatMap(items.get).map(retrieveDependencies(_, items))
    //x.flatMap { e => items.get(e).map { x => retrieveDependencies(x, items) } }
  }

  def retrieveDependencies(item: StoredQuery, items: Map[String, StoredQuery]): StoredQuery =
    item.clauses.foldLeft(item) { (acc, e) =>
      e match {
        case (clauseId, n: NamedClause) =>
          items.get(n.storedQueryId) match {
            case Some(innerItem) => acc.copy(clauses = acc.clauses + (clauseId -> n.copy(clauses = Some(retrieveDependencies(innerItem,items).clauses))))
            case None =>
              logger.warn("{} doesn't exist", n)
              acc
          }
        case _ => acc
      }
    }

  val g = Source.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    import org.json4s.JValue

    val bcast = builder.add(Broadcast[StoredQuery](2))

    val zipW = builder.add(ZipWith[JValue, JValue, JValue](_ merge _))

    source ~> states ~> changes ~> bcast ~> query    ~> zipW.in1
                                   bcast ~> keywords ~> zipW.in0

    SourceShape(zipW.out)
  })

  g.via(put).via(connectionFlow).runWith(Sink.foreach { case (res,id) =>
    res.get.status match {
      case OK => logger.debug("{} -- {}", id, res.get.status)
      case unexpected => logger.error("{} -- {}", id, res.get.entity.toString)
    }
   })

  //Sink.fold(Set.empty[String])


  def receive: Receive = {
    case _ =>

  }
}
