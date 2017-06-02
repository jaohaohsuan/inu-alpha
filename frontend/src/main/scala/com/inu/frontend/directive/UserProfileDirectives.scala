package com.inu.frontend.directive

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.server.Directives.extractMaterializer
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.io.IO
import org.json4s.{JObject, JValue}
import akka.pattern._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.json4s.JsonAST.{JArray, JValue}

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


  import com.inu.frontend.utils.encoding.NativeJson4sSupport._
  import org.json4s.native.JsonMethods._

  private val config = ConfigFactory.load()

  lazy val userProfileEndpoint: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
    Http().outgoingConnection(host = config.getString("service.user-profile.host"), port = config.getInt("service.user-profile.port"))

  lazy val dapiEndpoint =
    Http().outgoingConnection(host = config.getString("service.dapi.host"), port = config.getInt("service.dapi.port"))

  def userSid: Directive1[String]= {
    cookie("sid").flatMap { sid => provide(sid.value)
    }
  }


  def userFilter: Directive1[JValue] = {
    userSid.flatMap { sid =>
      extractMaterializer.flatMap { implicit mat =>

        val request = RequestBuilding.Get(s"/${config.getString("service.user-profile.filter")}").addHeader(RawHeader("Authorization", s"bearer $sid"))
        val response = Source.single(request).via(userProfileEndpoint).runWith(Sink.head).flatMap { resp =>
          Unmarshal(resp.entity).to[String].map { str => parse(str) \ "query" }
        }
        onComplete(response).flatMap {
          case scala.util.Success(query) => provide(query)
          case scala.util.Failure(ex) =>
            system.log.error(ex,"unable to get userFilter")
            reject
        }
      }
    }
  }

  def dataSourceFilters : Directive1[List[JValue]]= {
    userSid.flatMap { sid =>
      extractMaterializer.flatMap { implicit mat =>

        val request = RequestBuilding.Get(s"/${config.getString("service.user-profile.filters")}").addHeader(RawHeader("Authorization", s"bearer $sid"))
        val response = Source.single(request).via(userProfileEndpoint).runWith(Sink.head).flatMap { resp =>
          Unmarshal(resp.entity).to[String].map { str =>
            val JArray(xs) = parse(str) \ "esQueries"
            xs
          }
        }
        onComplete(response).flatMap {
          case scala.util.Success(esQueries) => provide(esQueries)
          case scala.util.Failure(ex) =>
            system.log.error(ex,"unable to get userFilters")
            reject
        }
      }
    }
  }

  def logsfilter(filterId: Option[String]): Directive1[JValue] = {

    headerValueByName("uid").flatMap { uid =>
      userSid.flatMap { sid =>

        extractMaterializer.flatMap { implicit mat =>

          val request = RequestBuilding.Get(s"/${config.getString("service.dapi.logsfilter")}?logsFilterId=${filterId.getOrElse("")}").addHeader(RawHeader("Authorization", s"bearer $sid"))
          val response = Source.single(request).via(userProfileEndpoint).runWith(Sink.head).flatMap { resp =>
            Unmarshal(resp.entity).to[String].map { str => parse(str) \ "esQuery" }
          }
          onComplete(response).flatMap {
            case scala.util.Success(esQueries) => provide(esQueries)
            case scala.util.Failure(ex) =>
              system.log.error(ex,"unable to get userFilters")
              reject
          }
        }
      }
    }
  }

}
