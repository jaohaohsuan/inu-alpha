package domain

import akka.persistence.PersistentActor

class QueryTemplate extends PersistentActor{

  override def receiveRecover: Receive = ???

  override def receiveCommand: Receive = ???

  override def persistenceId: String = ???
}
