package routing.request.search.template

import akka.contrib.pattern.ClusterSharding
import routing.request.PerRequest
import spray.http.StatusCodes._
import spray.routing._

case class SaveAsNewRequest(ctx: RequestContext, sourceTemplateId: String, newName: String) extends PerRequest {

  import domain.search.template._
  import CommandQueryProtocol._

  //def nextWorkId(): String = UUID.randomUUID().toString

  val searchTemplateRegion = ClusterSharding(context.system).shardRegion(Template.shardName)

  searchTemplateRegion ! SaveAsCommand(sourceTemplateId, newName)

  def processResult = {
    case NewTemplateSavedAck =>
      response {
        complete(Accepted)
      }

  }

}
