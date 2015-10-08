package frontend.logs

import frontend.WebvttSupport
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.search.highlight.HighlightBuilder
import spray.routing._
import spray.http.StatusCodes._
import elastic.ImplicitConversions._
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilders, QueryBuilder}
import es.indices.logs
import spray.util.LoggingContext
import scala.collection.JavaConversions._
import scala.language.implicitConversions
import scala.util.{Failure, Success}


trait LogsRoute extends HttpService with WebvttSupport{

  implicit def client: org.elasticsearch.client.Client

  import es.indices.storedQuery._
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit private val log = LoggingContext.fromActorRefFactory(actorRefFactory)

  implicit class Logs(r: GetRequest) {

    implicit def paramsToQuery(value: Map[String, String]): QueryBuilder = {
      import QueryBuilders._
      def must(bool: BoolQueryBuilder, param: (String, String)) = {
        val (field, text) = param
        bool.must(matchQuery(field, text))
      }
      value.foldLeft(boolQuery())(must)
    }

    def percolate(implicit params: Map[String, String]) = {
      preparePercolate(r.`type`())
        .setGetRequest(r)
        .setPercolateQuery(params)
        .setSize(10)
        .setHighlightBuilder(new HighlightBuilder().requireFieldMatch(true)
          .field("dialogs").numOfFragments(0).preTags("<c>").postTags("</c>")
          .field("agent*").numOfFragments(0).preTags("<c>").postTags("</c>")
          .field("customer*").numOfFragments(0).preTags("<c>").postTags("</c>"))
        .execute().asFuture.zip(logs.prepareGet(r).asFuture)
      .map { z =>

        val (percolateResp, logs.VttField(vtt)) = z

        log.info(s"$percolateResp")
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
        vtt ++ percolateResp.getMatches()
          .flatMap { m => m.getHighlightFields }
          .flatMap { case (_, hf) => hf.fragments().flatMap(splitFragment) }
          .flatMap { substitute(vtt)(_) }
      }
    }
  }


  lazy val `logs-*`: Route = {
    get {
      path(Segment / Segment / Segment ) { (index, `type`, id) =>
        parameterMap { implicit params => {
          import org.elasticsearch.client.Requests._
          onComplete(getRequest(index).`type`(`type`).id(id).percolate) {
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
