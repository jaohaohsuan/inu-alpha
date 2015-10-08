package frontend

import spray.http.{HttpEntity, MediaType, MediaTypes}
import spray.httpx.marshalling.Marshaller

/**
 * Created by henry on 10/8/15.
 */
trait WebvttSupport {

  val `text/vtt` = MediaTypes.register(MediaType.custom("text/vtt"))

  implicit val mapMarshaller: Marshaller[Map[String,String]] =
    Marshaller.of[Map[String,String]](`text/vtt`) { (value, contentType, ctx) =>
      val millisecond = """\w+-(\d+)""".r
      val body = value.toSeq.sortBy { case(millisecond(t), _) => {
        t.toInt
      } }.map { e => {
        val (cueid, subtitle) = e
        s"$cueid\n$subtitle"
      } }.mkString("\n\n")
      ctx.marshalTo(HttpEntity(contentType, s"WEBVTT\n\n$body"))
    }
}
