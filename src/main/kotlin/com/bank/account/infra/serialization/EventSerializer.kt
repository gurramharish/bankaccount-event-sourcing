package com.bank.account.infra.serialization

import com.bank.account.domain.model.AccountEvent
import com.eventstore.dbclient.EventData
import com.eventstore.dbclient.EventDataBuilder
import com.eventstore.dbclient.RecordedEvent
import com.eventstore.dbclient.ResolvedEvent
import kotlinx.serialization.json.Json
import java.util.UUID

object EventSerializer {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun toEventData(event: AccountEvent): EventData {
        val eventType = when (event) {
            is AccountEvent.Deposited -> "Deposited"
            is AccountEvent.Withdrawn -> "Withdrawn"
        }
        val payload = json.encodeToString(AccountEvent.serializer(), event).toByteArray(Charsets.UTF_8)
        return EventDataBuilder.json(UUID.randomUUID(), eventType, payload).build()
    }

    fun toEventData(snapshot: AccountSnapshot): EventData {
        val payload = json.encodeToString(AccountSnapshot.serializer(), snapshot).toByteArray(Charsets.UTF_8)
        return EventDataBuilder.json(UUID.randomUUID(), "AccountSnapshot", payload).build()
    }

    fun toAccountEvent(eventData: ByteArray): AccountEvent {
        val jsonString = String(eventData, Charsets.UTF_8)
        return json.decodeFromString(AccountEvent.serializer(), jsonString)
    }

    fun toAccountEvent(recordedEvent: RecordedEvent): AccountEvent {
        return toAccountEvent(recordedEvent.eventData)
    }

    fun toAccountEvent(resolvedEvent: ResolvedEvent): AccountEvent {
        return toAccountEvent(resolvedEvent.originalEvent.eventData)
    }

    fun toAccountSnapshot(eventData: ByteArray): AccountSnapshot {
        val jsonString = String(eventData, Charsets.UTF_8)
        return json.decodeFromString(AccountSnapshot.serializer(), jsonString)
    }

    fun toAccountSnapshot(recordedEvent: RecordedEvent): AccountSnapshot {
        return toAccountSnapshot(recordedEvent.eventData)
    }

    fun toAccountSnapshot(resolvedEvent: ResolvedEvent): AccountSnapshot {
        return toAccountSnapshot(resolvedEvent.originalEvent.eventData)
    }
}
