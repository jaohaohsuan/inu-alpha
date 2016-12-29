package com.inu.cluster.storedquery

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.{Backoff, BackoffSupervisor}
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.scaladsl._
import akka.stream.{ActorAttributes, ActorMaterializer, SourceShape, Supervision}
import com.inu.cluster.ElasticsearchExtension
import com.inu.cluster.storedquery.elasticsearch.PercolatorWriter
import com.inu.protocol.storedquery.messages._

import scala.language.implicitConversions
import scala.util.Failure
import scala.concurrent.duration._

object StoredQueryRepoView {

  def props: Props = Props[StoredQueryRepoView]
  def propsWithBackoff = BackoffSupervisor.props(
    Backoff.onStop(
      childProps = StoredQueryRepoView.props,
      childName = "StoredQueryRepoView",
      minBackoff = 3.seconds,
      maxBackoff = 30.seconds,
      randomFactor = 0.2
    ))
}

class StoredQueryRepoView extends Actor with PercolatorWriter with ActorLogging {

  import StoredQueryRepoAggRoot._

  implicit val system: ActorSystem = context.system
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatchers.lookup("view-thread-pool-dispatcher")

  val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  val source = readJournal.eventsByPersistenceId("StoredQueryRepoAggRoot", 0, Long.MaxValue)

  val illegalIdRegx = """[^\w]+""".r

  val states = Flow[EventEnvelope].scan(StoredQueries()){
    case (acc, evl @ EventEnvelope(_,_,_, evt: ItemCreated)) if illegalIdRegx.findFirstIn(evt.id).nonEmpty =>
      log.warning("illegal id found: {}", evl)
      acc
    case (acc, evl @ EventEnvelope(_,_,_, evt: ItemUpdated)) if illegalIdRegx.findFirstIn(evt.id).nonEmpty =>
      log.warning("illegal id found: {}", evl)
      acc
    case (acc, evl @ EventEnvelope(_, _, _, evt: Event)) =>
      log.debug("replaying: {}", evl)
      acc.update(evt)
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
              log.warning("{} doesn't exist", n)
              acc
          }
        case _ => acc
      }
    }

  val decider : Supervision.Decider = { e =>
    log.error(s"Unexpected",e)
    Supervision.restart
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

  //g.runWith(Sink.ignore)
  g.via(put).via(
      ElasticsearchExtension(system).httpConnectionPool
        .withAttributes(ActorAttributes.supervisionStrategy(decider)))
   .runWith(Sink.foreach {
    case (scala.util.Success(res),id) =>
      res.discardEntityBytes(mat).future().onComplete{ _ =>
        res.status match {
          case s if 400 <= s.intValue => log.error("{} ResponseCode:{}", id, s)
          case _ => // no errors
        }
      }
    case (Failure(ex), _) => log.error(ex, "Unable to send request to es")
   })

  def receive: Receive = {
    case _ =>
  }
}
