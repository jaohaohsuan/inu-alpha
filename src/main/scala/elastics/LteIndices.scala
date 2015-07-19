package elastics

import akka.actor.Actor
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{ElasticClient, QueryDefinition}

trait LteIndices {
  self: Actor ⇒

  def client: ElasticClient

  def `GET lte*/_search`(q: QueryDefinition) = {
    client.execute { search in "lte*" query q fields("vtt") highlighting(
      options requireFieldMatch true preTags "<b>" postTags "</b>",
      highlight field "agent*" fragmentSize 50000,
      highlight field "customer*" fragmentSize 50000,
      highlight field "dialogs" fragmentSize 50000) }
  }
}
