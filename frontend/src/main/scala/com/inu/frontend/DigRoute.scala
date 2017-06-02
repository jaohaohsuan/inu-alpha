package com.inu.frontend

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Route
import scala.concurrent.ExecutionContext

/**
  * Created by henry on 6/1/17.
  */
trait DigRoute {

  implicit val actorSystem: ActorSystem
  implicit val ec: ExecutionContext = actorSystem.dispatcher

  def route: Route = ???
}

class DigRouteService(implicit val actorSystem: ActorSystem) extends DigRoute
