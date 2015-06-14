package domain.search.template

object TemplateState {

  def empty(): TemplateState = TemplateState("", Map.empty)

  trait DomainEvent
  case class Named(name: String) extends DomainEvent
  case class ClauseAdded(id: Int,clause: BoolQueryClause) extends DomainEvent
  case class ClauseRemoved(id: Int, clause: BoolQueryClause) extends DomainEvent

  //case class GatheringNamedClause
}

case class TemplateState private (val name: String,
                                  val clauses: Map[Int,BoolQueryClause]) {

  import TemplateState._

  def update(event: DomainEvent): TemplateState = event match {

    case Named(newName) =>
      copy(name = newName)
    case ClauseAdded(id, clause) =>
      copy(clauses = clauses + (id -> clause))
    case ClauseRemoved(id, _) =>
      copy(clauses = clauses - id)

  }
}
