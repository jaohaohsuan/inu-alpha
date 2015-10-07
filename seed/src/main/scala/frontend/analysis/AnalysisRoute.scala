package frontend.analysis

import java.util

import akka.actor.ActorLogging
import es.indices.storedQuery
import frontend.CollectionJsonSupport
import org.elasticsearch.action.count.CountResponse
import org.elasticsearch.client.Client
import spray.http.Uri
import spray.routing._
import spray.http.StatusCodes._
import org.elasticsearch.index.query.QueryBuilders._
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilder}
import elastic.ImplicitConversions._
import org.json4s
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.native.JsonMethods._
import scala.util.{ Success, Failure }
import scala.language.implicitConversions
import spray.util._

import scala.concurrent.Future

object Condition {
  def uncounted(storedQueryId: String, title: String, query: String) = Condition(storedQueryId, title, query, "", Seq.empty)
  def set(conditions: Seq[String]) = Condition("set", "set", "", "set", conditions)
}

case class Condition(storedQueryId: String, title: String, query: String, state: String = "", conditions: Iterable[String], hits: Long = 0) {

  def count(implicit queries: Map[String, Condition], client: Client) = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val qb = conditions.flatMap(queries.get).foldLeft(boolQuery()){ (acc, c) => acc.must(wrapperQuery(c.query)) }
    client.prepareCount("logs*")
      .setQuery(qb)
      .execute().asFuture.map { resp =>
      //set hits
      queries.getOrElse(storedQueryId, this).copy(hits = resp.getCount, state = state)
    }
  }
}

class ConditionSet(conditions: Seq[String]) {

  def exclude(storedQueryId: String): Condition =
    Condition(storedQueryId, storedQueryId, "", "excludable", conditions.filterNot(_ == storedQueryId))

  def include(storedQueryId: String): Condition =
    Condition(storedQueryId, storedQueryId, "", "includable", conditions.+:(storedQueryId))

  def all = Condition("set", "set", "", "set", conditions)

}

class CrossAnalysisUriQuery(uri: Uri) {

  def further(storedQueryId: String, state: String) = {
    state match {
      case "excludable" => exclude(storedQueryId)
      case "includable" => include(storedQueryId)
      case _ =>
        ""
    }
  }

  def include(storedQueryId: String) = {
    val map = Seq(Option(uri.query.get("include").getOrElse(storedQueryId).replace(storedQueryId, ""))
      .filter(_.trim.nonEmpty).map("include" -> _),
      Option(uri.query.get("conditionSet").getOrElse("") + storedQueryId)
        .filter(_.trim.nonEmpty).map("conditionSet" -> _)).flatten.toMap
    s""", "links" : [ {"rel" : "action", "href" : "${uri.withQuery(map)}", "prompt" : "include" } ]"""
  }

  def exclude(storedQueryId: String) = {
    val map = Seq(Option(uri.query.get("conditionSet").getOrElse(storedQueryId).replace(storedQueryId, ""))
      .filter(_.trim.nonEmpty)
      .map("conditionSet" -> _),
      Option(uri.query.get("include").getOrElse("") + storedQueryId)
        .filter(_.trim.nonEmpty)
        .map("include" -> _)).flatten.toMap

    s""", "links" : [ {"rel" : "action", "href" : "${uri.withQuery(map)}", "prompt" : "exclude" } ]"""
  }
}

trait AnalysisRoute extends HttpService with CollectionJsonSupport {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit private val log = LoggingContext.fromActorRefFactory(actorRefFactory)
  implicit def client: Client

  def crossQuery(conditionSet: Seq[String], include: Seq[String], exclude: Seq[String]): Future[Seq[Condition]] = {

    import storedQuery._
    import scala.collection.JavaConversions._

    implicit def seqStringToConditionSet(value: Seq[String]): ConditionSet = new ConditionSet(value)

    prepareSearch
      .setQuery(idsQuery(".percolator").addIds(conditionSet ++ include ++ exclude))
      .setFetchSource(Array("query", "title"), null)
      .execute.asFuture
      .map { resp => resp.getHits.foldLeft(Map.empty[String, Condition]){ (acc, h) =>
        val source = parse(h.sourceAsString())
        val title = (source \ "title").extract[String]
        val query = compact(render(source \ "query"))
        acc + (h.id() -> Condition.uncounted(h.id(), title, query))}
    }.flatMap { implicit queries =>
       for {
         excluded <- Future.traverse(exclude) { c => conditionSet.exclude(c).count }
         included <- Future.traverse(include) { c => conditionSet.include(c).count }
         set <- conditionSet.all.count
       } yield (excluded ++ included).:+(set)
    }
  }

  lazy val `_analysis`: Route =
  get {
    requestUri { uri =>
      pathPrefix( "_analysis" ) {
        pathEnd {
          val body =  s"""{
             |  "collection" : {
             |    "version" : "1.0",
             |    "href" : "$uri",
             |
             |    "queries" : [
             |      { "href" : "${uri}/cross",
             |        "rel" : "search",
             |        "data" : [
             |          { "name" : "conditionSet", "value" : "" },
             |          { "name" : "include", "value" : "" }
             |        ]
             |      }
             |    ]
             |  }
             |}
          """.stripMargin
          complete(OK, body)
        } ~
        pathPrefix("cross") {
          path("details") {
            complete(ServiceUnavailable)
          } ~
          pathEnd {
            parameters('conditionSet.?, 'include.?) { (conditionSet, include) =>  ctx =>

            implicit def paramsToSeq(value: Option[String]): Seq[String] = value.map(_.split("""\s+""").toSeq).getOrElse(Seq.empty[String])

            crossQuery(conditionSet, include, exclude = conditionSet).onComplete {
              case Success(x) =>

                implicit def uriTo(uri: Uri): CrossAnalysisUriQuery = new CrossAnalysisUriQuery(uri)

                val items = x.map { case Condition(storedQueryId, title, _, state, _, hits) =>

                  s"""{
                     | "data" : [
                     |  { "name" : "hits", "value" : $hits }, { "name" : "title", "value" : "$title" }, { "name" : "state", "value" : "$state"}
                     | ] ${uri.further(storedQueryId, state)}
                     |}""".stripMargin
                }.mkString(",")

                val body =
                  s"""{
                    |  "collection" : {
                    |    "version" : "1.0",
                    |    "href" : "$uri",
                    |    "links" : [
                    |     { "rel" : "more", "href" : "${uri.withPath(uri.path / "details")}" }
                    |    ],
                    |
                    |    "items" : [ $items ]
                    |  }
                    |}""".stripMargin

                ctx.complete(OK, body)
              case Failure(ex) =>
                log.error(ex, s"$uri")
                ctx.complete(InternalServerError,
                  s"""{
                     |  "error" : {
                     |    "message" : "${ex.getMessage}"
                     |  }
                     |}""".stripMargin)
              }
            }
	        }
        }
      }
    }
  }
}
