package com.inu.frontend.logs

import com.inu.frontend.WebvttSupport
import com.inu.frontend.directive.{LogsDirectives, StoredQueryDirectives, VttDirectives}
import spray.routing.{HttpService, Route}
import spray.http.StatusCodes._
import com.inu.frontend.elasticsearch.ImplicitConversions._
import org.json4s.JsonAST.JString

import scala.concurrent.ExecutionContext
import org.json4s._
import org.json4s.native.JsonMethods._

trait LogsRoute extends WebvttSupport with HttpService with LogsDirectives with VttDirectives with StoredQueryDirectives {

  implicit def executionContext: ExecutionContext

  /*
  url sample:

  http://192.168.1.100:31115/sapi/logs-2016.07.01/amiast/wqidhfuehuhqw?_id=1775285775

  _id is 模型的id
   */
  lazy val `logs-*`: Route = {
    get {
      prepareGetVtt { q =>
        onSuccess(q.execute().future) { gr =>
          format(parse(gr.json) \\ "vtt" \\ classOf[JString]) { vttMap =>
            percolate(gr) { p =>
              onSuccess(p.execute().future) { pr =>
                extractFragments(parse(pr.json) \\ classOf[JString]) { segments =>
                  respondWithMediaType(`text/vtt`) {
                    complete(OK, vttMap.highlightWith(segments))
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
