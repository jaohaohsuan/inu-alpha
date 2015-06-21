package domain

import akka.actor._
import akka.persistence._
import scala.util.{Try, Success, Failure}


object StoredQueryAggregate {

  sealed trait Event

  sealed trait State

  sealed trait Command

  case class UnalliedBoolClauseAdded(storedQueryId: String, clause: UnalliedBoolClause) extends Event

  case class AcyclicDependencyClauseAdded(storedQueryId: String, clause: NamedClause, dependencies: Map[(String, String), String]) extends Event

  case class ClauseRemoved(storedQueryId: String, clauseId: Int) extends Event

  case class StoredQueryAdded(id: String, title: String) extends Event

  case class Updated(storedQueryId: String) extends Event

  sealed trait BoolClause

  sealed trait UnalliedBoolClause extends BoolClause

  case class NamedClause(storedQueryId: String,
                         occurrence: String,
                         clauses: Map[Int, BoolClause] = Map.empty) extends BoolClause

  case class MatchBoolClause(query: String, operator: String, occurrence: String) extends UnalliedBoolClause

  case class AddClause(storedQueryId: String, clause: BoolClause) extends Command


  case class StoredQuery(id: String, title: String = "", clauses: Map[Int, BoolClause] = Map.empty)

  case object CycleInDirectedGraphError

  case class Active(private val items: Map[String, StoredQuery] = Map.empty,
                    private val clausesDependencies: Map[(String, String), String] = Map.empty,
                    private val changes: Set[String] = Set.empty) extends State {

    import algorithm.TopologicalSort._

    private def renewItemClauses(item: StoredQuery, list: List[BoolClause], providers: Map[String, StoredQuery]) = {
       item.copy(clauses = item.clauses ++
        list.map {
          case x @ NamedClause(storedQueryId, occurrence, _) => s"$storedQueryId-$occurrence".hashCode -> x.copy(clauses = providers(storedQueryId).clauses)
          case x => x.hashCode -> x
        }.toMap
      )
    }
    

    def batchCascadingUpdate(storedQueryIds: Traversable[String]): Map[String, StoredQuery] = {
      storedQueryIds.foldLeft(items){ (acc, storedQueryId) =>
        acc ++ cascadingUpdate(storedQueryId, acc)
      }
    }

    def cascadingUpdate(from: String, zero: Map[String, StoredQuery]) = {
      collectPaths(from)(toPredecessor(clausesDependencies.keys)).flatMap { _.toList }.foldLeft(zero) { (acc, link) => {
        val (provider, consumer) = link
        acc + (consumer -> renewItemClauses(
                              acc(consumer),
                              List(NamedClause(provider, clausesDependencies((consumer, provider)), acc(provider).clauses)),
                              acc)
              )
      }}
    }

    def maybeAcyclicClauseDependencies(consumer: String, provider: String, occurrence: String) = {
      val source = clausesDependencies + ((consumer, provider) -> occurrence)
      Try(sort(toPredecessor(source.keys))) match {
        case Success(_) => Some(source)
        case Failure(_) => None
      }
    }

    def update(event: Event): Active = {

      event match {

        case StoredQueryAdded(id, name) =>
         copy( items = items + (id -> StoredQuery(id, name)))

        case AcyclicDependencyClauseAdded(storedQueryId, clause, acyclicDependencies) =>
          copy(
            clausesDependencies = acyclicDependencies,
            items = items + (storedQueryId -> renewItemClauses(items(storedQueryId), List(clause), items)),
            changes = changes + storedQueryId
          )

        case UnalliedBoolClauseAdded(storedQueryId, clause: UnalliedBoolClause) =>
          copy(
            items = items + (storedQueryId -> renewItemClauses(items(storedQueryId), List(clause), items)),
           /* clausesDependencies = clause match {
                                    case NamedClause(provider, occurrence, _) => clausesDependencies + ((storedQueryId, provider) -> occurrence)
                                    case _ => clausesDependencies
                                  },*/
            changes = changes + storedQueryId)

        case ClauseRemoved(storedQueryId, clauseId) =>
          copy(
            items = items + (storedQueryId -> items(storedQueryId).copy(clauses = items(storedQueryId).clauses - clauseId)),
            clausesDependencies = items(storedQueryId).clauses(clauseId) match {
                                          case NamedClause(provider, occurrence, _) => clausesDependencies - Tuple2(storedQueryId, provider)
                                          case _ => clausesDependencies
                                        },
            changes = changes + storedQueryId)

        case Updated(storedQueryId) => {
          copy(
            items = cascadingUpdate(storedQueryId, items),
            changes = changes - storedQueryId)
        }
      }
    }
  }
}

class StoredQueryAggregate extends PersistentActor with ActorLogging {

  import StoredQueryAggregate._

  override def persistenceId = "stored-query-aggregate"

  var state: Active = Active()

  def afterEventPersisted(`sender`: ActorRef, evt: Event): Unit = {
    state = state.update(evt)
    `sender` ! state
  }


  val receiveCommand: Receive = {

    case AddClause(storedQueryId, clause: NamedClause) =>
      state.maybeAcyclicClauseDependencies(storedQueryId, clause.storedQueryId, clause.occurrence) match {
        case Some(dp) =>
          persist(AcyclicDependencyClauseAdded(storedQueryId, clause, dp))(afterEventPersisted(sender(), _))
        case None =>
          sender() ! CycleInDirectedGraphError
      }

    case AddClause(storedQueryId, clause: UnalliedBoolClause) =>
      persist(UnalliedBoolClauseAdded(storedQueryId, clause))(afterEventPersisted(sender(), _))
  }

  val receiveRecover: Receive = {
    case evt: Event =>
      state = state.update(evt)
    case SnapshotOffer(_, snapshot: State) =>
  }

}