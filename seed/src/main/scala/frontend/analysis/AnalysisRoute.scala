package frontend.analysis

import java.util

import akka.actor.ActorLogging
import es.indices.storedQuery
import frontend.CollectionJsonSupport
import org.elasticsearch.action.count.CountResponse
import org.elasticsearch.client.Client
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

case class Condition(storedQueryId: String, title: String, query: String, state: String = "", hits: Long = 0)

trait AnalysisRoute extends HttpService with CollectionJsonSupport {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit private val log = LoggingContext.fromActorRefFactory(actorRefFactory)
  implicit def client: Client

  def count(c: String, state: String, conditions: Iterable[String])(implicit queries: Map[String, Condition], client: Client) = {
    val qb = conditions.flatMap(queries.get).foldLeft(boolQuery()){ (acc, c) => acc.must(wrapperQuery(c.query)) }
    client.prepareCount("clogs*")
      .setQuery(qb)
      .execute()
      .asFuture
      .map { resp =>
        queries.getOrElse(c, Condition(c, c, "")).copy(hits = resp.getCount, state = state)
      }
  }

  def crossQuery(conditionSet: Seq[String], include: Seq[String], exclude: Seq[String]): Future[Seq[Condition]] = {

    import storedQuery._
    import scala.collection.JavaConversions._

    prepareSearch
      .setQuery(idsQuery(".percolator").addIds(conditionSet ++ include ++ exclude))
      .setFetchSource(Array("query", "title"), null)
      .execute
      .asFuture
      .map { resp => resp.getHits.foldLeft(Map.empty[String, Condition]){ (acc, h) =>
        val source = parse(h.sourceAsString())
        val title = (source \ "title").extract[String]
        val query = compact(render(source \ "query"))
        acc + (h.id() -> Condition(h.id(), title, query))}
    }.flatMap { implicit queries =>
       for {
         excluded <- Future.traverse(exclude) { c => count(c, "excluded", conditionSet.filterNot(_ == c )) }
         included <- Future.traverse(include) { c => count(c, "included", conditionSet.+:(c))}
         set <- count("set", "set", conditionSet)
       } yield excluded ++ included ++ Seq(set)
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
                val items = x.map { case Condition(storedQueryId, title, _, state, hits) =>
                  s"""{
                     | "data" : [
                     |  { "name" : "hits", "value" : $hits }, { "name" : "title", "value" : "$title" }, { "name" : "state", "value" : "$state"}
                     | ]
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
