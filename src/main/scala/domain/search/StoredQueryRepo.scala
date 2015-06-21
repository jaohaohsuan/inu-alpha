package domain.search

import akka.actor.{Props, ActorLogging}
import akka.cluster.Cluster
import akka.contrib.pattern.{DistributedPubSubMediator, DistributedPubSubExtension, ClusterReceptionistExtension}
import akka.persistence.{RecoveryCompleted, PersistentActor}
import domain.search.StoredQueryRepo._

object StoredQueryRepo {

  def props: Props = Props[StoredQueryRepo]

  case class Item(storedQueryId: String, name: String, content: String)
  case object Query
  
  case class QueryResponse(items: List[Item])
}

class StoredQueryRepo extends PersistentActor with ActorLogging {

  import StoredQuery.StoredQueryItem
  
  val mediator = DistributedPubSubExtension(context.system).mediator
  mediator ! DistributedPubSubMediator.Subscribe("sortedQueryItem", self)

  ClusterReceptionistExtension(context.system).registerService(self)
  
  var storedQueryItems = Set[Item]()

  override def persistenceId: String = Cluster(context.system).selfRoles.find(_.startsWith("backend-")) match {
    case Some(role) => s"$role-storedQueryRepo"
    case None => "storedQueryRepo"
  }

  override def receiveCommand: Receive = {

    case StoredQueryItem(storedQueryId, name) =>
      log.info(s"StoredQueryItem")
      persist(Item(storedQueryId, name, "")) { event =>
        storedQueryItems = storedQueryItems + event        
      }

    case Query =>
      sender() ! QueryResponse(storedQueryItems.toList)
  }

  override def receiveRecover: Receive = {

    case event: Item =>
      storedQueryItems = storedQueryItems + event

    case RecoveryCompleted =>
      log.info(s"StoredQueryRepo: $storedQueryItems")
  }
}
