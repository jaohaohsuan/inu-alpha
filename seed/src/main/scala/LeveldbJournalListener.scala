package seed

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import scala.concurrent.Future
import scala.util.{Failure, Success}

class LeveldbJournalListener extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) =>
      registerLeveldbStore(member)
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}", member.address, previousStatus)
    case _: MemberEvent =>
  }

  def registerLeveldbStore(m: akka.cluster.Member) = {

    import akka.util.Timeout
    import context.dispatcher
    import context.system

    import scala.concurrent.duration._
    implicit val timeout = Timeout(5.seconds)

    system.actorSelection("/user/store").resolveOne.recoverWith {
      case ex: akka.actor.ActorNotFound if m.hasRole("store") =>
        // Start the shared journal on one node (don't crash this SPOF)
        Future { system.actorOf(Props[SharedLeveldbStore], "store") }
    }.onComplete {
        case Success(ref) =>
          log.info(s"Sending $ref to ${m.address}")
          context.actorSelection(RootActorPath(m.address) / "user" / "conf").tell(LeveldbStoreRegistration(m), ref)
        case Failure(ex) =>
          log.error(ex, "Can not resolve /user/store")
      }
  }
}
