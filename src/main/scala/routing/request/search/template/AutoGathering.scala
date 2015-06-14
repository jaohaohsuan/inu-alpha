package routing.request.search.template

import akka.actor.Actor.Receive
import akka.actor.{ActorRef, Actor, Terminated}
import akka.contrib.pattern.ClusterClient.SendToAll
import domain.search.template.CommandQueryProtocol._
import domain.search.template.Gathering
import domain.search.template.Graph.{Get, Routes, Inconsistency}
import routing.request.PerRequest
import spray.http.StatusCodes._
import scala.concurrent.duration._

trait AutoGathering {
  self: PerRequest =>

  val clusterClient: ActorRef
  val templateId: String

  import context.dispatcher

  def scheduler = context.system.scheduler

  def startGathering(): Unit = {
    context.become(gathering())
    clusterClient ! SendToAll(graphSingleton, Get(templateId))
  }

  def gathering(initialSegments: List[(String, (String, String))] = List.empty): Receive = {
    case Inconsistency(error) =>
      response {
        complete(InternalServerError, error)
      }
    case Routes(segments) =>
      
      scheduler.scheduleOnce(2.seconds, new Runnable {
        def run(): Unit = {
          context.watch(context.actorOf(Gathering.props(clusterClient, initialSegments ++ segments), name = "gathering"))
        }
      })
    case Terminated(child) =>
      log.info(s"$child is terminated")
      context.stop(this.self)
  }
}
