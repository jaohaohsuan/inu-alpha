package worker.percolator

import akka.actor.{Actor, ActorRef}
import common.ImplicitActorLogging
import org.elasticsearch.indices.IndexMissingException
import org.elasticsearch.transport.RemoteTransportException
import protocol.elastics.indices._
import protocol.storedQuery._

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
    case c: Changes =>
      val f = beginRegister(c) map { changes =>
        clusterClient ! SendToAllRegisterQueryOK(changes)
      }

      f onSuccess { case _ => log.info(s"StoredQuery successfully sync to percolator") }

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

  private def recursive[A<: Throwable: ClassTag](exception: Throwable): Option[A] =
    exception match {
      case e: RemoteTransportException => recursive(e.getCause)
      case e: A => Some(e)
      case _ => None
    }

  def beginRegister(changes: Changes): Future[Set[(String, Int)]] = {
    import com.sksamuel.elastic4s.ElasticDsl._
    Future.traverse(changes.items) {
      case (Percolator(percolatorId, boolQuery, map), version) =>
        elastics.client.execute {
          register id percolatorId into percolator query boolQuery fields map
        } map { resp => (percolatorId, version) }
    }
  }
}
