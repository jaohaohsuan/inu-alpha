package com.inu.frontend.storedquery

import com.inu.frontend.CollectionJsonSupport
import spray.routing._
import spray.http.StatusCodes._

trait StoredQueryRoute extends HttpService with CollectionJsonSupport {

  implicit def client: org.elasticsearch.client.Client

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
        }
      }
    } ~
    post {
      pathPrefix("_query" / "template") {
        pathEnd {
          entity(as[NewTemplate]) { implicit entity => implicit ctx =>
            actorRefFactory.actorOf(NewTemplateRequest.props)
          }
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
