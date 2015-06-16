package routing.request.search

import akka.actor.ActorRef
import akka.contrib.pattern.ClusterClient.{Send, SendToAll}
import domain.search.DependencyGraph.Edge
import routing.request.PerRequest
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing._

case class SaveAsNewRequest(ctx: RequestContext, clusterClient: ActorRef, sourceTemplateId: String, newName: String) extends PerRequest {

  import domain.search._
  import StoredQueryCommandQueryProtocol._

  clusterClient ! Send(storedQueryViewRegion, GetAsBoolClauseQuery(sourceTemplateId), localAffinity = true)

  def processResult = {
    case BoolClauseResponse(_, _, clauses, _) => {

      val templateId = newName

      clauses.foldLeft(Unit) { (acc, clause) =>
        clause match {
          case NamedBoolClause(clauseTemplateId, occur, _, _) =>
            clusterClient ! SendToAll(dependencyGraphSingleton, DependencyGraph.AddOccurredEdgeCommand(Edge(templateId, clauseTemplateId), occur))
          case _ =>
        }
        clusterClient ! Send(storedQueryRegion, AddClauseCommand(templateId, clause), localAffinity = true)
        acc
      }
      response {
        URI { href =>
          respondWithHeader(RawHeader("Location", href.resolve(templateId).toString)) {
            complete(Created)
          }
        }
      }
    }
  }
}
