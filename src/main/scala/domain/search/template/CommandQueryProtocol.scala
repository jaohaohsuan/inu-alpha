package domain.search.template

object CommandQueryProtocol {

  sealed trait Message {
    val templateId: String
  }

  sealed trait Command extends Message

  case class SaveAsCommand(templateId: String, newTemplateName: String) extends Command
  case class AddClauseCommand(templateId: String, clause: BoolQueryClause) extends Command
  case class RemoveClauseCommand(templateId: String, clauseId: Int) extends Command

  sealed trait Ack extends Message

  case class NewTemplateSavedAck(templateId: String) extends Ack
  case class ClauseAddedAck(templateId: String, version: Int, clauseId: String) extends Ack
  case class ClauseRemovedAck(templateId: String, clauseId: Int, clause: BoolQueryClause) extends Ack

  sealed trait Query extends Message

  case class GetAsBoolClauseQuery(templateId: String) extends Query
  case class GetVersion(templateId: String) extends Query

  sealed trait Response  extends Message

  case class BoolClauseResponse(templateId: String, templateName: String,clauses: List[BoolQueryClause], version: Int) extends Response
  case class VersionResponse(templateId: String, version: Int) extends Response

  val templateRegion =  s"/user/sharding/${domain.search.template.Template.shardName}"

  val templateViewRegion = s"/user/sharding/${domain.search.template.TemplateView.shardName}"

  val graphSingleton = "/user/searchTemplateGraph/active"

}