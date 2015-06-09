package worker

import akka.actor.{ActorRef, Props, ActorLogging}
import akka.cluster.Cluster
import akka.contrib.pattern.{DistributedPubSubMediator, DistributedPubSubExtension, ClusterReceptionistExtension}
import akka.persistence.PersistentActor

import scala.concurrent.duration.{Deadline, FiniteDuration}

object Master {

  val ResultTopic = "results"

  def props(workTimeout: FiniteDuration): Props = Props(classOf[Master], workTimeout)

  case class Ack(workId: String)

  private sealed trait WorkerStatus
  private case object Idle extends WorkerStatus
  private case class Busy(workerId: String, deadline: Deadline) extends WorkerStatus
  private case class WorkerState(ref: ActorRef, status: WorkerStatus)

  private case object CleanupTick
}

class Master(workTimeout: FiniteDuration) extends PersistentActor with ActorLogging {
  import Master._
  import WorkState._

  val mediator = DistributedPubSubExtension(context.system).mediator
  ClusterReceptionistExtension(context.system).registerService(self)

  // persistenceId must include cluster role to support multiple masters
  override def persistenceId: String = Cluster(context.system).selfRoles.find(_.startsWith("backend-")) match {
    case Some(role) => s"$role-master"
    case None => "master"
  }

  private var workers = Map[String, WorkerState]()

  private var workState = WorkState.empty

  import context.dispatcher
  val cleanupTask = context.system.scheduler.schedule(workTimeout / 2, workTimeout / 2,
    self, CleanupTick)

  override def postStop(): Unit = cleanupTask.cancel()

  override def receiveCommand: Receive = {
    case MasterWorkerProtocol.RegisterWorker(workerId) =>
      if(workers.contains(workerId)){
        //update worker ref
        workers += (workerId -> workers(workerId).copy(ref = sender()))
      }
      else {
        log.info(s"Worker registered: {}", workerId)
        workers += (workerId -> WorkerState(sender(), status = Idle))
        if(workState.hasWork)
          sender() ! MasterWorkerProtocol.WorkIsReady
      }

    case MasterWorkerProtocol.WorkerRequestWork(workerId) =>
      if(workState.hasWork) {
        workers.get(workerId) match {
          case Some(s @ WorkerState(_, Idle)) =>
            val work = workState.nextWork
            persist(WorkStarted(work.workId)) { event =>
              workState = workState.update(event)
              log.info("Giving worker {} some work {}", workerId, work.workId)
              workers += (workerId -> s.copy(status = Busy(work.workId, Deadline.now + workTimeout)))
              sender() ! work
            }
          case _ =>
        }
      }

    case MasterWorkerProtocol.WorkIsDone(workerId, workId, result) =>
      //idempotent
      if(workState.isDone(workId)){
        sender() ! MasterWorkerProtocol.Ack(workId)
      }
      else if(!workState.isInProgress(workId)){
        log.info("Work {} not in progress, reported as done by worker {}", workId, workerId)
      }
      else {
      log.info("Work {} is done by worker{}", workId, workerId)
      changeWorkerToIdle(workerId, workId)
      persist(WorkCompleted(workId, result)) { event =>
        workState = workState.update(event)
        mediator ! DistributedPubSubMediator.Publish(ResultTopic, WorkResult(workId, result))
        // Ack back to original sender
        sender() ! MasterWorkerProtocol.Ack(workId)
        }
      }

    case MasterWorkerProtocol.WorkFailed(workerId, workId) =>
      if(workState.isInProgress(workId)){
        log.info("Work {} failed by worker {}", workId, workerId)
        changeWorkerToIdle(workerId, workId)
        persist(WorkerFailed(workId)) { event =>
          workState = workState.update(event)
          notifyWorkers()
        }
      }

    case work: Work =>
      //idempotent
      if (workState.isAccepted(work.workId))
        sender() ! Master.Ack(work.workId)
      else {
        log.info("Accepted work: {}", work.workId)
        persist(WorkAccepted(work)) { event =>
          //Ack back to original sender(Frontend)
          sender() ! Master.Ack(work.workId)
          workState = workState.update(event)
          notifyWorkers()
        }
      }

    case CleanupTick =>
      for((workerId, s @ WorkerState(_, Busy(workId, deadline))) <- workers){
        if(deadline.isOverdue) {
          workers -= workerId
          persist(WorkerTimedOut(workId)){ event =>
            workState = workState.update(event)
            notifyWorkers()
          }
        }
      }
  }

  def notifyWorkers(): Unit = {
    if(workState.hasWork) {
      // could pick a few random instead of all
      workers.foreach {
        case (_, WorkerState(ref, Idle)) => ref ! MasterWorkerProtocol.WorkIsReady
        case _                           => // busy
      }
    }
  }

  def changeWorkerToIdle(workerId: String, workId: String): Unit = {
    workers.get(workerId) match {
      case Some(w @ WorkerState(_, Busy(`workId`, _))) =>
        workers += (workerId -> w.copy(status = Idle))
      case _ =>
        // ok, might happen after standby recovery, worker state is not persisted
    }
  }

  override def receiveRecover: Receive = {
    case event: WorkDomainEvent =>
      // only update current state by applying the event, no side effects
      workState = workState.update(event)
      log.info("Replayed {}", event.getClass.getSimpleName)
  }
}
