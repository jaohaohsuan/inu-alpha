package domain.search

object StoredQueryCommandQueryProtocol {

  sealed trait Message {
    val storedQueryId: String
  }

  sealed trait Command extends Message

  case class SaveAsCommand(storedQueryId: String, newTemplateName: String) extends Command
  case class AddClauseCommand(storedQueryId: String, clause: BoolQueryClause) extends Command
  case class RemoveClauseCommand(storedQueryId: String, clauseId: Int) extends Command

  sealed trait Ack extends Message

  sealed trait UpdatedAck extends Ack {
    val version: Int
  }

  case class NewTemplateSavedAck(storedQueryId: String) extends Ack

  case class ClauseAddedAck(storedQueryId: String, version: Int, clauseId: Int) extends UpdatedAck
  case class ClauseRemovedAck(storedQueryId: String, version: Int, clauseId: Int, clause: BoolQueryClause) extends UpdatedAck
  case object ClauseNotFoundAck

  sealed trait Query extends Message

  case class GetAsBoolClauseQuery(storedQueryId: String) extends Query
  case class GetVersion(storedQueryId: String) extends Query

  sealed trait Response  extends Message

  case class BoolClauseResponse(storedQueryId: String, templateName: String,clauses: List[BoolQueryClause], version: Int) extends Response
  case class VersionResponse(storedQueryId: String, version: Int) extends Response

  val storedQueryRegion =  s"/user/sharding/${domain.search.StoredQuery.shardName}"

  val storedQueryViewRegion = s"/user/sharding/${domain.search.StoredQueryView.shardName}"

  val dependencyGraphSingleton = "/user/searchTemplateGraph/active"

}