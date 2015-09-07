package seed

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.persistence.journal.leveldb.SharedLeveldbJournal

case object LeveldbStoreRegistration
case object SetSharedLeveldbJournalAck

trait SharedLeveldbStoreUsage extends Actor with ActorLogging {

  var storeRefs = IndexedSeq.empty[ActorRef]

  def registration: Receive = {
    case LeveldbStoreRegistration if !storeRefs.contains(sender()) =>
      storeRefs = storeRefs :+ sender()
      SharedLeveldbJournal.setStore(sender(), context.system)
      log.info("Successfully set SharedLeveldbJournal({})", sender())
      self ! SetSharedLeveldbJournalAck
  }
}
