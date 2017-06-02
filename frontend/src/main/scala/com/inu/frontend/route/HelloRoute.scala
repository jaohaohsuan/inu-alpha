package com.inu.frontend.route

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import com.inu.frontend.service.ElasticsearchClientService.ElasticsearchClientFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Created by henry on 6/1/17.
  */
class HelloRoute(implicit val esClientFactory: ElasticsearchClientFactory, actorSystem: ActorSystem) extends BaseRoute {

  private implicit val esClient = esClientFactory()
  implicit val ec: ExecutionContext = actorSystem.dispatcher

  import akka.http.scaladsl.model.HttpMethods._
  import com.inu.frontend.utils.http.HttpRequestBodyEncoder._

  def determineHealth(implicit mat: Materializer): Flow[HttpResponse, String, NotUsed] = Flow[HttpResponse].mapAsync(1) {
    case HttpResponse(StatusCodes.OK, _, entity, _) =>
      Unmarshal(entity).to[String].filter {
        case s if s matches """(?i)^(?!.*red)[\s\S]*$""" => true
        case _ => false
      }
    case res: HttpResponse =>
      Future.failed(new Exception(s"${res.entity.toString}"))

    case _ => Future.failed(new Exception("unexpected error"))
  }


  def doRoute(implicit mat: Materializer): Route = {
    get {
      path("hello") {
        onComplete(esClient.send(GET / "/_cat/health").via(determineHealth).runWith(Sink.head)) {
          case Success(result) => complete(OK, result)
          case Failure(ex) => complete((InternalServerError, s"An error occurred: ${ex.getMessage}"))
        }
      }
    }
  }
}
