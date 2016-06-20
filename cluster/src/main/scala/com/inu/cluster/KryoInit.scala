package com.inu.cluster

import com.esotericsoftware.kryo.Kryo

class KryoInit extends com.inu.protocol.Serialization.KryoInit {

  override def customize(kryo: Kryo): Unit  = {

    super.customize(kryo)
  }
}
