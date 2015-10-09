package frontend.logs

import elastic.ImplicitConversions._
import es.indices.logs
import frontend.WebvttSupport
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilder, QueryBuilders}
import spray.http.StatusCodes._
import spray.routing._
import spray.util.LoggingContext

import scala.collection.JavaConversions._
import scala.language.implicitConversions
import scala.util.{Failure, Success}


trait LogsRoute extends HttpService with WebvttSupport{

  implicit def client: org.elasticsearch.client.Client
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit private val log = LoggingContext.fromActorRefFactory(actorRefFactory)

  implicit class Logs(r: (String, String, String)) {

    implicit def paramsToQuery(value: Map[String, String]): QueryBuilder = {
      import QueryBuilders._
      def must(bool: BoolQueryBuilder, param: (String, String)) = {
        val (field, text) = param
        bool.must(matchQuery(field, text))
      }
      value.foldLeft(boolQuery())(must)
    }

    def percolate(filters: String) = {

      val (index, typ, id) = r

      val source = s"""{
        |    "filter" : $filters,
        |    "size" : 10,
        |    "highlight" : {
        |        "pre_tags" : ["<c>"],
        |        "post_tags" : ["</c>"],
        |        "require_field_match" : true,
        |        "fields" : {
        |            "agent*" :    { "number_of_fragments" : 0},
        |            "customer*" : { "number_of_fragments" : 0},
        |            "dialogs" :   { "number_of_fragments" : 0}
        |        }
        |    }
        |}""".stripMargin

      val xxx = client.preparePercolate()
        .setIndices("stored-query")
        .setDocumentType("ytx")
        .setGetRequest(client.prepareGet(index,typ,id).request())
        .setSource(source).execute().asFuture

      //log.info(s"${XContentHelper.convertToJson(xxx.request().source(), true, true)}")

      import es.indices.logs.SearchHitHighlightFields._

      def substitute(vtt: Map[String, String])(txt: String): Option[(String, String)] = txt match {
        case highlightedSentence(cueid, highlight) =>
          for {
            subtitle <- vtt.get(cueid)
            tag <- party.findFirstIn(cueid).map { txt => s"c.$txt" }
          } yield cueid -> subtitle.replaceAll( insideTagV, s"$$1$highlight$$2").replaceAll("""(?<=\<)c(?=>)""", tag)
        case _ =>
          println(s"'$txt' can not match with highlightedSentence.")
          None
      }
      for {
        logs.VttField(vtt) <- client.prepareGet(index,typ,id).setFields("vtt").execute().asFuture
        percolateResp <- xxx
      } yield vtt ++ percolateResp.getMatches()
        .flatMap { m => m.getHighlightFields }
        .flatMap { case (_, hf) =>
          hf.fragments().flatMap(splitFragment) }
        .flatMap { substitute(vtt)(_) }
    }
  }


  lazy val `logs-*`: Route = {
    get {
      path(Segment / Segment / Segment ) { (index, `type`, id) =>
        parameters('_id) { storedQueryId => {

          val idsQuery = s""""ids" : { "type" : ".percolator", "values" : [ "$storedQueryId" ] } """

          log.info(idsQuery)

          onComplete((index, `type`, id).percolate(s"""[ { $idsQuery} ]""")) {
            case Success(value) =>
              respondWithMediaType(`text/vtt`) {
                complete(OK, value)
              }
            case Failure(ex) =>
              requestUri { uri =>
                log.error(ex, s"$uri")
                complete(InternalServerError)
              }
            }
          }
        }
      }
    }
  }
}
