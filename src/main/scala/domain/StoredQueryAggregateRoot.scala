package domain

import akka.actor._
import akka.contrib.pattern.ClusterReceptionistExtension
import akka.persistence._
import algorithm.TopologicalSort._
import scala.util.{Failure, Success, Try}

object StoredQueryAggregateRoot {

  val storedQueryAggregateRootSingleton = "/user/stored-query-aggregate-root/active"

  sealed trait Event

  sealed trait State

  sealed trait Command

  case class ClauseAddedAck(clauseId: String)

  case object UpdatedAck

  case object ClausesRemovedAck

  case class ItemCreated(entity: StoredQuery, dependencies: Map[(String, String), Int]) extends Event

  case class ItemsChanged(items: Map[String, StoredQuery], changes: List[String], dependencies: Map[(String, String), Int]) extends Event

  case class ChangesRegistered(records: Set[(String, Int)]) extends Event

  sealed trait BoolClause {
    val occurrence: String
  }

  sealed trait UnalliedBoolClause extends BoolClause

  case class NamedBoolClause(storedQueryId: String,
                             title: String,
                             occurrence: String,
                             clauses: Map[Int, BoolClause] = Map.empty) extends BoolClause

  case class MatchBoolClause(query: String, operator: String, occurrence: String) extends UnalliedBoolClause

  case class SpanNearBoolClause(terms: List[String],
                                slop: Option[Int],
                                inOrder: Boolean, occurrence: String) extends UnalliedBoolClause

  case class AddClause(storedQueryId: String, clause: BoolClause) extends Command

  case class UpdateTags(storedQueryId: String, tags: Set[String])

  case class RemoveClauses(storedQueryId: String, specified: List[Int]) extends Command

  val temporaryId: String = "temporary"

  case class CreateNewStoredQuery(title: String, referredId: String) extends Command

  case class UpdateStoredQuery(storedQueryId: String, title: String, tags: Option[String]) extends Command


  case class StoredQuery(id: String = "", title: String = "", clauses: Map[Int, BoolClause] = Map.empty, tags: Set[String] = Set.empty)

  case object CycleInDirectedGraphError

  case class Active(items: Map[String, StoredQuery] = Map.empty,
                    clausesDependencies: Map[(String, String), Int] = Map.empty,
                    changes: Map[String, Int] = Map.empty) extends State {

    import algorithm.TopologicalSort._

    def CreateAcyclicClauseDependencies(consumer: String, provider: String, clauseId: Int) = {
      val source = clausesDependencies + ((consumer, provider) -> clauseId)
      Try(sort(toPredecessor(source.keys))) match {
        case Success(_) => Some(source)
        case Failure(_) => None
      }
    }

    def generateNewItemId: String = {
      val id = scala.math.abs(scala.util.Random.nextInt()).toString
      if (items.keys.exists(_ == id)) generateNewItemId else id
    }

    def generateNewClauseId(item: StoredQuery): Int = {
      val id = scala.math.abs(scala.util.Random.nextInt())
      if (item.clauses.keys.exists(_ == id)) generateNewClauseId(item) else id
    }

    def getItem(id: String): Option[StoredQuery] = items.get(id)

    def update(event: Event): Active = {

      event match {

        case ItemCreated(entity, dp) =>
          copy(items = items + (entity.id -> entity), clausesDependencies = dp,
            changes = changes + (entity.id -> (changes.getOrElse(entity.id, 0) + 1)))

        case ItemsChanged(xs, changesList, dp) =>
          copy(items = items ++ xs, clausesDependencies = dp, changes =
            changes ++ changesList.map { e => e -> (changes.getOrElse(e, 0) + 1) } - temporaryId)

        case ChangesRegistered(records) =>
          copy(changes = changes.toSet.diff(records).toMap)

      }
    }
  }

}

class StoredQueryAggregateRoot extends PersistentActor with ActorLogging {

  import StoredQueryAggregateRoot._
  import StoredQueryPercolatorProtocol._

  val persistenceId: String = "stored-query-aggregate-root"

  var state: Active = Active(items = Map(temporaryId -> StoredQuery(temporaryId, "temporary")))

  ClusterReceptionistExtension(context.system).registerService(self)

  val receiveCommand: Receive = {

    case CreateNewStoredQuery(title, referredId) =>
      state.getItem(referredId) match {
        case Some(item) =>
          def afterPersisted(`sender`: ActorRef, evt: ItemCreated) = {
            state = state.update(evt)
            `sender` ! evt
          }

          val newItem = item.copy(id = state.generateNewItemId, title = title)

          val itemCreated = ItemCreated(newItem, state.clausesDependencies ++ item.clauses.flatMap {
            case (k, v: NamedBoolClause) => Some((newItem.id, v.storedQueryId) -> k)
            case (k, v) => None
          })

          persist(itemCreated)(afterPersisted(sender(), _))
        case None =>
          sender() ! s"$referredId is not exist."
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
              state.CreateAcyclicClauseDependencies(storedQueryId, clauseStoredQueryId, newClauseId) match {
                case Some(dp) =>
                  persist(cascadingUpdate(storedQueryId, zero, dp))(afterPersisted(sender(), _))
                case None =>
                  sender() ! CycleInDirectedGraphError
              }
            case _ =>
              persist(cascadingUpdate(storedQueryId, zero, state.clausesDependencies))(afterPersisted(sender(), _))
          }
        case None =>
          sender() ! s"$storedQueryId is not exist."
      }

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

          persist(cascadingUpdate(storedQueryId, zero, state.clausesDependencies -- xs))(afterPersisted(sender(), _))

        case None =>
          sender() ! s"$storedQueryId is not exist."
      }

    case UpdateStoredQuery(storedQueryId, newTitle, newTags) =>
      state.getItem(storedQueryId) match {
        case Some(item) =>

          def afterPersisted(`sender`: ActorRef, evt: ItemsChanged) = {
            state = state.update(evt)
            `sender` ! UpdatedAck
          }

          val updateItem = storedQueryId -> item.copy(
            title = newTitle,
            tags = newTags.map {
              _.split(" ").toSet
            }.getOrElse(item.tags))
          val itemsChanged = if (item.title != newTitle) cascadingUpdate(storedQueryId, state.items + updateItem, state.clausesDependencies) else ItemsChanged(Map(updateItem), List(storedQueryId), state.clausesDependencies)

          persist(itemsChanged)(afterPersisted(sender(), _))
        case None =>
      }


    case Pull =>
      val items = (state.changes - temporaryId).map { case (k, v) => (state.items(k), v) }.toSet
      if (items.nonEmpty)
        sender() ! Changes(items)

    case RegisterQueryOK(records) =>
      persist(ChangesRegistered(records)) { evt =>
        state = state.update(evt)
        log.info(s"remains: [${state.changes.mkString(",")}]")
      }
  }

  def cascadingUpdate(from: String, items: Map[String, StoredQuery], dp: Map[(String, String), Int]) = {

    val zero = (items, List(from))

    val (updatedItems, changesList) = collectPaths(from)(toPredecessor(dp.keys)).flatten.foldLeft(zero) { (acc, link) => {
      val (provider, consumer) = link
      val (accItems, changes) = acc
      val clauseId = dp((consumer, provider))
      val updatedNamedBoolClause = accItems(consumer).clauses(clauseId).asInstanceOf[NamedBoolClause]
        .copy(clauses = accItems(provider).clauses)
      val updatedConsumer = accItems(consumer).copy(clauses = accItems(consumer).clauses + (clauseId -> updatedNamedBoolClause))
      (accItems + (consumer -> updatedConsumer), consumer :: changes)
    }
    }

    ItemsChanged(updatedItems, changesList, dp)
  }

  val receiveRecover: Receive = {
    case evt: Event =>
      state = state.update(evt)
    case SnapshotOffer(_, snapshot: State) =>
  }
}
