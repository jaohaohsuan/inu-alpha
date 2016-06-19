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
import com.inu.frontend.UriImplicitConversions._
import com.inu.frontend.storedquery.directive.{LogsDirectives, StoredQueryDirectives}
import com.inu.frontend.elasticsearch.ImplicitConversions._
import com.inu.protocol.media.CollectionJson.Template

import scala.concurrent.{ExecutionContext, Future}

trait StoredQueryRoute extends HttpService with CollectionJsonSupport with LogsDirectives with StoredQueryDirectives {

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
                ("rel" -> "edit") ~~    ("href" -> s"${uri / "match"}"),
                ("rel" -> "edit") ~~    ("href" -> s"${uri / "near"}") ,
                ("rel" -> "edit") ~~    ("href" -> s"${uri / "named"}"),
                ("rel" -> "section") ~~ ("href" -> s"${uri / "must"}") ~~ ("name" -> "must"),
                ("rel" -> "section") ~~ ("href" -> s"${uri / "should"}") ~~ ("name" -> "should"),
                ("rel" -> "section") ~~ ("href" -> s"${uri / "must_not"}") ~~ ("name" -> "must_not"),
                ("rel" -> "preview") ~~ ("href" -> s"${uri / "preview"}") ~~ ("name" -> "preview") ~~ ("data" -> Set(
                  ("name" -> "size") ~~ ("prompt" -> "size of displayed items"),
                  ("name" -> "from") ~~ ("prompt" -> "items display from")
                ))
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
            clausePath("named")(NamedClause("temporary","query", "should")) ~
            pathPrefix("preview") {
              prepareSearch(source \ "query") { sb =>
                pathEnd { implicit ctx =>
                  actorRefFactory.actorOf(PreviewRequest.props(sb))
                } ~
                path("status") {
                  onSuccess(sb.setSize(0).execute().future){ res =>
                    val href = JField("href", JString(s"$uri".replaceFirst("""\/status""", "")))
                    val item = Template(PreviewStatus(res.getHits.getTotalHits, pretty(render(source \ "query")))).template ~~ ("href" -> s"$uri")
                    val items = JField("items", JArray(item :: Nil))
                    complete(OK, href :: items :: Nil)
                  }
                }
              }
            }
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
