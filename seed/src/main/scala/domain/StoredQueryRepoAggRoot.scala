package domain

import akka.persistence.{PersistentActor, SnapshotOffer}
import protocol.storedQuery._

import scala.language.implicitConversions
/**
  * Created by henry on 4/30/16.
  */

case class StoredQueryRepo(items: Map[String, StoredQuery])
//case class StoredQueryPaths(paths: Map[(String, String), Int])
case class CascadingUpdateGuide(guides: List[(String, String)])

object StoredQueryRepoAggRoot {

  implicit class idValidator(id: String) {
    def exist()(implicit state: StoredQueries2) = state.items.get(id).nonEmpty
  }

  implicit def getEntity(id: String)(implicit state: StoredQueries2): StoredQuery = state.items(id)

  val persistenceId: String = "storedqRepoAggRoot"

  object CreateStoredQuery {
    def unapply(arg: Any): Option[Either[String, StoredQuery]] = {
      arg match {
        case (ItemCreated2(id, refId, title, tags), StoredQueryRepo(repo)) => {
          implicit def getItem2(referredId: Option[String]): Option[Option[StoredQuery]] = referredId.map(repo.get)
          implicit def fill(src: StoredQuery): Either[String, StoredQuery] = Right(src.copy(id = id, title = title, tags = tags.toSet))
          type StoredQueryRef = Option[StoredQuery]
          Some((refId: Option[StoredQueryRef]) match {
            case Some(None) => Left(refId.get) // bad refId
            case Some(Some(src)) => src // copy from
            case None => StoredQuery() // brand new
          })
        }
        case _ => None
      }
    }
  }

  object UpdateClauses {
    def unapply(arg: Any): Option[Either[String, StoredQuery]] = {
      arg match {
        case (ClauseAdded(consumer, boolClause), StoredQueryRepo(repo)) => {
          Some(repo.get(consumer) match {
            case None => Left("oops")
            case Some(entity) => Right(entity.copy(clauses = entity.clauses + boolClause))
          })
        }
        case (ClauseRemoved(storedQueryId, boolClauses), StoredQueryRepo(repo)) =>
          Some(repo.get(storedQueryId) match {
            case None => Left("oops")
            case Some(entity) =>
              Right(entity.copy(clauses = entity.clauses -- boolClauses.keys))
          })
        case _ => None
      }
    }
  }

  object BuildDependencies {

    import domain.algorithm.TopologicalSort._

    private def acyclicProofing(dep: Map[(String, String), Int]): Option[Map[(String, String), Int]] = {
      import algorithm.TopologicalSort._
      import scala.util._
      Try(sort(toPredecessor(dep.keys))) match {
        case Success(_) => Some(dep)
        case Failure(_) => None
      }
    }

    def unapply(arg: Any): Option[(List[(String,String)], StoredQueries2)] = {
      arg match {
        case (StoredQuery(id, _, clauses, _), state@StoredQueries2(_, paths)) =>
          val newDep = clauses.flatMap {
            case (k, ref: NamedBoolClause) => Some((id, ref.storedQueryId) -> k)
            case _ => None
          }
          val consumerPaths = paths.filterKeys({ case (consumer, _) => consumer == id }).keys
          acyclicProofing(paths -- consumerPaths ++ newDep).map { p =>
             val guides = collectPaths[String](id)(toPredecessor(p.keys)).flatten.toList
            (guides, state.copy(paths = p))
          }
        case _ => None
      }
    }
  }

  object CascadingUpdate {
    def unapply(arg: Any): Option[(List[(String, String)], StoredQueries2)] = {
      arg match {
        case (CascadingUpdateGuide(Nil), state: StoredQueries2) => Some((Nil, state))
        case (CascadingUpdateGuide((providerId, consumerId) :: xs), state@StoredQueries2(repo, dep)) =>
          for {
            provider <- repo.get(providerId)
            consumer <- repo.get(consumerId)
            clauseId <- dep.get((consumerId, providerId))
            clause <- consumer.clauses.get(clauseId)
            consumerClauses = clause match {
              case n: NamedBoolClause => consumer.clauses + (clauseId -> n.copy(clauses = provider.clauses))
              case _ => consumer.clauses
            }
            updatedConsumer = consumerId -> consumer.copy(clauses = consumerClauses)
          } yield (xs, state.copy(items = state.items + updatedConsumer))
        case _ => None
      }
    }

  }

  case class StoredQueries2(items: Map[String, StoredQuery] = Map.empty, paths: Map[(String, String), Int] = Map.empty ) {

    lazy val newItemId = {
      def generateNewItemId: String = {
        val id = scala.math.abs(scala.util.Random.nextInt()).toString
        if (items.keys.exists(_ == id)) generateNewItemId else id
      }
      generateNewItemId
    }

    def update(evt: Any): StoredQueries2 = {

      def proc(arg: Any): StoredQueries2 = {
        arg match {
          case CreateStoredQuery(Right(entity))      => proc((entity, copy(items = items.updated(entity.id, entity))))
          case UpdateClauses(Right(entity))          => proc((entity, copy(items = items.updated(entity.id, entity))))
          case BuildDependencies(list, state) => proc((CascadingUpdateGuide(list), state))
          case CascadingUpdate(Nil, state)           => state
          case CascadingUpdate(list, state)          => proc((CascadingUpdateGuide(list), state))
          case _ => this
        }
      }

      proc((evt, StoredQueryRepo(items)))
    }
  }
}

class StoredQueryRepoAggRoot extends PersistentActor  {

  import StoredQueryRepoAggRoot._
  import storedQuery.StoredQueryAggregateRoot._

  val persistenceId: String = StoredQueryRepoAggRoot.persistenceId

  implicit var state = StoredQueries2()

  val receiveCommand: Receive = {

    case Initial2 if !state.items.contains("temporary") =>
      doPersist(ItemCreated2("temporary", None, "temporary", Nil), PersistedAck(sender()))

    case CreateNewStoredQuery(_, Some(refId), _) if refId.exist() => sender() ! s"$refId is not exist."

    case CreateNewStoredQuery(title,refId, tags) =>
      doPersist(ItemCreated2(state.newItemId, refId, title, tags.toList), PersistedAck(sender()))

    case AddClause(storedQueryId, clause: BoolClause) if storedQueryId.exist() => sender() ! s"$storedQueryId is not exist."

    case AddClause(storedQueryId, clause: BoolClause) => {
      def genClauseId(item: StoredQuery): Int = {
        val id = scala.math.abs(scala.util.Random.nextInt())
        if (item.clauses.keys.exists(_ == id)) genClauseId(item) else id
      }
      val id = genClauseId(storedQueryId)
      doPersist(ClauseAdded(storedQueryId, (id, clause)), PersistedAck(sender(), Some(ClauseAddedAck(s"$id"))))
    }

    case RemoveClauses(id, _) if id.exist() => sender() ! s"$id is not exist."

    case RemoveClauses(storedQueryId, ids) =>
      val clauses = (storedQueryId: StoredQuery).clauses
      doPersist(ClauseRemoved(storedQueryId, clauses.filterKeys(ids.contains)), PersistedAck(sender(), Some(ClausesRemovedAck)))

    case ResetOccurrence(id, _) if id.exist() => sender() ! s"$id is not exist."

    case ResetOccurrence(storedQueryId, occurrence) =>
      val clauses = (storedQueryId: StoredQuery).clauses
      val removeClauses = clauses.filter{ case (_, BoolClause(occur)) => occur == occurrence }
      doPersist(ClauseRemoved(storedQueryId, removeClauses), PersistedAck(sender(), Some(ClausesRemovedAck)))

  }

  val receiveRecover: Receive = {
    case evt: Event =>
      state = state.update(evt)
    case SnapshotOffer(_, snapshot: State) =>
  }

  def afterPersisted(ack: PersistedAck)(evt: Event) = {
    state = state.update(evt)
    ack.send(evt)
  }

  def doPersist(evt: Event, ack: PersistedAck) = persist(evt)(afterPersisted(ack))

}
