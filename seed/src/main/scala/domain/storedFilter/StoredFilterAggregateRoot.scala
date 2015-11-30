package domain.storedFilter

import akka.actor.ActorRef
import akka.persistence.{SnapshotOffer, PersistentActor}
import common.{ImplicitLogging, ImplicitActorLogging}
import org.json4s.JsonAST.JObject
import protocol.storedFilter._

import domain._

object StoredFilterAggregateRoot {

  // acks
  case class ClauseAddedAck(clauseId: String)
  /*case object UpdatedAck
  case object ClausesRemovedAck
  case object ClausesEmptyAck*/

  //commands
  case class CreateNewStoredFilter(typ: String, title: String) extends Command
  object NewStoredFilter {
    def unapply(x: CreateNewStoredFilter)= Some(x.typ, x.title)
  }

  case class AddClause[T <: BoolClause](filterId: String, typ: String, value: T) extends Command

  //case class FilterUpdated extends Event

  //events
  case class ItemCreated(id: String, typ: String, title: String) extends Event
  case class ItemUpdated(id: String, typ: String, entity: StoredFilter) extends Event

  case class StoredFilters(items: Map[String, StoredFilter] = Map.empty) extends State with ImplicitLogging {

    lazy val newItemId: String = {
      def generateNewItemId: String = {
        val id = scala.math.abs(scala.util.Random.nextInt()).toString
        if (items.keys.exists(_ == id)) generateNewItemId else id
      }
      generateNewItemId
    }

    def update(event: Event): StoredFilters = {
      //s"${event.getClass.getName.replaceAll("\\$", ".")} event has been updated".logInfo()
      event match {
        case ItemCreated(id, typ, title) =>
          copy(items = items + (id -> StoredFilter(typ, title)))
        case ItemUpdated(id, typ, entity) =>
          copy(items = items + (id -> entity))
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
      def doPersistence[A <: Event](evt: Event) = {
        def afterPersisted(`sender`: ActorRef, evt: Event) = {
          state = state.update(evt)
          `sender` ! evt
        }
        persist(evt)(afterPersisted(sender(), _))
      }

      doPersistence(ItemCreated(state.newItemId, typ, title))

    case AddClause(filterId, typ, clause) =>
      def doPersistence(evt: Event, ack: ClauseAddedAck) = {
        def afterPersisted(`sender`: ActorRef, evt: Event) = {
          state = state.update(evt)
          `sender` ! ack
        }
        persist(evt)(afterPersisted(sender(), _))
        ack
      }
      val result = for {
        s@StoredFilter(source, _, _) <- state.items.get(filterId)
        if source == typ
      } yield doPersistence(ItemUpdated(filterId, typ, s.addClauses(clause)), ClauseAddedAck(s.newClauseKey))

      result.logInfo()
  }

  val receiveRecover: Receive = {
    case evt: Event =>
      state = state.update(evt)
    case SnapshotOffer(_, snapshot: State) =>
  }
}
