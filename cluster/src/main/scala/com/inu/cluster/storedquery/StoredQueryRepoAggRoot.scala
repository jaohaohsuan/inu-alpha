package com.inu.cluster.storedquery

import akka.actor.Props
import akka.persistence.{PersistentActor, SnapshotOffer}
import com.inu.cluster.storedquery.algorithm.TopologicalSort
import com.inu.protocol.storedquery.messages._
import messages._

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

case class StoredQueryRepo(items: Map[String, StoredQuery])
case class CascadingUpdateGuide(guides: List[(String, String)])

object StoredQueryRepoAggRoot {

  def props = Props(classOf[StoredQueryRepoAggRoot])

  implicit class idValidator(id: String) {
    def exist()(implicit state: StoredQueries) = state.items.get(id).isDefined
  }

  implicit def getEntity(id: String)(implicit state: StoredQueries): StoredQuery = state.items(id)

  object CreateStoredQuery {
    def unapply(arg: Any): Option[Either[String, StoredQuery]] = {
      arg match {
        case (ItemCreated(id, title, refId, tags), StoredQueryRepo(repo)) => {
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

  object ApplyStoredQueryUpdate {
    def unapply(arg: Any): Option[StoredQuery] = {
      arg match {
        case (ItemUpdated(id, newTitle, newTags), StoredQueryRepo(repo)) => repo.get(id).map(_.copy(title = newTitle, tags = newTags))
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
            case Some(entity) =>
              Right(entity.copy(clauses = entity.clauses + boolClause))
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

    import TopologicalSort._

    private def acyclicProofing(dep: Map[(String, String), Int]): Option[Map[(String, String), Int]] = {
      import TopologicalSort._

      import scala.util._
      Try(sort(toPredecessor(dep.keys))) match {
        case Success(_) => Some(dep)
        case Failure(_) => None
      }
    }

    def unapply(arg: Any): Option[(List[(String,String)], StoredQueries)] = {
      arg match {
        case (StoredQuery(id, _, clauses, _), state@StoredQueries(_, paths, _)) =>
          val newDep = clauses.flatMap {
            case (k, ref: NamedClause) => Some((id, ref.storedQueryId) -> k)
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
    def unapply(arg: Any): Option[(List[(String, String)], StoredQueries)] = {
      arg match {
        case (CascadingUpdateGuide(Nil), state: StoredQueries) => Some((Nil, state))
        case (CascadingUpdateGuide((providerId, consumerId) :: xs), state@StoredQueries(repo, dep, y :: yx)) =>
          for {
            provider <- repo.get(providerId)
            consumer <- repo.get(consumerId)
            clauseId <- dep.get((consumerId, providerId))
            clause <- consumer.clauses.get(clauseId)
            consumerClauses = clause match {
              case n: NamedClause => consumer.clauses + (clauseId -> n.copy(clauses = Some(provider.clauses)))
              case _ => consumer.clauses
            }
            updatedConsumer = consumerId -> consumer.copy(clauses = consumerClauses)
          } yield (xs, state.copy(items = state.items + updatedConsumer, changes = (consumerId :: y) :: yx))
        case _ => None
      }
    }

  }

  implicit class storedQueriesOps(value: StoredQueries) {

    import value._

    def update(evt: Any): StoredQueries = {
      val x :: xs = Nil :: changes
      def proc(arg: Any): StoredQueries = {
        arg match {
          case CreateStoredQuery(Right(entity)) => proc((entity, copy(items = items.updated(entity.id, entity), changes = (entity.id :: x) :: xs)))
          case ApplyStoredQueryUpdate(entity)   => proc((entity, copy(items = items.updated(entity.id, entity), changes = (entity.id :: x) :: xs)))
          case UpdateClauses(Right(entity))     => proc((entity, copy(items = items.updated(entity.id, entity), changes = (entity.id :: x) :: xs)))
          case BuildDependencies(list, state)   => proc((CascadingUpdateGuide(list), state))
          case CascadingUpdate(Nil, state)      => state
          case CascadingUpdate(list, state)     => proc((CascadingUpdateGuide(list), state))
          case _ => throw new Exception("state update condition miss matching")
        }
      }
      proc((evt, StoredQueryRepo(items)))
    }

    def testCycleInDirectedGraph(evt: ClauseAdded): String Either ClauseAdded = {
      val x :: xs = Nil :: changes
      def proc(arg: Any): StoredQueries = {
        arg match {
          case UpdateClauses(Right(entity))     => proc((entity, copy(items = items.updated(entity.id, entity), changes = (entity.id :: x) :: xs)))
          case BuildDependencies(guides, state) => state
          case _ => throw new Exception("CycleInDirectedGraphError")
        }
      }
      Try(proc((evt, StoredQueryRepo(items)))) match {
        case Success(_) => Right(evt)
        case Failure(e) => Left(e.getMessage)
      }
    }
  }
}

class StoredQueryRepoAggRoot extends PersistentActor  {

  import StoredQueryRepoAggRoot._

  val persistenceId: String = "StoredQueryRepoAggRoot"

  implicit var state = StoredQueries()

  val receiveCommand: Receive = {

    case Initial if !state.items.contains("temporary") =>
      doPersist(ItemCreated("temporary", "temporary"), PersistedAck(sender(), Some(StoredQueryCreatedAck("temporary"))))

    case Initial if state.items.contains("temporary") =>
      sender() ! RejectAck("already initialized")

    case CreateNewStoredQuery(_, Some(refId), _) if !refId.exist() => sender() ! RejectAck(s"$refId is not exist.")

    case CreateNewStoredQuery(title,refId, tags) =>
      doPersist(ItemCreated(state.newItemId, title, refId, tags), PersistedAck(sender(),Some(StoredQueryCreatedAck(state.newItemId))))

    case UpdateStoredQuery(storedQueryId, _, _) if !storedQueryId.exist() => sender() ! RejectAck(s"$storedQueryId is not exist.")

    case UpdateStoredQuery(_, "", _) => sender() ! RejectAck(s"title can not be blank")

    case UpdateStoredQuery(storedQueryId, title, tags) =>
      doPersist(ItemUpdated(storedQueryId, title, tags.getOrElse("").split("""\s+""").toSet), PersistedAck(sender(),Some(UpdatedAck)))

    case AddClause(storedQueryId, clause: BoolClause) if !storedQueryId.exist() => sender() ! RejectAck(s"$storedQueryId is not exist.")


    case AddClause(storedQueryId, clause: BoolClause) => {
      def genClauseId(item: StoredQuery): Int = {
        val id = scala.math.abs(scala.util.Random.nextInt())
        if (item.clauses.keys.exists(_ == id)) genClauseId(item) else id
      }
      val id = genClauseId(storedQueryId)

      state.testCycleInDirectedGraph(ClauseAdded(storedQueryId, (id, clause))) match {
        case Left(err) => sender() ! RejectAck(err)
        case Right(evt: ClauseAdded) => doPersist(evt, PersistedAck(sender(), Some(ClauseAddedAck(s"$id"))))
      }
    }

    case RemoveClauses(id, _) if !id.exist() => sender() ! RejectAck(s"$id is not exist.")

    case RemoveClauses(storedQueryId, ids) =>
      val clauses = (storedQueryId: StoredQuery).clauses
      doPersist(ClauseRemoved(storedQueryId, clauses.filterKeys(ids.contains)), PersistedAck(sender(), Some(ClausesRemovedAck)))

    case ResetOccurrence(id, _) if !id.exist() => sender() ! RejectAck(s"$id is not exist.")

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
