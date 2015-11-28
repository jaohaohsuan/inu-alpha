package frontend.logs

import elastic.ImplicitConversions._
import es.indices.logs
import frontend.{ImplicitHttpServiceLogging, WebvttSupport}
import org.elasticsearch.action.percolate.PercolateResponse.Match
import spray.http.StatusCodes._
import spray.routing._
import scala.collection.JavaConversions._
import scala.language.implicitConversions
import scala.util.{Failure, Success}

trait LogsRoute extends HttpService with WebvttSupport with ImplicitHttpServiceLogging {

  implicit def client: org.elasticsearch.client.Client

  implicit class Vtt(vtt: Map[String, String]) {
    import es.indices.logs.SearchHitHighlightFields._

    private def substitute(txt: String): Option[(String, String)] = txt match {
      case highlightedSentence(cueid, highlight) =>
        for {
          subtitle <- vtt.get(cueid)
          tag <- party.findFirstIn(cueid).map { txt => s"c.$txt" }
        } yield cueid -> subtitle.replaceAll( insideTagV, s"$$1$highlight$$2").replaceAll("""(?<=\<)c(?=>)""", tag)
      case _ =>
        log.warning(s"'$txt' can not match with highlightedSentence.")
        None
    }

    def append(matches: Iterable[Match]): Map[String,String] = {
      //log.info(s"PercolateResponse.Matches: ${matches.size}")
      vtt ++ matches
        .flatMap(_.getHighlightFields)
        .flatMap { case (_, hf) => hf.fragments().flatMap(splitFragment) }
        .flatMap(substitute)
    }
  }

  implicit class Logs(r: (String, String, String)) {
    def percolate(filters: String) = {

      import es.indices.storedQuery._
      val (index, typ, id) = r

      val source = s"""{
        |    "filter" : $filters,
        |    "doc" : %s,
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

      def prepareGetLog = client.prepareGet(index,typ,id)
                                .setFields("vtt")
                                .setFetchSource(Array("dialogs", "agent*", "customer*"), null)

      for {
        response <- prepareGetLog.execute().future
        logs.VttField(vtt) = response
        doc = response.getSourceAsString
        matches <- preparePercolate(typ)
                            //.setGetRequest(client.prepareGet(index,typ,id).request())
                            .setSource(source.format(doc)).execute().future.map(_.getMatches)
      } yield vtt.append(matches)
    }
  }

  lazy val `logs-*`: Route = {
    get {
      path(Segment / Segment / Segment ) { (index, `type`, id) =>
        parameters('_id) { storedQueryId => {
          val idsQuery = s""""ids" : { "type" : ".percolator", "values" : [ "$storedQueryId" ] } """
          onComplete((index, `type`, id).percolate(s"""{ $idsQuery }""")) {
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
