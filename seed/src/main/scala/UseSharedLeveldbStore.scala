package seed

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.persistence.journal.leveldb.SharedLeveldbJournal

case class LeveldbStoreRegistration(m: akka.cluster.Member)

trait UseSharedLeveldbStore extends Actor with ActorLogging {

  var storeRefs = IndexedSeq.empty[ActorRef]

  def registration: Receive = {
    case m: LeveldbStoreRegistration if !storeRefs.contains(sender()) =>
      storeRefs = storeRefs :+ sender()
      SharedLeveldbJournal.setStore(sender(), context.system)
      log.info("Successfully set SharedLeveldbJournal({})", sender())
      self forward m
  }
}
