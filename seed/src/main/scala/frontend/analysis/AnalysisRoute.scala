package frontend.analysis

import frontend.CollectionJsonSupport
import frontend.storedQuery.getRequest.QueryStoredQueryRequest
import org.elasticsearch.client.Client
import spray.http.StatusCodes._
import spray.routing._
import spray.util._

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
          implicit def toSeq(p: Option[String]): Seq[String] =
            p.map(_.split("""(\s|\+)+""").toSeq).getOrElse(Seq.empty).filter(_.trim.nonEmpty)
          path("logs") {
            complete(ServiceUnavailable)
          } ~
          path("source"){
            parameters('conditionSet.?, 'include.?, 'q.?, 'tags.?, 'size.as[Int] ? 10, 'from.as[Int] ? 0 ) { (conditionSet, include, q, tags, size, from) => implicit ctx =>
              val exclude: Seq[String] = conditionSet ++ include

              log.info(s"$exclude")

              actorRefFactory.actorOf(CrossAnalysisSourceRequest.props(exclude)(q, tags, size, from))
            }
          } ~
          pathEnd {
            parameters('conditionSet.?, 'include.?) { (conditionSet, include) => implicit ctx =>
              actorRefFactory.actorOf(CrossAnalysisRequest.props(conditionSet, include, exclude = conditionSet))
            }
	        }
        }
      }
    }
  }
}
