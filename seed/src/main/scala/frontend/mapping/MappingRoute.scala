package frontend.mapping

import frontend.{CollectionJsonSupport}
import org.elasticsearch.cluster.metadata.IndexTemplateMetaData
import spray.routing.{Route, HttpService}
import spray.http.StatusCodes._
import elastic.ImplicitConversions._
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success}
import es.indices.logs
import org.json4s._
import org.json4s.native.JsonMethods._

trait MappingRoute extends HttpService with CollectionJsonSupport {

  implicit def client: org.elasticsearch.client.Client

  def getTemplate: Future[IndexTemplateMetaData] =
      logs.getTemplate.asFuture.map(_.getIndexTemplates.headOption).filter(_.isDefined).map(_.get)

  lazy val `_mapping/`: Route =
    get {
      requestUri {  uri =>
        pathPrefix( "_mapping" ) {
          pathEnd { ctx =>
            getTemplate.onComplete {
              case Success(x) =>
                val types = x.getMappings().map(m =>
		            s"""{
                | "href" : "${uri}/${m.key}",
                | "data" : [
                |   { "name" : "type" , "value" : "${m.key}"}
                | ]
                |}""".stripMargin).mkString(",")
              ctx.complete(OK,
                s"""{
                  | "collection" : {
                  |   "version" : "1.0",
                  |   "href" : "$uri",
                  |   "items" : [ $types ]
                  | }
                  |}""".stripMargin)

            case _ => ctx.complete(NotFound)
          }
        } ~
          path( Segment ) { t => ctx =>
            getTemplate.map(_.mappings()).filter(_.containsKey(t)).map(_.get(t)).onComplete {
              case Success(x) =>
                val mapping = parse(s"$x")

                val JsonAST.JObject(xs) = mapping \\ "properties"
                val properties =  xs.map {
                  case (field, detail) =>
                    s"""{
                      | "href" : "$uri/$field",
                      | "data" : [
                      |   { "name" : "field", "value" : "$field" },
                      |   { "name" : "type", "value" : "${(detail \\ "type").extract[String]}" }
                      | ]
                      |}""".stripMargin
                }.mkString(",")

                val content = s"""{
                  | "collection" : {
                  |   "version" : "1.0",
                  |   "href" : "${uri}",
                  |   "items" : [ $properties ]
                  | }
                  |}""".stripMargin

                ctx.complete(OK, content)
              case _ => ctx.complete(NotFound)
            }
          }
        }
      }
    }
}
