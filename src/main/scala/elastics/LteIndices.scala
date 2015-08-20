package elastics

import akka.actor.Actor
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{ElasticClient, QueryDefinition, RichSearchHit}
import org.elasticsearch.action.count.CountResponse
import org.elasticsearch.action.search.SearchResponse

import scala.collection.immutable.Iterable
import scala.concurrent.Future
import scala.util.{Try,Failure, Success}
import scala.util.matching.Regex

object LteIndices {

  case class VttHighlightFragment(start: String, keywords: String)

  object VttField {

    import org.elasticsearch.action.get.GetResponse
    import scala.collection.JavaConversions._

    val NAME = "vtt"

    private val line = """(.+-\d+)\s([\s\S]+)\s$""".r

    implicit class fieldValuesToMap(values: java.util.List[Object]) {
      def asMap() = values.foldLeft(Map.empty[String, String]) { (acc, v) => {
        val line(cueid, content) = s"$v"
        acc + (cueid -> content)
      }
      }
    }

    def unapply(value: AnyRef): Option[Map[String, String]] = {
      value match {
        case doc: GetResponse if doc.exists {_.getName equals NAME} =>
          Some(doc.getField(NAME).getValues.asMap())

        case h: RichSearchHit =>
          h.fieldOpt(NAME).map {
            _.getValues.asMap()
          }
        case _ => None
      }
    }
  }

  object SearchHitHighlightFields {

    import org.elasticsearch.common.text.Text

    val insideHighlightTag = """(?:<\w+\b[^>]*>)([^<>]*)(?:<\/\w+>)""".r
    // "agent0-780</c> 喂哎哎您好下<c>女士</c>是吧" extract '780' and '喂哎哎您好下<c>女士</c>是吧'
    val highlightedSentence = """(?:<\w+>)*((?:agent|customer)\d+-\d+)(?:<\/(?:em|c)>)*\s([\s\S]+)""".r
    val highlightFragment = """(?:[^\n]*[<>]+[^\n]*)""".r
    val insideTagV = """(<v\b[^>]*>)[^<>]*(<\/v>)"""
    val startTime = """^(\d{2,3}[:\.]?)+""".r
    val party = """\w+(?=-)""".r

    def splitFragment(fragment: Text): List[String] = {
      //import util.ImplicitPrint._
      (highlightFragment findAllIn fragment.string()).toList//.println()
    }

    def substitute(vtt: Map[String, String])(txt: String): Option[VttHighlightFragment] = {
      txt match {
        case highlightedSentence(cueid, highlighted) =>
          //println(s"$cueid, $highlighted")
          (for {
            highlightedSubtitle <- Try(vtt(cueid).replaceAll(insideTagV, s"$$1$highlighted$$2"))
            keywords <- Try((for (m <- insideHighlightTag findAllMatchIn highlighted) yield m group 1).mkString(" "))
            time <- Try(startTime.findFirstIn(highlightedSubtitle).get)
          } yield VttHighlightFragment(time, keywords)) match {
            case Failure(ex) =>
             println(s"extract error: $highlighted ${ex}")
              None
            case Success(v) => Some(v)
          }

        case _ =>
          println(s"highlightedSentence '$txt' unmatched.")
          None
      }
    }

    def unapply(value: AnyRef): Option[(String, scala.Iterable[VttHighlightFragment])] = {
      value match {
        case h: RichSearchHit =>
          val VttField(map) = h
          Some((s"${h.index}/${h.`type`}/${h.id}", h.highlightFields.values.flatMap {
            _.fragments().flatMap(splitFragment)
          }.flatMap(substitute(map)(_))))
        case _ => None
      }
    }
  }

}

trait LteIndices {
  self: Actor ⇒



  def `GET lte*/_search`(block: QueryDefinition): Future[SearchResponse] = {

    val request = search in "lte*" query block fields "vtt" highlighting(
      options requireFieldMatch true preTags "<em>" postTags "</em>",
      highlight field "agent*" numberOfFragments 0,
      highlight field "customer*" numberOfFragments 0,
      highlight field "dialogs" numberOfFragments 0)

    elastics.Cluster.`4s client`.execute { request }
  }

  def `GET lte*/_count`(block: QueryDefinition): Future[CountResponse] = {
    val request = count from "lte*" query block

    elastics.Cluster.`4s client`.execute(request)
  }

}
