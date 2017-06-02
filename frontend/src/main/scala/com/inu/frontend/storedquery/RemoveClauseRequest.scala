package com.inu.frontend.storedquery

import akka.actor.Props
import com.inu.frontend.{CollectionJsonSupport, PerRequest}
import com.inu.protocol.media.CollectionJson.Template
import com.inu.protocol.storedquery.messages.{Command, _}
import org.json4s._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.server.RequestContext

/**
  * Created by henry on 6/14/16.
  */
object RemoveClauseRequest {
  def props(message: Command)(implicit ctx: RequestContext) =
    Props(classOf[RemoveClauseRequest], ctx, message)
}

case class RemoveClauseRequest(ctx: RequestContext, message: Command) extends PerRequest with CollectionJsonSupport {

  context.actorSelection("/user/StoredQueryRepoAggRoot-Proxy") ! message

  def processResult: Receive = {
    case ClausesRemovedAck(clauses) if clauses.isEmpty =>
      response {
        complete(NoContent)
      }
    case ClausesRemovedAck(clauses) =>
      response {
        extractUri { uri =>
            val itemPrefixHref = message match {
              case ResetOccurrence(id, occur) => s"${uri.withQuery(Query())}".replaceFirst("""\/\w+$""", "")
              case RemoveClauses(id, _) => s"${uri.withQuery(Query())}".replaceFirst("""\/\w+\/\w+$""", "")
              case _ => s"${uri.withQuery(Query())}"
            }

            import org.json4s.JsonDSL._
            val href = JField("href", JString(s"${uri.withQuery(Query())}"))
            val items = clauses.map { case (cid,e) => Template(e).template ~~ ("href" -> s"$itemPrefixHref/${e.shortName}/$cid") }.toList
            complete(OK, href :: JField("items", JArray(items)) :: Nil)

        }
      }
  }
}