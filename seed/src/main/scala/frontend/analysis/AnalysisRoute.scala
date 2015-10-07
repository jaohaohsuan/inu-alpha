package frontend.analysis

import frontend.CollectionJsonSupport
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
          path("details") {
            complete(ServiceUnavailable)
          } ~
          pathEnd {
            parameters('conditionSet.?, 'include.?) { (conditionSet, include) => implicit ctx =>

            implicit def toSeq(p: Option[String]): Seq[String] = p.map(_.split("""\s+""").toSeq).getOrElse(Seq.empty)
              actorRefFactory.actorOf(CrossAnalysisRequest.props(conditionSet, include, exclude = conditionSet))
            }
	        }
        }
      }
    }
  }
}
