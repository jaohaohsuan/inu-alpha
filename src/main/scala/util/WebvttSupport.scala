package util

import org.json4s.native.JsonMethods._
import spray.http.{HttpEntity, MediaType, MediaTypes}
import spray.httpx.marshalling.Marshaller

trait WebvttSupport {

  val `text/vtt` = MediaTypes.register(MediaType.custom("text/vtt"))

  implicit val mapMarshaller: Marshaller[Map[String,String]] =
    Marshaller.of[Map[String,String]](`text/vtt`) { (value, contentType, ctx) =>
      val body = value.toSeq.sortBy { case(cueid, _) => {
        val p = """\w+-(\d+)""".r
        val p(t) = cueid
        t.toInt
      } }.map { e => {
        val (cueid, subtitle) = e
        s"$cueid\n$subtitle"
      } }.mkString("\n\n")
      ctx.marshalTo(HttpEntity(contentType, s"WEBVTT\n\n$body"))
    }

}
