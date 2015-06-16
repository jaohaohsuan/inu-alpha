package domain.search

import domain.search.DependencyGraph._

import scala.util.Try

object DependencyGraphState {

  def empty() = DependencyGraphState(occurredEdges = Map.empty)
}

case class DependencyGraphState private (val occurredEdges: Map[Edge, String]) {

  import algorithm.TopologicalSort._

  def isAcyclic(edge: Edge) = {
    Try(sort(append(toPredecessor(occurredEdges.keys.map { e => (e.consumer, e.provider) }), (edge.consumer, edge.provider)))).isSuccess
  }

  def tryConvertToExecutableSeq(from: StoredQueryId): Try[Seq[ChainLink] ]=
    Try(collectPaths(from)(toPredecessor(occurredEdges.keys.map { e => (e.consumer, e.provider) })).flatMap { _.map {
      case (provider, consumer) => ChainLink(consumer, provider, occurredEdges.get(Edge(consumer, provider)).get)
    }}.toSeq)

  def update(event: DomainEvent): DependencyGraphState = {
    event match {
      case OccurredEdgeAdded(edge, occurrence) =>
        copy(occurredEdges = occurredEdges + (edge -> occurrence))
      case OccurredEdgeRemoved(edge, _) =>
        copy(occurredEdges = occurredEdges - edge)
    }
  }


}
