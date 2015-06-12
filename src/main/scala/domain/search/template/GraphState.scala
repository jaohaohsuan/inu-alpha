package domain.search.template


import scala.util.Try

object GraphState {
  def empty[A]() = GraphState[A](Map.empty[A, Set[A]])

  trait DomainEvent
  case class EdgeAdded[A](edge: (A,A), clause: (A, String)) extends  DomainEvent
}

case class GraphState[A] private (preds: Map[A, Set[A]]) {

  import algorithm.TopologicalSort._

  def isAccepted(edge: (A, A)) = {
    Try(sort(append(preds, edge))).isSuccess
  }

  def getUpdateRoutes(start: A) = collectPaths(start)(preds)

  def update(edge: (A, A)): GraphState[A] = {
    copy(preds = append(preds, edge))
  }
}
