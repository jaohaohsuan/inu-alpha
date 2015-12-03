package domain.storedFilter

import akka.actor.ActorRef
import akka.persistence.{PersistentActor, SnapshotOffer}
import common.{ImplicitActorLogging, ImplicitLogging}
import domain._
import protocol.storedFilter._

object StoredFilterAggregateRoot {

  // acks
  case class ClauseAddedAck(clauseId: String)
  case object NoContentAck
  case object ClausesRemovedAck
  case object ClausesEmptyAck
  case object UpdatedAck
  case class ItemCreatedAck(filterId: String)
  case object ItemDeletedAck

  //commands
  case class CreateNewStoredFilter(typ: String, title: String, referredId: Option[String] = None) extends Command
  case class DeleteItem(filterId: String, typ: String) extends Command
  case class Rename(filter: String, name: String) extends Command
  case class RemoveClause(filterId: String, typ: String, clauseId: String) extends Command
  case class EmptyClauses(filterId: String, typ: String, occur: String) extends Command
  case class AddClause[T <: BoolClause](filterId: String, typ: String, value: T) extends Command

  //case class FilterUpdated extends Event

  //events
  case class ItemCreated(id: String, typ: String, entity: StoredFilter) extends Event
  case class ItemUpdated(id: String, typ: String, entity: StoredFilter) extends Event
  case class ItemDeleted(id: String, typ: String, entity: StoredFilter) extends Event

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
        case ItemCreated(id, _, entity) =>
          copy(items = items + (id -> entity))
        case ItemUpdated(id, _, entity) =>
          copy(items = items + (id -> entity))
        case ItemDeleted(id, _, _) =>
          copy(items = items.-(id))
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

  implicit class Persister1[A <: Event](evt: A) {
    def persistWithAck(f: A => AnyRef) =
      persist(evt)(sender().after(f(evt)))
  }

  implicit class Persister0(`sender`: ActorRef) {
    def after(ack: AnyRef)(evt: Event) = {
      state = state.update(evt)
      `sender` ! ack
    }
  }

  val receiveCommand: Receive = {

    case CreateNewStoredFilter(typ, title, None) =>
      ItemCreated(state.newItemId, typ, StoredFilter(typ, title)).persistWithAck { evt => ItemCreatedAck(evt.id) }

    case CreateNewStoredFilter(typ, title, Some(referredId)) =>
      val maybe = for {
        s@StoredFilter(source, _, _) <- state.items.get(referredId)
        if source == typ
      } yield ItemCreated(state.newItemId, typ, s.copy(title = title))

      maybe match {
        case Some(e) => e.persistWithAck { evt => ItemCreatedAck(evt.id) }
        case None => sender() ! NoContentAck
      }

    case DeleteItem(filterId, typ) =>
      val maybe = for {
        s@StoredFilter(source, _, _) <- state.items.get(filterId)
        if source == typ
      } yield ItemDeleted(filterId, typ, s)

      maybe match {
        case Some(e) => e.persistWithAck { _ => ItemDeletedAck}
        case None => sender() ! ItemDeletedAck
      }

    case AddClause(filterId, typ, clause) =>
      val maybe = for {
        s@StoredFilter(source, _, _) <- state.items.get(filterId)
        if source == typ
      } yield s
      maybe match {
        case Some(s) =>
          ItemUpdated(filterId, typ, s.add(clause)).persistWithAck { _ => ClauseAddedAck(s.newClauseKey) }
        case None =>
          sender() ! NoContentAck
      }

    case RemoveClause(filterId, typ, clauseId) =>
      val entity = for {
        s@StoredFilter(source, _, _) <- state.items.get(filterId)
        if source == typ && s.clauses.contains(clauseId)
      } yield s
      entity match {
        case Some(e) => ItemUpdated(filterId, typ, e.remove(clauseId)).persistWithAck { _ => ClausesRemovedAck}
        case None => sender() ! ClausesRemovedAck
      }

    case EmptyClauses(filterId, typ, occur) =>
      for {
        s@StoredFilter(source, _, _) <- state.items.get(filterId)
        if source == typ && s.clauses.exists { case (k, v) => v.occurrence == occur }
      } yield
        ItemUpdated(
          filterId, typ,
          s.copy(clauses = s.clauses.filter { case (k,v) => v.occurrence != occur })
        ).persistWithAck { _ => ClausesEmptyAck }

    case Rename(filterId, name) =>
      for {
        s@StoredFilter(source, _, _) <- state.items.get(filterId)
      } yield ItemUpdated(filterId, source, s.copy(title = name)).persistWithAck { _ => UpdatedAck }
  }

  val receiveRecover: Receive = {
    case evt: Event =>
      state = state.update(evt)
    case SnapshotOffer(_, snapshot: State) =>
  }
}
