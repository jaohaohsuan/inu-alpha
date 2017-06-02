package com.inu.frontend.utils.encoding

/**
  * Created by henry on 6/2/17.
  */
trait NativeJson4sSupport extends de.heikoseeberger.akkahttpjson4s.Json4sSupport {

  implicit val serialization = org.json4s.native.Serialization // or jackson.Serialization
  implicit val formats = org.json4s.DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all

}

object NativeJson4sSupport extends NativeJson4sSupport {

}
