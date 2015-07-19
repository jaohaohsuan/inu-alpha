package domain

import akka.actor.{Actor, ActorRef}
import akka.contrib.pattern.ClusterClient.SendToAll
import com.sksamuel.elastic4s.QueryDefinition
import org.elasticsearch.indices.IndexMissingException
import org.elasticsearch.transport.RemoteTransportException

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

object Percolator {
  import domain.StoredQueryAggregateRoot.StoredQuery

  def unapply(value: AnyRef): Option[(String, QueryDefinition, Map[String, Any])] = try {
    value match {
      case e @ StoredQuery(percolatorId, title, clauses, tags) =>
        val (referredClauses, boolQuery) = e.buildBoolQuery()
        Some((
          percolatorId,
          boolQuery,
          Map("enabled" -> true, "title" -> title, "tags" -> tags.toArray, "referredClauses" -> referredClauses.toArray)
          ))
      case unknown =>
        println(s"$unknown)")
        None
    }
  } catch {
    case ex: Exception =>
      println(s"$ex")
      None
  }
}

class PercolatorWorker(clusterClient: ActorRef, node: Option[org.elasticsearch.node.Node]) extends Actor
  with util.ImplicitActorLogging {

  val client = node.map { com.sksamuel.elastic4s.ElasticClient.fromNode }.getOrElse(
    com.sksamuel.elastic4s.ElasticClient.remote("127.0.0.1", 9300)
  )

  import StoredQueryPercolatorProtocol._
  import context.dispatcher


  val pullingTask = context.system.scheduler.schedule(5.seconds, 5.seconds, clusterClient,
    SendToAll(`/user/stored-query-aggregate-root/active`, Pull))

  override def postStop(): Unit = pullingTask.cancel()

  val processing: Receive = {
    case Changes(items) =>
      import com.sksamuel.elastic4s.ElasticDsl._
      import elastics.PercolatorIndex._

      val f = Future.traverse(items) {
        case (Percolator(percolatorId, boolQuery, map), version) =>
          client.execute {
            register id percolatorId into `inu-percolate` query boolQuery fields map
          } map { resp => (percolatorId, version) }
      }

      f onSuccess {
        case changes: Set[(String, Int)] =>
          clusterClient ! SendToAll(`/user/stored-query-aggregate-root/active`, RegisterQueryOK(changes))
      }

      f onFailure {
        case error =>
          recursive[IndexMissingException](error) match {
            case Some(_) =>
              log.error(s"IndexMissingException: ${error.getMessage}")
            case None =>
              log.error(s"${error.getMessage}, ${error.getClass.getSimpleName}")
              context.stop(self)
          }
      }
  }

  private def recursive[A<: Throwable: ClassTag](exception: Throwable): Option[A] = {
    exception match {
      case e: RemoteTransportException => recursive(e.getCause)
      case e: A => Some(e)
      case _ => None
    }
  }


  def receive: Receive = processing
}
