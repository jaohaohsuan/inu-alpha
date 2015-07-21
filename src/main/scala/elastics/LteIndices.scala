package elastics

import akka.actor.Actor
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{ElasticClient, QueryDefinition, RichSearchHit}
import org.elasticsearch.action.search.SearchResponse

import scala.collection.immutable.Iterable
import scala.concurrent.Future

object LteIndices {

  case class VttHighlightFragment(cueid: String, subtitle: String, keywords: String)

  object VttField {

    import org.elasticsearch.search.SearchHitField
    import scala.collection.JavaConversions._

    def unapply(h: RichSearchHit): Option[Map[String,String]] = {

      h.fieldOpt("vtt") match {
        case Some(field: SearchHitField) =>
          val line = """(.+-\d+)\s([\s\S]+)\s$""".r
          Some(field.values().foldLeft(Map.empty[String,String]){ (acc, v) => {
            val line(cueid, content) = s"$v"
            acc + (cueid -> content)
          }})
        case _ => None
      }
    }
  }

  object LteHighlightFields {

    def splitFragment(fragment: org.elasticsearch.common.text.Text) =
      ("""(?:(?:agent|customer)\d-\d+\b)(?:[^\n]*[<>]+[^\n]*)""".r findAllIn fragment.string()).toList

    def substitute(vtt: Map[String,String])(txt: String) = {
      val highlightedSentence = """((?:agent|customer)\d{1,2}-\d+)\s([\s\S]+)""".r

      val tagText = """(?:<\w+\b[^>]*>)([^<>]*)(?:<\/\w+>)""".r

      txt match {
        case highlightedSentence(cueid, highlight) =>
          Some(VttHighlightFragment(
          cueid,
          vtt.get(cueid).map {
            _.replaceAll( """(<v\b[^>]*>)[^<>]*(<\/v>)""", s"$$1$highlight$$2")
            }.getOrElse(s"'$cueid' key doesn't exist in vtt."),
            (for (m <- tagText findAllMatchIn highlight) yield m group 1).mkString(" ")))
        case _ =>
          println(s"highlightedSentence '$txt' unmatched.")
          None
      }
    }

    def unapply(h: RichSearchHit): Option[(String, Iterable[VttHighlightFragment])] = {

      val VttField(vtt) = h

      val fragments = h.highlightFields
        .flatMap { case (_, hf) => hf.fragments().flatMap(splitFragment) }.flatMap (substitute(vtt)(_))

      Some((s"${h.index}/${h.`type`}/${h.id}", fragments))
    }
  }

}

trait LteIndices {
  self: Actor ⇒

  def client: ElasticClient

  def `GET lte*/_search`(block: QueryDefinition): Future[SearchResponse] = {

    val request = search in "lte*" query block fields "vtt" highlighting(
      options requireFieldMatch true preTags "<b>" postTags "</b>",
      highlight field "agent*" fragmentSize 50000,
      highlight field "customer*" fragmentSize 50000,
      highlight field "dialogs" fragmentSize 50000)
    request.show
    client.execute { request }
  }

}
