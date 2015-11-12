package frontend.analysis.cross

import akka.actor.Props
import es.indices.{ logs, storedQuery }
import es.indices.storedQuery._
import frontend.{ Pagination, PerRequest }
import frontend.analysis.StoredQueryQuery
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.index.query.QueryBuilders._
import org.elasticsearch.index.query.{ BoolQueryBuilder, QueryBuilder, QueryBuilders, WrapperQueryBuilder }
import org.json4s.{ DefaultFormats, Formats }
import elastic.ImplicitConversions._
import shapeless.HNil
import spray.http.StatusCodes._
import spray.http.Uri.Path
import spray.routing._
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.language.implicitConversions

object GetLogsRequest {
  def props(implicit ctx: RequestContext, client: org.elasticsearch.client.Client) =
    Props(classOf[GetLogsRequest], ctx, client)
}

case class GetLogsRequest(ctx: RequestContext, implicit val client: org.elasticsearch.client.Client) extends PerRequest {

  import storedQuery._
  import akka.pattern._
  import context.dispatcher
  import shapeless._
  import logs.SearchRequestBuilder0

  implicit def toSeq(p: Option[String]): Seq[String] = p.map(_.split("""(,|\s|\+)+""").toSeq).getOrElse(Seq.empty).filter(_.trim.nonEmpty)

  def getQuery(conditionSet: Seq[String], zero: BoolQueryBuilder) = prepareSearchStoredQueryQuery(conditionSet).execute.asFuture
    .map(_.getHits.foldLeft(zero) { (acc, h) =>
      StoredQueryQuery.unapply(h) match {
        case StoredQueryQuery(title, q) if q.nonEmpty => acc.must(QueryBuilders.wrapperQuery(q))
        case _ => acc
      }
    })

  val queryParams = parameters('conditionSet.?, 'size.as[Int] ? 10, 'from.as[Int] ? 0, 'type.?)

  val search = queryParams.hmap {
    case conditionSet :: size :: from :: typ :: HNil =>

      val zero = typ match {
        case None => boolQuery()
        case Some(value) =>
         boolQuery().filter(value.split("""(\s+|,)""").foldLeft(boolQuery()){ (acc, t) => acc.should(QueryBuilders.typeQuery(t))})
      }

      for {
        query <- getQuery(conditionSet, zero)
        searchResponse <- logs.prepareSearch().setQuery(query).setSize(size).setFrom(from).setVttHighlighter.execute().asFuture
      } yield (searchResponse, size, from, conditionSet.getOrElse(""))
  }

  search { result => _ => result pipeTo self } (ctx)

  def processResult = {
    case (r: SearchResponse, size: Int, from: Int, conditionSet: String) =>
      response {
          requestUri { implicit uri =>
          import Pagination._
          val `HH:mm:ss.SSS` = org.joda.time.format.DateTimeFormat.forPattern("HH:mm:ss.SSS")
          def startTime(value: logs.VttHighlightFragment): Int = `HH:mm:ss.SSS`.parseDateTime(value.start).getMillisOfDay

          val links = Pagination(size, from, r).links.filterNot(_.isEmpty).mkString(",")

          val items = r.getHits.map {
            case logs.SearchHitHighlightFields(location, fragments) =>
              s"""{
               |  "href" : "${uri.withPath(Path(s"/$location")).withQuery(("_id", conditionSet))}",
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
