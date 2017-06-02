package com.inu.frontend

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model.{HttpCharsets, MediaType, MessageEntity}


trait WebvttSupport {

  val `text/vtt` = MediaType.applicationWithFixedCharset("text/vtt", HttpCharsets.`UTF-8`)

  implicit val mapMarshaller: Marshaller[Map[String,String], MessageEntity] = Marshaller.stringMarshaller(`text/vtt`).compose {
    case value: Map[String,String] =>
      val millisecond = """\w+-(\d+)""".r
      val body = value.toSeq.sortBy { case(millisecond(t), _) => t.toInt }.map { case (cueid, subtitle) => s"$cueid\n$subtitle" }.mkString("\n\n")
      s"WEBVTT\n\n$body"
  }
}
