package frontend.analysis

import frontend.CollectionJsonSupport
import frontend.analysis.cross.GetLogsRequest
import frontend.storedQuery.getRequest.{CollectionJsonBuilder, QueryStoredQueryRequest}
import org.elasticsearch.client.Client
import org.json4s
import spray.http.StatusCodes._
import spray.routing._
import spray.util._
import scalaz._
import Scalaz._

import scala.language.implicitConversions

trait AnalysisRoute extends HttpService with CollectionJsonSupport {

  implicit private val log = LoggingContext.fromActorRefFactory(actorRefFactory)
  implicit def client: Client

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
             |      { "href" : "$uri/cross",
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
          implicit def toSeq(p: Option[String]): Seq[String] = p.map(_.split("""(,|\s|\+)+""").toSeq).getOrElse(Seq.empty).filter(_.trim.nonEmpty)
          path("logs") {
            parameters('conditionSet.?, 'size.as[Int] ? 10, 'from.as[Int] ? 0 ) { (conditionSet, size, from) => implicit ctx =>
              actorRefFactory.actorOf(GetLogsRequest.props(conditionSet, size, from))
            }
          } ~
          path("source"){
            parameters('conditionSet.?, 'include.?, 'q.?, 'tags.?, 'size.as[Int] ? 10, 'from.as[Int] ? 0 ) { (conditionSet, includable, q, tags, size, from) => implicit ctx =>
              //log.info(s"$exclude")
              val b = new CollectionJsonBuilder {
                def body(hits: Iterable[json4s.JValue], tags: String, pagination: Seq[String]): String = {
                  import frontend.UriImplicitConversions._
                  val prefix = uri.drop("q", "tags", "size", "from")

                  val `_query/template` = spray.http.Uri.Path("/_query") / "template"
                  val itemPrefix = uri.withQuery(Map.empty[String, String]).withPath(`_query/template`)

                  val items = itemsMap(hits).flatMap(_.map { case (id, data) =>

                    val action = s"${prefix.append("include", id)}".replaceFirst("""/source""", "")
                    s"""{"href":"$itemPrefix/$id","data":$data,"links":[{"href":"$action","rel":"action"}]}"""
                  })
                  val links = pagination
                  s"""{
                     | "collection" : {
                     |   "version" : "1.0",
                     |   "href" : "$prefix",
                     |
                     |   "links" : [
                     |      ${links.filter(_.trim.nonEmpty).mkString(",")}
                     |   ],
                     |
                     |   "queries" : [ {
                     |      "href" : "$prefix",
                     |      "rel" : "search",
                     |      "data" : [
                     |        { "name" : "q", "prompt" : "search title or any terms" },
                     |        { "name" : "tags", "prompt" : "$tags " },
                     |        { "name" : "size", "prompt" : "size of displayed items" },
                     |        { "name" : "from", "prompt" : "items display from" }
                     |      ]
                     |    } ],
                     |
                     |   "items" : [ ${items.mkString(",")} ]
                     | }
                     |}""".stripMargin
                }
              }
              val exclude = (conditionSet: Seq[String]) ++ includable
              actorRefFactory.actorOf(CrossAnalysisSourceRequest.props(exclude, b)(q, tags, size, from))
            }
          } ~
          path("graph0") {
            parameters('conditionSet.?, 'include.?) { (conditionSet, includable) => implicit ctx =>
              actorRefFactory.actorOf(ConditionSetBarChartRequest.props(conditionSet))
            }
          } ~
          path("graph1") {
            parameters('conditionSet.?, 'include.?) { (conditionSet, includable) => implicit ctx =>
              actorRefFactory.actorOf(CrossAnalysisLineChartRequest.props(conditionSet, includable))
            }
          } ~
          pathEnd {
            parameters('conditionSet.?, 'include.?) { (conditionSet, includable) => implicit ctx =>
              actorRefFactory.actorOf(CrossAnalysisRequest.props(conditionSet, includable, exclude = conditionSet))
            }
	        }
        }
      }
    }
  }
}
