package com.inu.cluster

import com.esotericsoftware.kryo.Kryo
import com.inu.cluster.storedquery.messages._
import com.inu.protocol.storedquery.messages._

class KryoInit {

  def customize(kryo: Kryo): Unit  = {
    kryo.register(classOf[ItemCreated])
    //kryo.register(classOf[ClauseAdded])
    //kryo.register(classOf[ClauseRemoved])
    kryo.register(scala.None.getClass)
    kryo.register(scala.collection.immutable.Nil.getClass)
    kryo.register(classOf[StoredQuery])
/*  kryo.register(classOf[protocol.storedQuery.BoolClause])
    kryo.register(classOf[protocol.storedQuery.NamedBoolClause])
    kryo.register(classOf[protocol.storedQuery.SpanNearBoolClause])
    kryo.register(classOf[protocol.storedQuery.MatchBoolClause])
*/
    kryo.register(classOf[scala.collection.Map[_,_]])
    kryo.register(classOf[scala.collection.Set[_]])
    kryo.register(scala.collection.immutable.::.getClass)
    kryo.register(Set.empty.getClass)
    kryo.register(Map.empty.getClass)
    kryo.register(Tuple1.getClass)
    kryo.register(Tuple2.getClass)
    kryo.register(classOf[ItemUpdated])
  }
}
