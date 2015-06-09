package worker

import java.util.UUID

import akka.actor._
import akka.contrib.pattern.ClusterClient.SendToAll
import scala.concurrent.duration._
import akka.actor.SupervisorStrategy.{ Stop, Restart}
import scala.concurrent.duration.FiniteDuration

object Worker {
  def props(clusterClient: ActorRef, workExecutorProps: Props, registerInterval: FiniteDuration  = 10.seconds): Props =
    Props(classOf[Worker], clusterClient, workExecutorProps, registerInterval)

  case class WorkComplete(result: Any)
}

class Worker(clusterClient: ActorRef, workExecutorProps: Props, registerInterval: FiniteDuration)
  extends Actor with ActorLogging {

  val workerId = UUID.randomUUID().toString

  import context.dispatcher
  val registerTask = context.system.scheduler.schedule(0.seconds, registerInterval, clusterClient,
   SendToAll("/user/master/active", MasterWorkerProtocol.RegisterWorker(workerId)))

  val workExecutor = context.watch(context.actorOf(workExecutorProps, name = "exec"))

  var currentWorkId: Option[String] = None
  def workId: String = currentWorkId match {
    case Some(workId) => workId
    case None => throw new IllegalStateException("Not working")
  }

  override def supervisorStrategy = OneForOneStrategy() {
    case _: ActorInitializationException => Stop
    case _: DeathPactException           => Stop
    case _: Exception =>
      currentWorkId foreach { workId => sendToMaster(MasterWorkerProtocol.WorkFailed(workerId, workId))}
      context.become(idle)
      Restart
  }

  override def postStop(): Unit = registerTask.cancel()

  def receive = idle

  def idle: Receive = {
    case MasterWorkerProtocol.WorkIsReady =>
      sendToMaster(MasterWorkerProtocol.WorkerRequestWork(workerId))
    case Work(workId, job) =>
      log.info("Got work: {}", job)
      currentWorkId = Some(workId)
      workExecutor ! job
      context.become(working)
  }

  def working: Receive = {
    case Worker.WorkComplete(result) =>
      log.info("Work is complete. Result {}.", result)
      sendToMaster(MasterWorkerProtocol.WorkIsDone(workerId, workId, result))
      context.setReceiveTimeout(5.seconds)
      context.become(waitForWorkIsDoneAck(result))
    case _: Work =>
      log.info("Yikes. Master told me to do work, while I'm working.")
  }

  def waitForWorkIsDoneAck(result: Any): Receive = {

    case MasterWorkerProtocol.Ack(id) if id == workId =>
      sendToMaster(MasterWorkerProtocol.WorkerRequestWork(workerId))
      context.setReceiveTimeout(Duration.Undefined)
      context.become(idle)

    case ReceiveTimeout =>
      log.info("No ack from master, retrying")
      sendToMaster(MasterWorkerProtocol.WorkIsDone(workerId, workId, result))
  }

  override def unhandled(message: Any): Unit = message match {
    case Terminated(`workExecutor`)       => context.stop(self)
    case MasterWorkerProtocol.WorkIsReady =>
      log.info(s"unhandled WorkIsReady")
    case _                                => super.unhandled(message)
  }

  def sendToMaster(message: Any): Unit = {
    clusterClient ! SendToAll("/user/master/active", message)

  }

}
