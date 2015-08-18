package elastics

import akka.actor.Actor
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.mappings.DynamicMapping._
import com.sksamuel.elastic4s.mappings.FieldType._
import com.sksamuel.elastic4s.mappings.{TermVector, DynamicTemplateDefinition, StringFieldDefinition}
import com.sksamuel.elastic4s.{WhitespaceAnalyzer, QueryDefinition, ElasticClient}

object LteTemplate {

  object fields {

    def agent = new DynamicTemplateDefinition("agent") matching "agent*" matchMappingType "string"
    def customer = new DynamicTemplateDefinition("customer") matching "customer*" matchMappingType "string"

    def configDynamicFieldIndexAnalyzer(df: DynamicTemplateDefinition, analyzer: String) = df mapping {
      field typed StringType indexAnalyzer analyzer searchAnalyzer WhitespaceAnalyzer
    }

    def configIndexAnalyzer(field: StringFieldDefinition, analyzer: String) =
      field indexAnalyzer analyzer searchAnalyzer WhitespaceAnalyzer

    def vtt: StringFieldDefinition = "vtt" typed StringType

    def dialogs: StringFieldDefinition = "dialogs" typed StringType

    def parties: StringFieldDefinition = "parties" typed StringType

    def path: StringFieldDefinition = "path" typed StringType index "not_analyzed"
  }



  object mappings {
    import fields._

    lazy val cht = mapping("cht") as Seq(
      parties,
      path,
      configIndexAnalyzer(vtt, "whitespace"),
      configIndexAnalyzer(dialogs, "whitespace")
    ) all false source true dynamic Dynamic templates(configDynamicFieldIndexAnalyzer(agent, "whitespace"),configDynamicFieldIndexAnalyzer(customer, "whitespace"))

    lazy val ytx = mapping("ytx") as Seq(
      parties,
      path,
      configIndexAnalyzer(vtt, "ik_stt_analyzer"),
      configIndexAnalyzer(dialogs, "ik_stt_analyzer")
    ) all false source true dynamic Dynamic templates(configDynamicFieldIndexAnalyzer(agent,"ik_stt_analyzer"),configDynamicFieldIndexAnalyzer(customer,"ik_stt_analyzer"))

  }
}

trait LteTemplate extends util.ImplicitActorLogging{
  self: Actor ⇒

  lazy val `PUT _template/lte` = {
    import LteTemplate.mappings._
    import context.dispatcher

    client.execute { create template "lte" pattern "lte*" mappings(cht, ytx) }.map { ("PUT _template/lte", _) }
  }
  
  def client: ElasticClient
}




