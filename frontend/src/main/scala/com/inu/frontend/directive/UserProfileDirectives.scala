package com.inu.frontend.directive

import akka.actor.ActorSystem
import akka.io.IO
import org.json4s.JValue
import spray.can.Http
import spray.http._
import spray.routing.{Directive1, Directives}
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.http.HttpEntity._
import spray.http.HttpHeaders.RawHeader
import spray.http.HttpMethods._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Created by henry on 6/24/16.
  */
trait UserProfileDirectives extends Directives {

  implicit def executionContext: ExecutionContext
  implicit def system: ActorSystem
  implicit val timeout: Timeout = Timeout(15.seconds)

  private val config = ConfigFactory.load()

  def userSid: Directive1[String]= {
    cookie("sid").flatMap {
      case sid => provide(sid.content)
    }
  }

  def userFilter: Directive1[Future[JValue]] = {
    userSid.flatMap { sid =>
      val response = (IO(Http) ? HttpRequest(GET, Uri(s"${config.getString("service.user-profile.host")}/${config.getString("service.user-profile.filter")}"), headers = RawHeader("Authorization", s"bearer $sid") :: Nil)).mapTo[HttpResponse]
      provide(response.map { res => res.entity match {
        case entity: NonEmpty =>
          import org.json4s.native.JsonMethods._
          parse(entity.data.asString(HttpCharsets.`UTF-8`)) \ "query"
          }
        }
      )
    }
  }

}
