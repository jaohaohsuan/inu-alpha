/**
 * Created by henry on 9/4/15.
 */
package seed

import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.actor._
import akka.persistence.journal.leveldb.SharedLeveldbStore
import common._

import scala.concurrent.Future
import scala.util.{Failure, Success}

class SimpleClusterListener extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
    classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
      registerLeveldbStore(member)
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}", member.address, previousStatus)
    case _: MemberEvent =>
  }

  def registerLeveldbStore(m: akka.cluster.Member) = {

    import context.dispatcher
    import akka.util.Timeout
    import scala.concurrent.duration._
    implicit val timeout = Timeout(5.seconds)

    // Start the shared journal on one node (don't crash this SPOF)

    val store: Future[ActorRef] =
      if(m.hasRole("store")) Future { context.system.actorOf(Props[SharedLeveldbStore], "store") } else context.actorSelection("/user/store").resolveOne

    store.onComplete {
      case Success(ref) =>
        context.actorSelection(RootActorPath(m.address) / "user" / "conf").tell(LeveldbStoreRegistration, ref)
      case Failure(ex) =>
        log.error(ex, "Can not resolve /user/store")
    }

  }
}
