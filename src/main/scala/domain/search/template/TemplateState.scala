package domain.search.template

object TemplateState {

  def empty(): TemplateState = TemplateState("", Map.empty, 0)

  trait DomainEvent
  case class Named(name: String) extends DomainEvent
  case class ClauseAdded(id: Int,clause: BoolQueryClause) extends DomainEvent
  case class ClauseRemoved(id: Int, clause: BoolQueryClause) extends DomainEvent

  //case class GatheringNamedClause
}

case class TemplateState private (val name: String,
                                  val clauses: Map[Int,BoolQueryClause], val version: Int) {

  import TemplateState._

  def nextVersion = version + 1

  def update(event: DomainEvent): TemplateState = event match {

    case Named(newName) =>
      copy(name = newName, version = nextVersion)
    case ClauseAdded(id, clause) =>
      copy(clauses = clauses + (id -> clause), version = nextVersion)
    case ClauseRemoved(id, _) =>
      copy(clauses = clauses - id, version = nextVersion)

  }
}
