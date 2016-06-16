package com.inu.frontend.storedquery

import com.inu.frontend.CollectionJsonSupport
import spray.routing._
import spray.http.StatusCodes._
import spray.httpx.unmarshalling._
import com.inu.protocol.storedquery.messages._
import org.elasticsearch.action.get.GetResponse
import org.json4s.JsonAST.{JArray, JField, JString}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._

import scala.concurrent.{ExecutionContext, Future}

trait StoredQueryRoute extends HttpService with CollectionJsonSupport {

  implicit def client: org.elasticsearch.client.Client
  implicit def executionContext: ExecutionContext

  val aggRoot = actorRefFactory.actorSelection("/user/StoredQueryRepoAggRoot-Proxy")

  def addClause[A <: BoolClause ](name: String)(implicit storedQueryId: String, um: FromRequestUnmarshaller[A]): Route = {
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

  def item (id: String): Directive1[JValue] = {
    import com.inu.frontend.elasticsearch.ImplicitConversions._
    val f: Future[GetResponse] = client.prepareGet("stored-query", ".percolator", id).setFetchSource(Array("item", "occurs"), null).execute().future
    onComplete(f).flatMap {
      case scala.util.Success(res) => provide(parse(res.getSourceAsString()))
      case _ => reject
    }
  }

  lazy val `_query/template/`: Route =
    requestUri { uri =>
      get {
        path("_query" / "template") {
          parameters('q.?, 'tags.?, 'size.as[Int] ? 10, 'from.as[Int] ? 0 ) { (q, tags, size, from) => implicit ctx =>
            actorRefFactory.actorOf(SearchRequest.props(q, tags, size, from))
          }
        } ~
        pathPrefix("_query" / "template" / Segment) { implicit storedQueryId =>
          item(storedQueryId) { source =>
            pathEnd {
              val links: JObject = "links" -> Set(
                ("rel" -> "edit") ~~ ("href" -> s"${uri.withPath(uri.path / "match")}"),
                ("rel" -> "edit") ~~ ("href" -> s"${uri.withPath(uri.path / "near" )}"),
                ("rel" -> "edit") ~~ ("href" -> s"${uri.withPath(uri.path / "named")}"),
                ("rel" -> "section") ~~ ("href" -> s"${uri.withPath(uri.path / "must")}"),
                ("rel" -> "section") ~~ ("href" -> s"${uri.withPath(uri.path / "should")}"),
                ("rel" -> "section") ~~ ("href" -> s"${uri.withPath(uri.path / "must_not")}")
              )

              val items = JField("items", JArray((source \ "item" transformField {
                case JField("href", _) => ("href", JString(s"$uri"))
              }).merge(links) :: Nil))

              val href = JField("href", JString("""/\w+$""".r.replaceFirstIn(s"$uri", "")))
              val template = JField("template", "data" -> (source \ "item" \ "data"))
              complete(OK, href :: items :: template :: Nil)
            } ~
            path("""^must$|^must_not$|^should$""".r) { occur =>
              val items = source \ "occurs" \ occur transformField {
                case JField("href", JString(p)) => ("href", JString(s"""/$occur""".r.replaceFirstIn(s"$uri", p)))
              }
              complete(OK, JField("items", items) :: Nil)
            } ~
            clausePath("near")(SpanNearClause("hello inu", "dialogs", 10, false, "must"), ("query", "it must contain at least two words")) ~
            clausePath("match")(MatchClause("hello inu", "dialogs", "or", "must_not")) ~
            clausePath("named")(NamedClause("temporary","query", "should"))
          }
        }
      } ~
      post {
        pathPrefix("_query" / "template") {
          path("init") { implicit ctx =>
            actorRefFactory.actorOf(AdminRequest.props(Initial))
          } ~
          pathEnd {
            entity(as[NewTemplate]) { implicit entity => implicit ctx =>
              actorRefFactory.actorOf(NewTemplateRequest.props)
            }
          } ~
          pathPrefix(Segment) { implicit id =>
            addClause[NamedClause]("named") ~
            addClause[MatchClause]("match") ~
            addClause[SpanNearClause]("near")
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
      } ~
      delete {
        pathPrefix("_query" / "template" / Segment) { implicit storedQueryId =>
          path( """^match$|^near$|^named$""".r / IntNumber) { (clauseType, clauseId) => implicit ctx =>
            actorRefFactory.actorOf(RemoveClauseRequest.props(RemoveClauses(storedQueryId, List(clauseId))))
          } ~
          path( """^must$|^must_not$|^should$""".r ) { occur => implicit ctx =>
            actorRefFactory.actorOf(RemoveClauseRequest.props(ResetOccurrence(storedQueryId, occur)))
          }
        }
      }
    }
}
