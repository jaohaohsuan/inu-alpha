package domain.storedQuery

import akka.actor._
import akka.persistence._
import common.ImplicitActorLogging
import protocol.storedQuery._
import domain.algorithm.TopologicalSort
import protocol.elastics.boolQuery.OccurrenceRegex
import domain._

import scala.util.{Failure, Success, Try}


object StoredQueryAggregateRoot {

  type ClauseDependencies = Map[(String, String), Int]

  type StoredQueryMap = Map[String, StoredQuery]

  val temporaryId: String = "temporary"

  // acks
  case class ClauseAddedAck(clauseId: String)
  case object UpdatedAck
  case object ClausesRemovedAck
  case object ClausesEmptyAck

  //events
  case class ItemCreated(entity: StoredQuery, dependencies: ClauseDependencies) extends Event
  case class ItemsChanged(items: Seq[(String, StoredQuery)], changes: List[String], dp: ClauseDependencies) extends Event {
  }
  case class ChangesRegistered(records: Set[(String, Int)]) extends Event

  //commands
  case class AddClause(storedQueryId: String, clause: BoolClause) extends Command
  case class UpdateTags(storedQueryId: String, tags: Set[String])
  case class RemoveClauses(storedQueryId: String, specified: List[Int]) extends Command
  case class CreateNewStoredQuery(title: String, referredId: Option[String], tags: Set[String]) extends Command
  case object Initial extends Command
  case class UpdateStoredQuery(storedQueryId: String, title: String, tags: Option[String]) extends Command
  case class ResetOccurrence(storedQueryId: String, occurrence: String) extends Command {
    require(test)
    def test = occurrence.matches(OccurrenceRegex.toString())
  }

  //errors
  case object CycleInDirectedGraphError

  //states
  case class StoredQueries(items: StoredQueryMap = Map.empty,
                           clauseDependencies: ClauseDependencies = Map.empty, changes: Map[String, Int] = Map.empty) extends State {

    lazy val newItemId = {
      def generateNewItemId: String = {
        val id = scala.math.abs(scala.util.Random.nextInt()).toString
        if (items.keys.exists(_ == id)) generateNewItemId else id
      }
      generateNewItemId
    }

    def acyclicProofing(consumer: String, provider: String, clauseId: Int): Option[ClauseDependencies] = {
      import TopologicalSort._
      val source = clauseDependencies + ((consumer, provider) -> clauseId)
      Try(sort(toPredecessor(source.keys))) match {
        case Success(_) => Some(source)
        case Failure(_) => None
      }
    }

    def newItem(title: String, origin: Option[StoredQuery] = None) = origin.getOrElse(StoredQuery()).copy(id = newItemId, title = title)

    def generateNewClauseId(item: StoredQuery): Int = {
      val id = scala.math.abs(scala.util.Random.nextInt())
      if (item.clauses.keys.exists(_ == id)) generateNewClauseId(item) else id
    }

    def getItem(id: String): Option[StoredQuery] = items.get(id)

    def update(event: Event): StoredQueries = {
      def escalateVer(id: String) = id -> (changes.getOrElse(id, 0) + 1)
      event match {
        case ItemCreated(entity, dp) =>
          copy(items   = items   + (entity.id -> entity), clauseDependencies = dp,
               changes = changes + escalateVer(entity.id))
        case ItemsChanged(xs, changesList, dp) =>
          copy(items   = items   ++ xs, clauseDependencies = dp,
               changes = changes ++ changesList.map(escalateVer))
        case ChangesRegistered(syncedRecords) =>
          copy(changes = changes.toSet.diff(syncedRecords).toMap)
      }
    }
  }
}

class StoredQueryAggregateRoot extends PersistentActor with ImplicitActorLogging {

  import StoredQueryAggregateRoot._

  val persistenceId: String = NameOfAggregate.root.name

  var state: StoredQueries = StoredQueries()

  log.info("StoredQueryAggregateRoot established")

  val receiveCommand: Receive = {

    case Initial if !state.items.contains(temporaryId) =>
      def afterPersisted(`sender`: ActorRef, evt: ItemCreated) = {
        state = state.update(evt)
        log.info(s"Add temporary storedQuery")
      }
      persist(ItemCreated(StoredQuery(temporaryId, "temporary"), Map.empty))(afterPersisted(sender(), _))

    case CreateNewStoredQuery(title, referredId, tags) =>
      log.info("Incoming command: CreateNewStoredQuery")
      def doPersist(entity: StoredQuery) = {
        val itemCreated = ItemCreated(entity.copy(tags = tags), state.clauseDependencies ++ entity.clauses.flatMap {
          case (k, v: NamedBoolClause) => Some((entity.id, v.storedQueryId) -> k)
          case (k, v) => None
        })
        def afterPersisted(`sender`: ActorRef, evt: ItemCreated) = {
          state = state.update(evt)
          `sender` ! evt
        }
        persist(itemCreated)(afterPersisted(sender(), _))
      }

      referredId.map { state.getItem(_).map { e => state.newItem(title, Some(e)) } } match {
        case Some(Some(newItem)) => doPersist(newItem)
        case Some(None) => sender() ! s"$referredId is not exist."
        case None => doPersist(state.newItem(title))
      }

    case AddClause(storedQueryId, clause) =>
      state.getItem(storedQueryId) match {
        case Some(item) =>
          val newClauseId: Int = state.generateNewClauseId(item)
          val zero = state.items + (storedQueryId -> item.copy(clauses = item.clauses + (newClauseId -> clause)))

          def afterPersisted(`sender`: ActorRef, evt: ItemsChanged) = {
            state = state.update(evt)
            `sender` ! ClauseAddedAck(s"$newClauseId")
          }

          clause match {
            case NamedBoolClause(clauseStoredQueryId, title, _, _) =>
              state.acyclicProofing(storedQueryId, clauseStoredQueryId, newClauseId) match {
                case Some(dp) =>
                  persist(cascadingUpdate(storedQueryId, zero, dp))(afterPersisted(sender(), _))
                case None =>
                  sender() ! CycleInDirectedGraphError
              }
            case _ =>
              persist(cascadingUpdate(storedQueryId, zero, state.clauseDependencies))(afterPersisted(sender(), _))
          }
        case None =>
          sender() ! s"$storedQueryId is not exist."
      }

    case ResetOccurrence(storedQueryId, occurrence) =>
      state.getItem(storedQueryId)match {
        case Some(item)=>
          item.clauses.filter{ case (_,c) => c.occurrence == occurrence }.keys.toList match {
            case Nil =>
              sender() ! ClausesEmptyAck
            case xs =>
              self forward RemoveClauses(storedQueryId, xs)
          }
        case None =>
          sender() ! s"$storedQueryId is not exist."
      }

    case RemoveClauses(storedQueryId, specified) if specified.isEmpty =>
      sender() ! ClausesEmptyAck

    case RemoveClauses(storedQueryId, specified) =>
      state.getItem(storedQueryId) match {
        case Some(item) =>

          def afterPersisted(`sender`: ActorRef, evt: ItemsChanged) = {
            state = state.update(evt)
            `sender` ! ClausesRemovedAck
          }

          val xs = item.clauses.flatMap {
            case (k, v: NamedBoolClause) if specified.contains(k) => Some((storedQueryId, v.storedQueryId))
            case (k, v) => None
          }

          val zero = state.items + (storedQueryId -> item.copy(clauses = item.clauses -- specified))

          persist(cascadingUpdate(storedQueryId, zero, state.clauseDependencies -- xs))(afterPersisted(sender(), _))

        case None =>
          sender() ! s"$storedQueryId is not exist."
      }

    case UpdateStoredQuery(storedQueryId, newTitle, newTags) =>
      state.getItem(storedQueryId) match {
        case Some(origin) =>
          def afterPersisted(`sender`: ActorRef, evt: ItemsChanged) = {
            state = state.update(evt)
            `sender` ! UpdatedAck
          }
          val itemsChanged = storedQueryId -> origin.copy(title = newTitle, tags = newTags.map { _.split("""\s+""").toSet }.getOrElse(origin.tags)) match {
            case updated @ (_, StoredQuery(_, title, _ , _)) if origin.title != title =>
              cascadingUpdate(storedQueryId, state.items + updated, state.clauseDependencies)
            case updated =>
              ItemsChanged(Seq(updated), List(storedQueryId), state.clauseDependencies)
          }
          persist(itemsChanged)(afterPersisted(sender(), _))

        case None => log.error(s"$newTitle#$storedQueryId does not exist")
      }
  }

  private def cascadingUpdate(from: String, items: StoredQueryMap, dp: ClauseDependencies): ItemsChanged = {
    val zero = (items, List(from))
    val (updatedItems, changesList) = TopologicalSort.collectPaths(from)(TopologicalSort.toPredecessor(dp.keys)).flatten.foldLeft(zero) { case (acc, (provider: String, consumer: String)) =>
      val (accItems, changes) = acc
      val clauseId = dp (consumer, provider)
      val updatedNamedBoolClause = accItems(consumer).clauses(clauseId).asInstanceOf[NamedBoolClause]
        .copy(clauses = accItems(provider).clauses)
      val updatedConsumer = accItems(consumer).copy(clauses = accItems(consumer).clauses + (clauseId -> updatedNamedBoolClause))
      (accItems + (consumer -> updatedConsumer), consumer :: changes)
    }
    ItemsChanged(updatedItems.toSeq, changesList, dp)
  }

  val receiveRecover: Receive = {
    case evt: Event =>
      state = state.update(evt)
    case SnapshotOffer(_, snapshot: State) =>
  }
}
