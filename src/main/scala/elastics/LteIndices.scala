package elastics

import akka.actor.Actor
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{RichSearchHit, RichSearchResponse, ElasticClient, QueryDefinition}
import org.elasticsearch.action.search.SearchResponse

import scala.collection.immutable.Iterable
import scala.concurrent.Future

object LteIndices {


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

    def unapply(h: RichSearchHit): Option[(String, Iterable[(String, String)])] = {
      val VttField(vtt) = h
      val highlightSentence = """((agent|customer)\d{1,2}-\d+)\s([\s\S]+)""".r
      val fragments = h.highlightFields.flatMap { case (_, hf) => hf.getFragments }.flatMap { txt =>
        txt.string match {
          case highlightSentence(cueid, _, highlight) =>
            Some(cueid -> vtt.get(cueid).map {
              _.replaceAll( """(<v\b[^>]*>)[^<>]*(<\/v>)""", s"$$1$highlight$$2")
            }.getOrElse(s"$vtt"))
          case _ => None
        }
      }

      Some((s"${h.index}/${h.`type`}/${h.id}", fragments))
    }
  }

}

trait LteIndices {
  self: Actor ⇒

  def client: ElasticClient

  def `GET lte*/_search`(q: QueryDefinition): Future[SearchResponse] = {
    client.execute { search in "lte*" query q fields("vtt") highlighting(
      options requireFieldMatch true preTags "<b>" postTags "</b>",
      highlight field "agent*" fragmentSize 50000,
      highlight field "customer*" fragmentSize 50000,
      highlight field "dialogs" fragmentSize 50000) }
  }

}
