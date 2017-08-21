package com.inu.cluster.storedquery

import akka.actor.{ActorLogging, PoisonPill, Props}
import akka.pattern.{Backoff, BackoffSupervisor}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import com.inu.cluster.storedquery.algorithm.TopologicalSort
import com.inu.cluster.storedquery.messages._
import com.inu.protocol.storedquery.messages._
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.duration._


import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

case class StoredQueryRepo(items: Map[String, StoredQuery])
case class CascadingUpdateGuide(guides: List[(String, String)])

object StoredQueryRepoAggRoot extends LazyLogging {

  def props = Props(classOf[StoredQueryRepoAggRoot])

  def propsWithBackoff = BackoffSupervisor.props(
    Backoff.onStop(
      childProps = props,
      childName = "StoredQueryRepoAggRoot",
      minBackoff = 3.seconds,
      maxBackoff = 3 minute,
      randomFactor = 0.2
    ))

  implicit class idValidator(id: String) {
    def exist()(implicit state: StoredQueries): Boolean = state.items.contains(id)
    def notExist()(implicit state: StoredQueries): Boolean = !state.items.contains(id)
  }

  implicit def getEntity(id: String)(implicit state: StoredQueries): StoredQuery = {
    val item = state.items(id)
    //if(item.archived)
    //  item.copy(clauses = Map.empty)
    item
  }

  object CreateStoredQuery {
    def unapply(arg: Any): Option[Either[String, StoredQuery]] = {
      arg match {
        case (ItemCreated(id, title, refId, tags), StoredQueryRepo(repo)) => {
          implicit def getItem2(referredId: Option[String]): Option[Option[StoredQuery]] = referredId.map(repo.get)
          implicit def fill(src: StoredQuery): Either[String, StoredQuery] = Right(src.copy(id = id, title = title, tags = tags))
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
        case (ItemUpdated(id, newTitle, newTags), StoredQueryRepo(repo)) => repo.get(id).map(_.copy(title = newTitle.trim, tags = newTags))
        case _ => None
      }
    }
  }

  object UpdateClauses {
    def unapply(arg: Any): Option[Either[String, StoredQuery]] = {
      arg match {
        case (ClauseAdded(consumer, boolClause), StoredQueryRepo(repo)) => {
          Some(repo.get(consumer) match {
            case None =>
              logger.error("Add {} to unknown storedQuery {}", boolClause, consumer)
              Left("oops")
            case Some(entity) =>
              boolClause match {
                case (_, NamedClause(refId,_, _, _)) if !refId.matches("\\d+") =>
                  logger.warn(s"prevent to add temporary of NamedClause on $consumer")
                  Right(entity.copy(clauses = entity.clauses))
                case _ =>
                  Right(entity.copy(clauses = entity.clauses + boolClause))
              }

          })
        }
        case (ClauseRemoved(storedQueryId, boolClauses), StoredQueryRepo(repo)) =>
          Some(repo.get(storedQueryId) match {
            case None =>
              logger.error("Removing unknown storedQuery {}'s clauses", storedQueryId)
              Left("oops")
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

  object CascadingUpdateOneByOne {
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

    def update(evt: Event): StoredQueries = {
      def proc(arg: Any): StoredQueries = {
        arg match {
          // 事件處理, 一次只會進入一個
          case CreateStoredQuery(Right(entity)) => proc((entity, copy(items = items.updated(entity.id, entity), changes = (entity.id :: Nil) :: changes)))
          case ApplyStoredQueryUpdate(entity)   => proc((entity, copy(items = items.updated(entity.id, entity), changes = (entity.id :: Nil) :: changes)))
          case UpdateClauses(Right(entity))     => proc((entity, copy(items = items.updated(entity.id, entity), changes = (entity.id :: Nil) :: changes)))

          // 模型引用查找與更新
          case BuildDependencies(guides, state)     => proc((CascadingUpdateGuide(guides), state))
          case CascadingUpdateOneByOne(Nil, state)  => state
          case CascadingUpdateOneByOne(guides, state) => proc((CascadingUpdateGuide(guides), state))
          case _ => throw new Exception("state update condition miss matching")
        }
      }
      // start from here
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

class StoredQueryRepoAggRoot extends PersistentActor with ActorLogging {

  import StoredQueryRepoAggRoot._

  val persistenceId: String = "StoredQueryRepoAggRoot"

  implicit var state = StoredQueries()

  val receiveCommand: Receive = {

    case PoisonPill => context stop self

    case Initial if !state.items.contains("temporary") =>
      doPersist(ItemCreated("temporary", "temporary"), PersistedAck(sender(), Some(StoredQueryCreatedAck("temporary"))))

    case Initial if state.items.contains("temporary") =>
      sender() ! RejectAck("already initialized")

    case InitialTemporary(uid) if """[^\w]+""".r.findFirstIn(uid).nonEmpty =>
      sender() ! RejectAck("illegal uid")

    case InitialTemporary(uid) if state.items.contains(uid) =>
      sender() ! RejectAck("already initialized")

    case InitialTemporary(uid) => doPersist(ItemCreated(uid, "temporary"), PersistedAck(sender(),Some(StoredQueryCreatedAck(uid))))

    case CreateNewStoredQuery(_, Some(refId), _) if refId.notExist() => sender() ! RejectAck(s"$refId is not exist.")

    case CreateNewStoredQuery(title,refId, tags) =>
      doPersist(ItemCreated(state.newItemId, title, refId, tags), PersistedAck(sender(),Some(StoredQueryCreatedAck(state.newItemId))))

    case UpdateStoredQuery(storedQueryId, _, _) if storedQueryId.notExist() => sender() ! RejectAck(s"$storedQueryId is not exist.")

    case UpdateStoredQuery(_, title, _) if title.trim().isEmpty => sender() ! RejectAck(s"title can not be blank")

    case UpdateStoredQuery(storedQueryId, title, tags) =>
      doPersist(ItemUpdated(storedQueryId, title, tags.getOrElse("").split("""[\s,]+""").toSet), PersistedAck(sender(),Some(UpdatedAck)))

    case AddClause(storedQueryId, _) if storedQueryId.notExist() => sender() ! RejectAck(s"$storedQueryId is not exist.")
    case AddClause(_, NamedClause(refId, _, _, _))    if refId.notExist()         => sender() ! RejectAck(s"$refId is not exist.")
    case AddClause(_, NamedClause("temporary", _, _, _))                          => sender() ! RejectAck(s"temporary stored query cannot be referenced.")
    case AddClause(_, NamedClause(uid, _, _, _))      if !uid.matches("\\d+")     => sender() ! RejectAck(s"user temporary stored query cannot be referenced.")

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

    case RemoveClauses(id, _) if id.notExist() => sender() ! RejectAck(s"$id is not exist.")
    case RemoveClauses(storedQueryId, ids) =>
        storedQueryId.clauses.filterKeys(ids.contains) match {
          case filtered if filtered.isEmpty => sender() !  ClausesRemovedAck(filtered)
          case clauses                      => doPersist(ClauseRemoved(storedQueryId, clauses), PersistedAck(sender(), Some(ClausesRemovedAck(clauses))))
        }

    case ResetOccurrence(id, _) if id.notExist() => sender() ! RejectAck(s"$id is not exist.")
    case ResetOccurrence(storedQueryId, occurrence) =>
      storedQueryId.clauses.filter{ case (_, BoolClause(occur)) => occur == occurrence } match {
        case filtered if filtered.isEmpty => sender() !  ClausesRemovedAck(filtered)
        case removeClauses                =>  doPersist(ClauseRemoved(storedQueryId, removeClauses), PersistedAck(sender(), Some(ClausesRemovedAck(removeClauses))))
      }
  }

  val receiveRecover: Receive = {
    case RecoveryCompleted =>
      log.info(s"RecoveryCompleted")
    case evt: Event =>
      state = state.update(evt)
    case SnapshotOffer(_, snapshot: State) =>
  }


  override protected def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
    super.onRecoveryFailure(cause, event)
    throw cause
  }

  def afterPersisted(ack: PersistedAck)(evt: Event) = {
    state = state.update(evt)
    ack.send(evt)
  }

  def doPersist(evt: Event, ack: PersistedAck) = persist(evt)(afterPersisted(ack))

  override def preStart() = {
    log.info("StoredQueryRepoAggRoot up")
  }
}
