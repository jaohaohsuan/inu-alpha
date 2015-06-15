package domain.search.template


import domain.search.template.Graph.Edge

import scala.util.Try

object GraphState {
  def empty() = GraphState(edges = Set.empty)
}

case class GraphState private (val edges: Set[Edge]) {

  import algorithm.TopologicalSort._

  def isAccepted(edge: Edge) = {
    Try(sort(append(toPredecessor(edges.map { e => (e.consumer, e.provider) }), (edge.consumer, edge.provider)))).isSuccess
  }

  def getUpdateRoutes(start: String) = collectPaths(start)(toPredecessor(edges.map { e => (e.consumer, e.provider) }))

  def update(edge: Edge): GraphState = {
    copy(edges = edges + edge)
  }

  def remove(edge: Edge): GraphState = {
    copy(edges = edges - edge)
  }

}
