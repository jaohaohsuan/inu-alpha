package frontend.mapping

import frontend.{CollectionJsonSupport}
import spray.routing.{Route, HttpService}
import spray.http.StatusCodes._

trait MappingRoute extends HttpService with CollectionJsonSupport {

  lazy val `_mapping/`: Route =
    get {
      pathPrefix( "_mapping" / Segment ) { t =>
        pathEnd {
          complete(OK,
            """{
              | "collection" : {}
              |}""".stripMargin)
        }
      }
    }
}
