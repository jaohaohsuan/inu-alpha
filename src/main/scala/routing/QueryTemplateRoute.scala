package routing

import java.util.UUID

import akka.actor.{Terminated, ActorRef, Props}
import akka.pattern._
import akka.contrib.pattern.ClusterSingletonProxy
import akka.util.Timeout
import spray.http.StatusCodes._
import spray.routing.{HttpService, RequestContext}
import worker.{Master, Work}
import scala.concurrent.duration._

object QueryTemplateRoute {

  def props(templateId: String, value: NamedClause)(implicit ctx: RequestContext):Props =
    Props(classOf[AddNamedClauseRequest], ctx, templateId, value)



  case class NewTemplate(name: String)
  case class NamedClause(id: String, occur: String)

  case class NewTemplateRequest(ctx: RequestContext, job: NewTemplate) extends PerRequest {

    def nextWorkId(): String = UUID.randomUUID().toString

    val masterProxy = context.actorOf(ClusterSingletonProxy.props(
      singletonPath = "/user/master/active",
      role = Some("backend")
    ), name = "masterProxy")


    masterProxy ! Work(nextWorkId(), job)

    def processResult = {
      case Master.Ack(_) =>
        response {
          complete(Accepted)
        }
    }
  }

  case class AddNamedClauseRequest(ctx: RequestContext, templateId: String, value: NamedClause) extends PerRequest {
    import domain.QueryTemplateGraph._
    import context.dispatcher

    def nextWorkId(): String = UUID.randomUUID().toString

    val queryTemplateGraphProxy = context.actorOf(ClusterSingletonProxy.props(
      singletonPath = "/user/queryTemplateGraph/active",
      role = Some("backend")
    ), name = "queryTemplateGraphProxy")

    val masterProxy = context.actorOf(ClusterSingletonProxy.props(
      singletonPath = "/user/master/active",
      role = Some("backend")
    ), name = "masterProxy")

    queryTemplateGraphProxy ! AddNamedClause((UUID.fromString(templateId), UUID.fromString(value.id)))

    def processResult: Receive = {
      case job: PropagatingRequest =>
        implicit val timeout = Timeout(5.seconds)
        (masterProxy ? Work(nextWorkId(), job)).map {
          case Master.Ack(_) => Ok
        }.recover { case _ =>  NotOk } pipeTo sender()
      case Ack =>
        response {
          complete(NoContent)
        }
    }
  }
}

trait QueryTemplateRoute extends HttpService {

  import QueryTemplateRoute._
  import util.CollectionJsonSupport._

  val queryTemplateRoute =
    post {
      path("_query" / "template") {
        entity(as[NewTemplate]){ template =>
          ctx =>
            actorRefFactory.actorOf(Props(classOf[NewTemplateRequest], ctx, template))
        }
      }
    } ~
    put {
      path("_query" / "template" / Segment) { templateId =>
        path("named") {
          entity(as[NamedClause]) { namedClause =>
            implicit ctx =>
              actorRefFactory.actorOf(QueryTemplateRoute.props(templateId, namedClause))
          }
        }
      }
    } ~
    get {
      path("api") {
        complete("hello spray.io")
      }
    }



}
