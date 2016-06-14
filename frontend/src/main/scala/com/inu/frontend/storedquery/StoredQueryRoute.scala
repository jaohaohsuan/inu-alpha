package com.inu.frontend.storedquery

import com.inu.frontend.CollectionJsonSupport
import spray.routing._
import spray.http.StatusCodes._
import spray.httpx.unmarshalling._
import com.inu.protocol.storedquery.messages._
import org.json4s._

trait StoredQueryRoute extends HttpService with CollectionJsonSupport {

  implicit def client: org.elasticsearch.client.Client

  def postClause[A <: BoolClause ](name: String)(implicit storedQueryId: String, um: FromRequestUnmarshaller[A]): Route = {
    entity(as[A]) { entity => implicit ctx: RequestContext =>
      actorRefFactory.actorOf(AddClauseRequest.props(entity))
    }
  }
  def clausePath[A <: AnyRef](name: String)(e: A, kvp : (String, String)* ): Route = {
    path(name) {
      requestUri { uri =>
        import com.inu.protocol.media.CollectionJson.Template
        complete(OK, JField("href", JString(s"$uri")) :: JField("template", Template(e, kvp.toMap).template) :: Nil)
      }
    }
  }

  lazy val `_query/template/`: Route =
    get {
      path("_query" / "template") {
        parameters('q.?, 'tags.?, 'size.as[Int] ? 10, 'from.as[Int] ? 0 ) { (q, tags, size, from) =>
          requestUri { uri => implicit ctx =>
            complete(OK)
            actorRefFactory.actorOf(SearchRequest.props(q, tags, size, from))
          }
        }
      } ~
      pathPrefix("_query" / "template" / Segment) { implicit storedQueryId =>
        pathEnd { implicit ctx =>
          actorRefFactory.actorOf(GetItemRequest.props)
        } ~
        clausePath("near")(SpanNearClause("hello inu", "dialogs", 10, false, "must"), ("query", "it must contain at least two words")) ~
        clausePath("match")(MatchClause("hello inu", "dialogs", "or", "must_not")) ~
        clausePath("named")(NamedClause("temporary","query", "should"))
      }
    } ~
    post {
      pathPrefix("_query" / "template") {
        pathEnd {
          entity(as[NewTemplate]) { implicit entity => implicit ctx =>
            actorRefFactory.actorOf(NewTemplateRequest.props)
          }
        } ~
        pathPrefix(Segment) { implicit id =>
          postClause[NamedClause]("named") ~
          postClause[MatchClause]("match") ~
          postClause[SpanNearClause]("near")
        }
      }
    } ~
    put {
      pathPrefix("_query" / "template") {
        path(Segment) { implicit  storedQueryId =>
          entity(as[StoredQueryItem]) { entity => implicit ctx =>
            actorRefFactory.actorOf(ApplyUpdateRequest.prop(entity))
          }
        }
      }
    }
}
