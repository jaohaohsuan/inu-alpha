package com.inu.frontend

import com.esotericsoftware.kryo.Kryo
import com.inu.protocol.storedquery.messages._

class KryoInit extends com.inu.protocol.Serialization.KryoInit {
  override def customize(kryo: Kryo): Unit  = {

    super.customize(kryo)
  }
}
