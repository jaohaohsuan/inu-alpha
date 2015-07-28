package routing

import elastics.LteIndices.VttField
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.get.{GetRequest, GetResponse}
import org.elasticsearch.action.percolate.PercolateResponse
import org.elasticsearch.client.{Client, Requests}
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilders}
import org.elasticsearch.search.highlight.HighlightBuilder
import spray.http.StatusCodes._
import spray.routing.HttpService
import util.{CorsSupport, WebvttSupport}

import scala.collection.JavaConversions._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}


trait SearchPreviewRoute extends HttpService with WebvttSupport with CorsSupport {

  import scala.concurrent.ExecutionContext.Implicits.global

  def client: Client

  def getDoc(getRequest: GetRequest) = {
    val p = Promise[GetResponse]()
    val listener = new ActionListener[GetResponse] {
      def onFailure(e: Throwable): Unit = p.tryFailure(e)
      def onResponse(resp: GetResponse): Unit = p.trySuccess(resp)
    }
    client.get(getRequest.fields("vtt"), listener)
    p.future
  }

  def percolate(getRequest: GetRequest, params: Map[String, String]): Future[PercolateResponse] = {

    def must(bool: BoolQueryBuilder, param: (String, String)) =
      bool.must(QueryBuilders.matchQuery(param._1, param._2))

    val q = params.foldLeft(QueryBuilders.boolQuery())(must)
    val percolateRequest = {
      import elastics.PercolatorIndex._
      client.preparePercolate()
        .setIndices(`inu-percolate`)
        .setDocumentType(logs.`type`)
        .setGetRequest(getRequest)
        .setPercolateQuery(q)
        .setSize(10)
        .setHighlightBuilder(new HighlightBuilder().requireFieldMatch(true)
                                   .field("dialogs").numOfFragments(0).preTags("<c>").postTags("</c>")
                                   .field("agent*").numOfFragments(0).preTags("<c>").postTags("</c>")
                                   .field("customer*").numOfFragments(0).preTags("<c>").postTags("</c>"))
        .request()
    }

    println(s"${XContentHelper.convertToJson(percolateRequest.source(), true, true)}")

    val p = Promise[PercolateResponse]()
    val listener = new ActionListener[PercolateResponse] {
      def onFailure(e: Throwable): Unit = p.tryFailure(e)
      def onResponse(resp: PercolateResponse): Unit = p.trySuccess(resp)
    }
    client.percolate(percolateRequest, listener)
    p.future
  }
  
  def highlightWebVTT(index: String, tpe: String, id: String)(implicit params: Map[String, String]) = {

    import elastics.LteIndices.SearchHitHighlightFields._
    
    getDoc(Requests.getRequest(index).`type`(tpe).id(id))
      .zip(percolate(Requests.getRequest(index).`type`(tpe).id(id),params))
      .map { case(VttField(vtt), percolateResp) =>

      def substitute(vtt: Map[String, String])(txt: String): Option[(String, String)] = txt match {
        case highlightedSentence(cueid, highlight) =>
          for {
            subtitle <- vtt.get(cueid)
            tag <- party.findFirstIn(cueid).map { txt => s"c.$txt" }
          } yield cueid -> subtitle.replaceAll( insideTagV, s"$$1$highlight$$2").replaceAll("""(?<=\<)c(?=>)""", tag)
        case _ =>
          None
      }

      vtt ++ percolateResp.getMatches()
        .flatMap { m => m.getHighlightFields }
        .flatMap { case (_, hf) => hf.fragments().flatMap(splitFragment) }
        .flatMap { substitute(vtt)(_) }
    }
  }

  def `_search/preview` = cors {
    get {
      path("_search" / "preview") {
        complete(OK)
      } ~
        path("_vtt" / Segment / Segment / Segment) { (index, tpe, id) =>
          parameterMap { implicit params => {
            onComplete(highlightWebVTT(index, tpe, id)) {
              case Success(value) =>
                respondWithMediaType(`text/vtt`) {
                  complete(OK, value)
                }
              case Failure(ex) => complete(InternalServerError, ex)
            }
          }
          }
        }
    }
  }
}
