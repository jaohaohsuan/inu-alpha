package com.inu.protocol.Serialization

import com.esotericsoftware.kryo.Kryo
import com.inu.protocol.storedquery.messages._
import com.romix.scala.serialization.kryo._

class KryoInit {

  def customize(kryo:Kryo):Unit = {

    // Serialization of Scala maps like Trees, etc
    kryo.addDefaultSerializer(classOf[scala.collection.Map[_,_]], classOf[ScalaImmutableMapSerializer])
    kryo.addDefaultSerializer(classOf[scala.collection.generic.MapFactory[scala.collection.Map]], classOf[ScalaImmutableMapSerializer])

    // Serialization of Scala sets
    kryo.addDefaultSerializer(classOf[scala.collection.Set[_]], classOf[ScalaImmutableSetSerializer])
    kryo.addDefaultSerializer(classOf[scala.collection.generic.SetFactory[scala.collection.Set]], classOf[ScalaImmutableSetSerializer])

    // Serialization of all Traversable Scala collections like Lists, Vectors, etc
    kryo.addDefaultSerializer(classOf[scala.collection.Traversable[_]], classOf[ScalaCollectionSerializer])

    // entities
    kryo.register(classOf[StoredQuery], 4001)
    kryo.register(classOf[BoolClause], 4002)
    kryo.register(classOf[NamedClause], 4003)
    kryo.register(classOf[SpanNearClause], 4004)
    kryo.register(classOf[MatchClause], 4005)

    // command
    kryo.register(classOf[CreateNewStoredQuery], 5000)
    kryo.register(classOf[UpdateStoredQuery], 5001)
    kryo.register(classOf[AddClause], 5002)
    kryo.register(classOf[RemoveClauses], 5003)
    kryo.register(classOf[ResetOccurrence], 5004)


    // ack
    kryo.register(classOf[StoredQueryCreatedAck], 6000)
    kryo.register(classOf[ClauseAddedAck], 6001)
    kryo.register(classOf[RejectAck], 6002)

    // event
    kryo.register(classOf[ItemCreated], 7000)
    kryo.register(classOf[ItemUpdated], 7001)
    kryo.register(classOf[ClauseAdded], 7002)
    kryo.register(classOf[ClauseRemoved], 7003)
  }

}