package frontend.mapping

import frontend.{CollectionJsonSupport}
import org.elasticsearch.cluster.metadata.IndexTemplateMetaData
import spray.routing.{Route, HttpService}
import spray.http.StatusCodes._
import read.storedQuery.StoredQueryIndex._
import elastic.ImplicitConversions._
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success}

trait MappingRoute extends HttpService with CollectionJsonSupport {


  def getIndexTemplate(id: String = "clogs1"): Future[IndexTemplateMetaData] =
    client.admin()
      .indices()
      .prepareGetTemplates(id)
      .execute()
      .asFuture.map(_.getIndexTemplates.headOption).filter(_.isDefined).map(_.get)

  lazy val `_mapping/`: Route =
    get {
      requestUri {  uri =>
        pathPrefix( "_mapping" ) {
          pathEnd { ctx =>
            getIndexTemplate().onComplete {
              case Success(x) =>
                val types = x.getMappings().map(m =>
		            s"""{
                | "href" : "${uri}/${m.key}",
                | "data" : [
                |   { "name" : "name" , "value": "${m.key}"}
                | ]
                |}""".stripMargin).mkString(",")
              ctx.complete(OK,
                s"""{
                  | "collection" : {
                  |   "version" : "1.0",
                  |   "items" : [ $types ]
                  | }
                  |}""".stripMargin)

            case _ => ctx.complete(NoContent)
          }
        } ~
          path( Segment ) { t =>
            complete(OK)
          }
        }
      }
    }
}
