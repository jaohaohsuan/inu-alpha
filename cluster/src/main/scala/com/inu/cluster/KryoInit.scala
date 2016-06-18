package com.inu.cluster

import com.esotericsoftware.kryo.Kryo
import com.inu.cluster.storedquery.messages._

class KryoInit extends com.inu.protocol.Serialization.KryoInit {

  override def customize(kryo: Kryo): Unit  = {

    kryo.register(classOf[ItemCreated])
    kryo.register(classOf[ItemUpdated])
    kryo.register(classOf[ClauseAdded])
    kryo.register(classOf[ClauseRemoved])

    super.customize(kryo)
  }
}
