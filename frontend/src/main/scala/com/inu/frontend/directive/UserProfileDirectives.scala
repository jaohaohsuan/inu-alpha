package com.inu.frontend.directive

import akka.actor.ActorSystem
import akka.io.IO
import org.json4s.{JObject, JValue}
import spray.can.Http
import spray.http._
import spray.routing.{Directive1, Directives}
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.json4s.JsonAST.JArray
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

  def userFilter: Directive1[JValue] = {
    userSid.flatMap { sid =>
      val response = (IO(Http) ? HttpRequest(GET, Uri(s"${config.getString("service.user-profile.host")}/${config.getString("service.user-profile.filter")}"), headers = RawHeader("Authorization", s"bearer $sid") :: Nil)).mapTo[HttpResponse]
      onSuccess(response).flatMap { res => res.entity match {
          case entity: NonEmpty =>
            import org.json4s.native.JsonMethods._
            provide(parse(entity.data.asString(HttpCharsets.`UTF-8`)) \ "query")
          case _ => reject
        }
      }
    }
  }

  def dataSourceFilters : Directive1[List[JValue]]= {
    userSid.flatMap { sid =>
      val response = (IO(Http) ? HttpRequest(GET, Uri(s"${config.getString("service.user-profile.host")}/${config.getString("service.user-profile.filters")}"), headers = RawHeader("Authorization", s"bearer $sid") :: Nil)).mapTo[HttpResponse]
      onSuccess(response).flatMap { res => res.entity match {
          case entity: NonEmpty =>
            import org.json4s.native.JsonMethods._
            val JArray(xs) = parse(entity.data.asString(HttpCharsets.`UTF-8`)) \ "esQueries"
            provide(xs)
          case _ => reject
        }
      }
    }
  }

  def logsfilter(filterId: Option[String]): Directive1[JValue] = {

    headerValueByName("uid").flatMap { uid =>
      userSid.flatMap { sid =>

        val req = HttpRequest(GET,
          Uri(s"${config.getString("service.dapi.host")}/${config.getString("service.dapi.logsfilter")}?logsFilterId=${filterId.getOrElse("")}"),
          headers = RawHeader("Authorization", s"bearer $sid") :: RawHeader("uid",uid) :: Nil)
        val response = (IO(Http) ? req).mapTo[HttpResponse]
        onSuccess(response).flatMap { res =>
          res.entity match {
            case entity: NonEmpty =>
              import org.json4s.native.JsonMethods._
              val result: JValue = parse(entity.data.asString(HttpCharsets.`UTF-8`)) \\ "esQuery"
              provide(result)
            case _ => reject
          }
        }
      }
    }

  }

}
