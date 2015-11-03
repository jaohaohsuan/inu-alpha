package frontend.analysis.cross

import akka.actor.Props
import es.indices.{logs, storedQuery}
import es.indices.storedQuery._
import frontend.{Pagination, PerRequest}
import frontend.analysis.StoredQueryQuery
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.index.query.QueryBuilders._
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilder, QueryBuilders, WrapperQueryBuilder}
import org.json4s.{DefaultFormats, Formats}
import elastic.ImplicitConversions._
import spray.http.StatusCodes._
import spray.http.Uri.Path
import spray.routing.RequestContext
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.language.implicitConversions

object GetLogsRequest {
  def props(conditionSet: Seq[String], size: Int = 10, from: Int = 0)(implicit ctx: RequestContext, client: org.elasticsearch.client.Client) =
    Props(classOf[GetLogsRequest], ctx, client, conditionSet, size, from)
}

case class GetLogsRequest(ctx: RequestContext, implicit val client: org.elasticsearch.client.Client, conditionSet: Seq[String], size: Int, from: Int) extends PerRequest{

  import storedQuery._
  import akka.pattern._
  import context.dispatcher
  import logs.SearchRequestBuilder0

  def getQuery = prepareSearchStoredQueryQuery(conditionSet).execute.asFuture
    .map(_.getHits.foldLeft(boolQuery()){ (acc, h) =>
      StoredQueryQuery.unapply(h) match {
        case StoredQueryQuery(title, q) if q.nonEmpty =>  acc.must(QueryBuilders.wrapperQuery(q))
        case _ => acc
      }})

  (for {
    query <- getQuery
    hits <- logs.prepareSearch().setQuery(query).setSize(size).setFrom(from).setVttHighlighter.execute().asFuture
  } yield hits) pipeTo self

  def processResult = {
    case r: SearchResponse =>
      response {
        requestUri { implicit uri =>
          import Pagination._
          val `HH:mm:ss.SSS` = org.joda.time.format.DateTimeFormat.forPattern("HH:mm:ss.SSS")
          def startTime(value: logs.VttHighlightFragment): Int =
            `HH:mm:ss.SSS`.parseDateTime(value.start).getMillisOfDay

          val links = Pagination(size, from, r).links.filterNot(_.isEmpty).mkString(",")

          val items = r.getHits.map { case logs.SearchHitHighlightFields(location, fragments) =>
            s"""{
               |  "href" : "${uri.withPath(Path(s"/$location")).withQuery(("_id", conditionSet.mkString(" ")))}",
               |  "data" : [
               |    { "name" : "highlight", "array" : [ ${fragments.toList.sortBy { e => startTime(e) }.map { case logs.VttHighlightFragment(start, keywords) => s""""$start $keywords"""" }.mkString(",")} ] },
               |    { "name" : "keywords" , "value" : "${fragments.flatMap { _.keywords.split("""\s""") }.toSet.mkString(" ")}" }
               |  ]
               |}""".stripMargin
          }.mkString(",")

          complete(OK,
            s"""{
               |   "collection" : {
               |     "version" : "1.0",
               |     "href" : "$uri",
               |     "links" : [ $links ],
               |
              |     "items" : [ $items ]
               |   }
               |}""".stripMargin)
        }
      }

  }
}
