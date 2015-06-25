package domain

import akka.actor._
import akka.contrib.pattern.ClusterReceptionistExtension
import akka.persistence._
import algorithm.TopologicalSort._

import scala.util.{Try, Success, Failure}


object StoredQueryAggregateRoot {

  val storedQueryAggregateRootSingleton = "/user/stored-query-aggregate-root/active"

  sealed trait Event

  sealed trait State

  sealed trait Command

  case class ClauseAddedAck(clauseId: String)

  case object ClausesRemovedAck

  case class ItemCreated(entity: StoredQuery, dependencies: Map[(String, String), Int]) extends Event

  case class ItemsChanged(items: Map[String, StoredQuery], changes: List[String], dependencies: Map[(String, String), Int]) extends Event

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

  val temporaryId: String = "temporary"

  case class CreateNewStoredQuery(title: String, referredId: String) extends Command


  case class StoredQuery(id: String = "", title: String = "", clauses: Map[Int, BoolClause] = Map.empty)

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

        case ItemsChanged(xs, changesList,dp) =>
          copy(items = items ++ xs, clausesDependencies = dp, changes =
            changes ++ changesList.map { e => e -> (changes.getOrElse(e, 0) + 1) }.toMap)

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
    case ItemCreated(entity, dp) if isPersistent =>
      log.info(s"${entity.id}, ${entity.title} was created.")
      items = items + (entity.id -> entity)

    case ItemsChanged(xs, changes , _) =>
      changes.foreach { id => log.info(s"$id, ${xs(id).title} was changed.")}
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

    case Pull =>
      sender() ! Changes((state.changes - temporaryId).keys.map { state.items(_) }.toList)
  }

  def cascadingUpdate(from: String, items: Map[String, StoredQuery], dp: Map[(String, String), Int]) = {

    val zero = (items, List(from))

    val (updatedItems, changesList) = collectPaths(from)(toPredecessor(dp.keys)).flatMap { _.toList }.foldLeft(zero) { (acc, link) => {
      val (provider, consumer) = link
      val (accItems, changes) = acc
      val clauseId = dp((consumer, provider))
      val updatedNamedBoolClause = accItems(consumer).clauses(clauseId).asInstanceOf[NamedBoolClause]
        .copy(clauses = accItems(provider).clauses)
      val updatedConsumer = accItems(consumer).copy(clauses = accItems(consumer).clauses + (clauseId -> updatedNamedBoolClause))
      (accItems + (consumer -> updatedConsumer), consumer :: changes)
    } }

    ItemsChanged(updatedItems, changesList, dp)
  }

  val receiveRecover: Receive = {
    case evt: Event =>
      state = state.update(evt)
    case SnapshotOffer(_, snapshot: State) =>
  }
}

