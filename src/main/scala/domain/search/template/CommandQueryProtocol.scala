package domain.search.template

object CommandQueryProtocol {

  sealed trait Message {
    val templateId: String
  }

  sealed trait Command extends Message

  case class SaveAsCommand(templateId: String, newTemplateName: String) extends Command
  case class AddClauseCommand(templateId: String, clause: BoolQueryClause) extends Command


  sealed trait Ack extends Message

  case class NewTemplateSavedAck(templateId: String) extends Ack
  case class ClauseAddedAck(templateId: String, clauseId: String) extends Ack


  sealed trait Query extends Message

  case class GetAsBoolClauseQuery(templateId: String) extends Query

  sealed trait Response  extends Message

  case class BoolClauseResponse(templateId: String, templateName: String,clauses: List[BoolQueryClause]) extends Response

}

/*

  case object Ok
  case object NotOk

  case class PropagatingRequest(routes: Set[Set[(String, String)]])

  case class AddNamedClause(edge: (String, String))

  case class NamedClauseAdded(edge: (String, String))

  case object Ack

 */