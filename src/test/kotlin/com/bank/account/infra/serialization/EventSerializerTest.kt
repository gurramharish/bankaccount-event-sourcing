package com.bank.account.infra.serialization

import com.bank.account.domain.model.AccountEvent
import com.bank.account.domain.model.AccountId
import com.bank.account.domain.model.AccountState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class EventSerializerTest {

    private val accountId = AccountId("acc-456")

    @Test
    fun serialize_and_deserialize_deposited_event() {
        val event = AccountEvent.Deposited(accountId, BigDecimal("250.75"))
        val eventData = EventSerializer.toEventData(event)

        eventData.eventType shouldBe "Deposited"

        val deserialized = EventSerializer.toAccountEvent(eventData.eventData)
        deserialized shouldBe event
    }

    @Test
    fun serialize_and_deserialize_withdrawn_event() {
        val event = AccountEvent.Withdrawn(accountId, BigDecimal("120.00"))
        val eventData = EventSerializer.toEventData(event)

        eventData.eventType shouldBe "Withdrawn"

        val deserialized = EventSerializer.toAccountEvent(eventData.eventData)
        deserialized shouldBe event
    }

    @Test
    fun serialize_and_deserialize_account_snapshot() {
        val state = AccountState(
            accountId = accountId,
            balance = BigDecimal("1500.50"),
            isInitialized = true
        )
        val snapshot = AccountSnapshot(
            state = state,
            snapshotRevision = 15L,
            timestampEpochMillis = 1770000000000L
        )

        val eventData = EventSerializer.toEventData(snapshot)
        eventData.eventType shouldBe "AccountSnapshot"

        val deserialized = EventSerializer.toAccountSnapshot(eventData.eventData)
        deserialized shouldBe snapshot
    }
}
