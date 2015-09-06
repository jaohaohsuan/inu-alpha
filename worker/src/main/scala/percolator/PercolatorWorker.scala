package worker.percolator

import akka.actor.{Actor, ActorRef}
import akka.cluster.client.ClusterClient
import common.ImplicitActorLogging
import org.elasticsearch.indices.IndexMissingException
import org.elasticsearch.transport.RemoteTransportException
import protocol.storedQuery._
import protocol.elastics.indices._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

class PercolatorWorker(clusterClient: ActorRef) extends Actor with ImplicitActorLogging {

  import AggregateRootClient._
  import context.dispatcher
  import worker.elastics

  val pullingTask = context.system.scheduler.schedule(5.seconds, 5.seconds, clusterClient, PullChanges)

  override def postStop(): Unit = pullingTask.cancel()

  def receive: Receive = {
    case Changes(items) =>
      import com.sksamuel.elastic4s.ElasticDsl._
      val f = Future.traverse(items) {
        case (Percolator(percolatorId, boolQuery, map), version) =>
          elastics.client.execute {
            register id percolatorId into percolator query boolQuery fields map
          } map { resp => (percolatorId, version) }
      }
      f onSuccess {
        case changes: Set[(String, Int)] => clusterClient ! SendToAllRegisterQueryOK(changes)
      }
      f onFailure {
        case error =>
          recursive[IndexMissingException](error) match {
            case Some(_) => log.error(s"IndexMissingException: ${error.getMessage}")
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
}
