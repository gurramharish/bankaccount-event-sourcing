package com.bank.account.application

import com.bank.account.domain.model.AccountCommand
import com.bank.account.domain.model.AccountId
import com.bank.account.infra.eventstore.EventStoreClientFactory
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountSnapshotIntegrationTest {

    private val client = EventStoreClientFactory.create()
    private val handler = AccountCommandHandler(client)

    @AfterAll
    fun tearDown() {
        client.shutdown()
    }

    @Test
    fun under_threshold_replays_all_events_and_does_not_create_snapshot(): Unit = runBlocking {
        val accountId = AccountId("snap-test-under-" + UUID.randomUUID())

        repeat(9) {
            handler.handle(AccountCommand.Deposit(accountId, BigDecimal("10.00")))
        }

        val loaded = handler.loadState(accountId)

        loaded.state.balance shouldBe BigDecimal("90.00")
        loaded.isFromSnapshot shouldBe false
        loaded.lastSnapshotRevision shouldBe -1L
        loaded.streamRevision shouldBe 8L
        loaded.eventsReplayedCount shouldBe 9
    }

    @Test
    fun reaching_threshold_creates_snapshot_and_subsequent_load_uses_snapshot(): Unit = runBlocking {
        val accountId = AccountId("snap-test-threshold-" + UUID.randomUUID())

        repeat(10) {
            handler.handle(AccountCommand.Deposit(accountId, BigDecimal("10.00")))
        }

        val loaded = handler.loadState(accountId)

        loaded.state.balance shouldBe BigDecimal("100.00")
        loaded.isFromSnapshot shouldBe true
        loaded.lastSnapshotRevision shouldBe 9L
        loaded.streamRevision shouldBe 9L
        loaded.eventsReplayedCount shouldBe 0
    }

    @Test
    fun after_snapshot_only_delta_events_are_replayed(): Unit = runBlocking {
        val accountId = AccountId("snap-test-delta-" + UUID.randomUUID())

        repeat(10) {
            handler.handle(AccountCommand.Deposit(accountId, BigDecimal("10.00")))
        }

        handler.handle(AccountCommand.Deposit(accountId, BigDecimal("25.00")))
        handler.handle(AccountCommand.Withdraw(accountId, BigDecimal("15.00")))

        val loaded = handler.loadState(accountId)

        loaded.state.balance shouldBe BigDecimal("110.00")
        loaded.isFromSnapshot shouldBe true
        loaded.lastSnapshotRevision shouldBe 9L
        loaded.streamRevision shouldBe 11L
        loaded.eventsReplayedCount shouldBe 2
    }
}
