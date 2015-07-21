package routing

import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.get.{GetRequest, GetResponse}
import org.elasticsearch.action.percolate.PercolateResponse
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.client.{Client, Requests}
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilders}
import org.elasticsearch.search.highlight.HighlightBuilder
import spray.http.StatusCodes._
import spray.routing.HttpService
import util.{CorsSupport, WebvttSupport}
import scala.collection.JavaConversions._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}


trait SearchPreviewRoute extends HttpService with WebvttSupport with CorsSupport {

  val client: Client = new TransportClient().addTransportAddress(new InetSocketTransportAddress("127.0.0.1", 9300))

  def getDoc(index: String,
             tpe: String, id: String) = Requests.getRequest(index).`type`(tpe).id(id)

  def percolate(getRequest: GetRequest,
                params: Map[String, String]): Future[PercolateResponse] = {

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
        .setHighlightBuilder(new HighlightBuilder().requireFieldMatch(true).field("dialogs").numOfFragments(0).field("agent*").field("customer*"))
        .request()
    }

    val p = Promise[PercolateResponse]()
    val listener = new ActionListener[PercolateResponse] {
      def onFailure(e: Throwable): Unit = p.tryFailure(e)
      def onResponse(resp: PercolateResponse): Unit = p.trySuccess(resp)
    }

    client.percolate(percolateRequest, listener)
    p.future
  }

  import scala.concurrent.ExecutionContext.Implicits.global

  def `_search/preview` = cors {
    get {
      path("_search" / "preview") {
        complete(OK)
      } ~
        path("_vtt" / Segment / Segment / Segment) { (index, tpe, id) =>
          parameterMap { params => {

            val p = Promise[GetResponse]()
            val listener = new ActionListener[GetResponse] {
              def onFailure(e: Throwable): Unit = p.tryFailure(e)
              def onResponse(resp: GetResponse): Unit = p.trySuccess(resp)
            }
            client.get(getDoc(index,tpe,id).fields("vtt"), listener)

            val f = p.future.zip(percolate(getDoc(index,tpe,id),params)).map { case (doc,percolateResp) =>

              def substitute(vtt: Map[String, String],txt: String) = {
                val highlightedSentence = """((?:agent|customer)\d{1,2}-\d+)\s([\s\S]+)""".r
                txt match {
                  case highlightedSentence(cueid, highlight) =>
                    Some(cueid -> vtt.get(cueid).map {
                      _.replaceAll( """(<v\b[^>]*>)[^<>]*(<\/v>)""", s"$$1$highlight$$2")
                    }.getOrElse(s"'$cueid' key doesn't exist in vtt."))
                  case _ =>
                    println(s"highlightedSentence '$txt' unmatched.")
                    None
                }
              }

              def splitFragment(fragment: org.elasticsearch.common.text.Text) =
                ("""(?:(?:agent|customer)\d-\d+\b)(?:[^\n]*[<>]+[^\n]*)""".r findAllIn fragment.string()).toList

              val vtt = doc.getField("vtt").getValues().foldLeft(Map.empty[String,String]){ (acc, v) => {
                val line = """(.+-\d+)\s([\s\S]+)\s$""".r
                val line(cueid, content) = s"$v"
                acc + (cueid -> content)
              }}

              vtt ++ percolateResp.getMatches()
                .flatMap { m => m.getHighlightFields() }
                .flatMap { case (_, hf) => hf.fragments().flatMap(splitFragment) }
                .flatMap { substitute(vtt, _) }
            }

            onComplete(f) {
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
