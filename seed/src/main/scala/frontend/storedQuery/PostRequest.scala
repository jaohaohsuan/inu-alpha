package frontend.storedQuery.postRequest


import akka.actor.Props
import protocol.storedQuery.StoredQuery
import seed.domain.storedQuery.StoredQueryAggregateRoot.{CreateNewStoredQuery, ItemCreated}
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing.RequestContext
import frontend.PerRequest
import scala.language.implicitConversions

object NewTemplateImplicits {
  implicit def optionStringToSet(value : Option[String]) = value.map { _.split("""\s+""").toSet }.getOrElse(Set.empty)
}

case class NewTemplate(title: String, tags: Option[String]){
  require( title.nonEmpty )
}

object NewTemplateRequest {
  def props(implicit ctx: RequestContext, e: NewTemplate, referredId: Option[String] = None) =
    Props(classOf[NewTemplateRequest], ctx, e, referredId)
}
case class NewTemplateRequest(ctx: RequestContext, e: NewTemplate, referredId: Option[String] = None) extends PerRequest {

  import NewTemplateImplicits._

  context.actorSelection("/user/aggregateRootProxy") ! CreateNewStoredQuery(e.title, referredId, e.tags)

  def processResult = {
    case ItemCreated(StoredQuery(id, title, _, _), _)  =>
      response {
        URI { href =>
          respondWithHeader(RawHeader("Location", s"$href/$id".replaceAll("""/(\d|temporary)+(?=/\d)""", ""))){
            complete(Created)
          }
        }
      }
  }
}


