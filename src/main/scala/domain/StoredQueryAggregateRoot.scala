package domain

import akka.actor._
import akka.contrib.pattern.ClusterReceptionistExtension
import akka.persistence._

import scala.util.{Try, Success, Failure}


object StoredQueryAggregateRoot {

  val storedQueryAggregateRootSingleton = "/user/stored-query-aggregate-root/active"

  sealed trait Event

  sealed trait State

  sealed trait Command

  case class ClauseAddedAck(clauseId: String)

  case object ClausesRemovedAck

  case class ItemChanged(entity: StoredQuery, dependencies: Option[Map[(String, String), Int]] = None) extends Event

  case class ItemsChanged(items: Map[String, StoredQuery]) extends Event

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

  case class RemoveClauses(storedQueryId: String, specified: List[Int]) extends Command

  case object Sync extends Command

  val temporaryId: String = "temporary"

  case class CreateNewStoredQuery(title: String, referredId: String) extends Command


  case class StoredQuery(id: String = "", title: String = "", clauses: Map[Int, BoolClause] = Map.empty)

  case object CycleInDirectedGraphError

  case class Active(private val items: Map[String, StoredQuery] = Map.empty,
                    val clausesDependencies: Map[(String, String), Int] = Map.empty,
                    private val changes: Set[String] = Set.empty) extends State {

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

    def batchCascadingUpdate(): Map[String, StoredQuery] = {
      changes.foldLeft(items) { (acc, storedQueryId) =>
        acc ++ cascadingUpdate(storedQueryId, acc)
      }
    }

    private def cascadingUpdate(from: String, zero: Map[String, StoredQuery]): Map[String, StoredQuery] = {
      collectPaths(from)(toPredecessor(clausesDependencies.keys)).flatMap {
        _.toList
      }.foldLeft(zero) { (acc, link) => {
        val (provider, consumer) = link
        val clauseId = clausesDependencies((consumer, provider))
        val updatedNamedBoolClause = acc(consumer).clauses(clauseId).asInstanceOf[NamedBoolClause]
          .copy(clauses = acc(provider).clauses)
        acc + (consumer -> acc(consumer).copy(clauses = acc(consumer).clauses + (clauseId -> updatedNamedBoolClause)))
      }
      }
    }

    def getItem(id: String): Option[StoredQuery] = items.get(id)

    def update(event: Event): Active = {

      event match {

        case ItemChanged(entity, dependencies) =>
          copy(items = items + (entity.id -> entity), clausesDependencies = dependencies.getOrElse(clausesDependencies), changes = changes + entity.id)

        case ItemsChanged(xs) =>
          copy(items = items ++ xs, changes = changes -- xs.keys)

      }
    }
  }

}

object StoredQueryItemsView {

  import StoredQueryAggregateRoot.{BoolClause}

  case class Query(text: String = "")

  case class StoredQueryItem(id: String, title: String)

  case class QueryResponse(items: List[StoredQueryItem])

  case class GetItem(id: String)

  case class GetItemClauses(id: String, occurrence: String)

  case class ItemDetailResponse(item: StoredQueryItem)

  case class ItemClausesResponse(clauses: Map[Int, BoolClause])

  case class ItemNotFound(id: String)

  val storedQueryItemsViewSingleton = "/user/stored-query-items-view/active"
}

class StoredQueryItemsView extends PersistentView with ActorLogging {

  import StoredQueryAggregateRoot._
  import StoredQueryItemsView._

  override val viewId: String = "stored-query-aggregate-root-view"

  override val persistenceId: String = "stored-query-aggregate-root"

  var items: Map[String, StoredQuery] = Map(temporaryId -> StoredQuery(temporaryId, "temporary"))

  ClusterReceptionistExtension(context.system).registerService(self)

  def receive: Receive = {
    case ItemChanged(entity, dp) if isPersistent =>
      log.info(s"${entity.id}, ${entity.title} was changed.")
      items = items + (entity.id -> entity)

    case ItemsChanged(xs) =>
      items = items ++ xs

    case GetItem(id) =>
      items.get(id) match {
        case Some(StoredQuery(id, title, _)) =>
          sender() ! ItemDetailResponse(StoredQueryItem(id, title))
        case None =>
          sender() ! ItemNotFound(id)
      }

    case GetItemClauses(id, occurrence) =>
      items.get(id) match {
        case Some(StoredQuery(id, _, clauses)) =>
          sender() ! ItemClausesResponse(clauses.filter { case (clausesId, clause) => clause.occurrence == occurrence })

        case None =>
          sender() ! ItemNotFound(id)
      }

    case Query(text) =>
      sender() ! QueryResponse((items - temporaryId).values.map { e => StoredQueryItem(id = e.id, title = e.title) }.toList)
  }
}

class StoredQueryAggregateRoot extends PersistentActor with ActorLogging {

  import StoredQueryAggregateRoot._

  val persistenceId: String = "stored-query-aggregate-root"

  var state: Active = Active(items = Map(temporaryId -> StoredQuery(temporaryId, "temporary")))

  ClusterReceptionistExtension(context.system).registerService(self)

  val receiveCommand: Receive = {

    case CreateNewStoredQuery(title, referredId) =>
      state.getItem(referredId) match {
        case Some(item) =>
          def afterPersisted(`sender`: ActorRef, evt: ItemChanged) = {
            state = state.update(evt)
            `sender` ! evt
          }
          persist(ItemChanged(item.copy(id = state.generateNewItemId, title = title)))(afterPersisted(sender(), _))
        case None =>
          sender() ! s"$referredId is not exist."
      }

    case AddClause(storedQueryId, clause) =>
      state.getItem(storedQueryId) match {
        case Some(item) =>
          val newClauseId: Int = state.generateNewClauseId(item)
          val itemChanged = ItemChanged(item.copy(clauses = item.clauses + (newClauseId -> clause)))

          def afterPersisted(`sender`: ActorRef, evt: ItemChanged) = {
            state = state.update(evt)
            `sender` ! ClauseAddedAck(s"$newClauseId")
          }

          clause match {
            case NamedBoolClause(clauseStoredQueryId, title, _, _) =>
              state.CreateAcyclicClauseDependencies(storedQueryId, clauseStoredQueryId, newClauseId) match {
                case dp@Some(_) =>
                  persist(itemChanged.copy(dependencies = dp))(afterPersisted(sender(), _))
                case None =>
                  sender() ! CycleInDirectedGraphError
              }
            case _ =>
              persist(itemChanged)(afterPersisted(sender(), _))
          }
        case None =>
          sender() ! s"$storedQueryId is not exist."
      }

    case RemoveClauses(storedQueryId, specified) =>
      state.getItem(storedQueryId) match {
        case Some(item) =>

          def afterPersisted(`sender`: ActorRef, evt: ItemChanged) = {
            state = state.update(evt)
            `sender` ! ClausesRemovedAck
          }

          val xs = item.clauses.flatMap {
            case (k, v: NamedBoolClause) if specified.contains(k) => Some((storedQueryId, v.storedQueryId))
            case (k, v) => None
          }
          persist(ItemChanged(item.copy(clauses = item.clauses -- specified), Some(state.clausesDependencies -- xs)))(afterPersisted(sender(), _))

        case None =>
          sender() ! s"$storedQueryId is not exist."
      }

    case Sync =>
      def afterPersisted(`sender`: ActorRef, evt: ItemsChanged) = {
        state = state.update(evt)
        `sender` ! evt
      }
      persist(ItemsChanged(state.batchCascadingUpdate()))(afterPersisted(sender(), _))
  }

  val receiveRecover: Receive = {
    case evt: Event =>
      state = state.update(evt)
    case SnapshotOffer(_, snapshot: State) =>
  }
}

