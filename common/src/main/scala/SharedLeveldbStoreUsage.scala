package common

import akka.actor.{Actor, ActorLogging}
import akka.persistence.journal.leveldb.SharedLeveldbJournal


/**
 * Created by henry on 9/5/15.
 */
class SharedLeveldbStoreUsage extends Actor with ActorLogging {

  def receive = {
    case LeveldbStoreRegistration =>
      val store = sender()
      SharedLeveldbJournal.setStore(store, context.system)
      log.info("Injecting the (remote {}) SharedLeveldbStore actor reference", store)
  }
}
