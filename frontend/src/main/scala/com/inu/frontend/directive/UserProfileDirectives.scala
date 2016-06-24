package com.inu.frontend.directive

import akka.actor.ActorSystem
import akka.io.IO
import org.json4s.JValue
import spray.can.Http
import spray.http.{HttpCookie, HttpRequest, HttpResponse, Uri}
import spray.routing.{Directive1, Directives}
import akka.pattern._
import akka.util.Timeout
import spray.http.HttpHeaders.RawHeader
import spray.http.HttpMethods._
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext

/**
  * Created by henry on 6/24/16.
  */
trait UserProfileDirectives extends Directives {

  implicit def executionContext: ExecutionContext
  implicit def system: ActorSystem
  implicit val timeout: Timeout = Timeout(15.seconds)

  def userSid: Directive1[String]= {
    cookie("sid").flatMap {
      case sid => provide(sid.content)
    }
  }

  def userFilter: Directive1[String] = {
    userSid.flatMap { sid =>
      val response = (IO(Http) ? HttpRequest(GET, Uri("http://127.0.0.1:2403/users/me?include=datasourcesRangeEsQueries"),
         headers = RawHeader("Authorization", s"bearer $sid") :: Nil)).mapTo[HttpResponse]
      onSuccess(response).flatMap { res =>
        provide(s"${res.entity}")
      }
    }
  }

}
