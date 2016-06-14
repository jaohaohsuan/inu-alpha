package com.inu.protocol.media

import org.json4s._
import org.json4s.JsonDSL._
/**
  * Created by henry on 6/14/16.
  */
object CollectionJson {

  trait Template[A <: AnyRef] {
    import org.json4s._
    import org.json4s.JsonDSL._
    val entity: A
    val prompts: Map[String, String]
    lazy val template: JObject = {
      import org.json4s.native.JsonMethods._
      import org.json4s.native.Serialization
      import org.json4s.native.Serialization.write
      implicit val formats = Serialization.formats(NoTypeHints)

      val JObject(xs) = parse(write[A](entity))
      "data" -> JArray(xs.map {
        case JField(name, value) if prompts.contains(name) => ("name" -> name) ~~ ("value", value) ~~ ("prompt", prompts(name))
        case JField("field", value) => ("name" -> "field") ~~ ("value", value) ~~ ("prompt", "dialogs agent* customer*")
        case JField(name, value) => ("name" -> name) ~~ ("value", value)
      })
    }
  }

  object Template {
    def apply[A <: AnyRef](e: A, kvp: Map[String, String] = Map.empty) = new Template[A] {
      val entity = e
      val prompts = kvp
    }
  }
}
