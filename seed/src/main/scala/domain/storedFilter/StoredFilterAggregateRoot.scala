package domain.storedFilter

import akka.actor.ActorRef
import akka.persistence.{SnapshotOffer, PersistentActor}
import common.{ImplicitLogging, ImplicitActorLogging}
import protocol.storedFilter.{NameOfAggregate}
import domain._

object StoredFilterAggregateRoot {

  //commands
  case class CreateNewStoredFilter(typ: String, title: String) extends Command
  object NewStoredFilter {
    def unapply(x: CreateNewStoredFilter)= Some(x.typ, x.title)
  }


  //events
  case class ItemCreated(id: String, typ: String, title: String) extends Event

  case class StoredFilters(items: Map[String, String] = Map.empty) extends State with ImplicitLogging {

    lazy val newItemId: String = {
      def generateNewItemId: String = {
        val id = scala.math.abs(scala.util.Random.nextInt()).toString
        if (items.keys.exists(_ == id)) generateNewItemId else id
      }
      generateNewItemId
    }

    def update(event: Event): StoredFilters = {
      event match {
        case ItemCreated(id, typ, title) =>
          s"Event $event of StoredFilterAggregateRoot has been updated".logInfo()
          copy(items = items + (id -> title))
        case unknown =>
          s"Unknown event '$unknown' were found when updating ${this.getClass.getName} state.".logWarn()
          this
      }
    }
  }

}

class StoredFilterAggregateRoot extends PersistentActor with ImplicitActorLogging  {

  import StoredFilterAggregateRoot._

  val persistenceId: String = NameOfAggregate.root.name

  var state: StoredFilters = StoredFilters()

  val receiveCommand: Receive = {

    case NewStoredFilter(typ, title) =>

      def doPersistence(itemCreated: Event) = {
        def afterPersisted(`sender`: ActorRef, evt: Event) = {
          state = state.update(evt)
          `sender` ! evt
        }
        persist(itemCreated)(afterPersisted(sender(), _))
      }
      doPersistence(ItemCreated(state.newItemId, typ, title))
  }

  val receiveRecover: Receive = {
    case evt: Event =>
      state = state.update(evt)
    case SnapshotOffer(_, snapshot: State) =>
  }
}
