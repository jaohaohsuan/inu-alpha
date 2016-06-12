package com.inu.protocol.storedquery.messages

import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write

import scala.language.implicitConversions

object ImplicitJsonConversions {

  implicit def json4sFormats: Formats = DefaultFormats

  implicit def dataApply[T <: AnyRef: Manifest](value: T): JValue =
    JArray(parse(write(value)) match {
      case JObject(xs) =>
        xs.map { case (f: String,v: JValue) => ("name" -> f) ~ ("value" -> v) }
      case _ => Nil
    })

  def boolClauseCollectionItem(c: (Int, BoolClause)): JObject = {
    val (clauseId, boolClause) = c
    ("data" -> boolClause) ~ ("href" -> s"#{uri}/${boolClause.shortName}/$clauseId")
  }

}
