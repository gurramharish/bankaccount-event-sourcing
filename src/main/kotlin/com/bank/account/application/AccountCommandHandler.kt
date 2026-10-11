package com.bank.account.application

import com.bank.account.domain.accountDecider
import com.bank.account.domain.model.AccountCommand
import com.bank.account.domain.model.AccountEvent
import com.bank.account.domain.model.AccountId
import com.bank.account.domain.model.AccountState
import com.bank.account.infra.serialization.AccountSnapshot
import com.bank.account.infra.serialization.EventSerializer
import com.eventstore.dbclient.AppendToStreamOptions
import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.ExpectedRevision
import com.eventstore.dbclient.ReadStreamOptions
import com.eventstore.dbclient.StreamNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture

sealed interface AccountSnapshotSource {
    val state: AccountState
    val nextReadRevision: Long
    val snapshotRevision: Long
    val isFromSnapshot: Boolean

    data class FromSnapshot(val snapshot: AccountSnapshot) : AccountSnapshotSource {
        override val state: AccountState get() = snapshot.state
        override val nextReadRevision: Long get() = snapshot.snapshotRevision + 1L
        override val snapshotRevision: Long get() = snapshot.snapshotRevision
        override val isFromSnapshot: Boolean get() = true
    }

    data object Initial : AccountSnapshotSource {
        override val state: AccountState get() = accountDecider.initialState
        override val nextReadRevision: Long get() = 0L
        override val snapshotRevision: Long get() = -1L
        override val isFromSnapshot: Boolean get() = false
    }
}

data class ReconstitutedAccount(
    val state: AccountState,
    val streamRevision: Long,
    val lastSnapshotRevision: Long,
    val eventsReplayedCount: Int,
    val isFromSnapshot: Boolean
)

class AccountCommandHandler(
    private val client: EventStoreDBClient
) {
    companion object {
        const val SNAPSHOT_THRESHOLD = 10
    }

    fun getStreamName(accountId: AccountId): String = "account-${accountId.value}"

    fun getSnapshotStreamName(accountId: AccountId): String = "account-${accountId.value}-snapshots"

    suspend fun loadState(accountId: AccountId): ReconstitutedAccount {
        val source = readLatestSnapshot(getSnapshotStreamName(accountId))
        val deltaEvents = readEventsFrom(getStreamName(accountId), source.nextReadRevision)
        val currentState = deltaEvents.fold(source.state, accountDecider.evolve)

        val streamRevision = deltaEvents.lastOrNull()
            ?.let { source.nextReadRevision + deltaEvents.size - 1L }
            ?: source.snapshotRevision

        return ReconstitutedAccount(
            state = currentState,
            streamRevision = streamRevision,
            lastSnapshotRevision = source.snapshotRevision,
            eventsReplayedCount = deltaEvents.size,
            isFromSnapshot = source.isFromSnapshot
        )
    }

    suspend fun handle(command: AccountCommand): AccountState {
        val loaded = loadState(command.accountId)
        val newEvents = accountDecider.decide(command, loaded.state).toList()

        if (newEvents.isEmpty()) return loaded.state

        appendEvents(command.accountId, loaded.streamRevision, newEvents)

        return newEvents.fold(loaded.state, accountDecider.evolve).also { nextState ->
            val newStreamRevision = loaded.streamRevision + newEvents.size
            if (shouldSnapshot(newStreamRevision, loaded.lastSnapshotRevision)) {
                persistSnapshot(command.accountId, nextState, newStreamRevision)
            }
        }
    }

    private fun shouldSnapshot(newStreamRevision: Long, lastSnapshotRevision: Long): Boolean =
        (newStreamRevision - lastSnapshotRevision) >= SNAPSHOT_THRESHOLD

    private suspend fun appendEvents(
        accountId: AccountId,
        currentRevision: Long,
        events: List<AccountEvent>
    ) {
        val options = AppendToStreamOptions.get().expectedRevision(
            if (currentRevision < 0L) ExpectedRevision.noStream()
            else ExpectedRevision.expectedRevision(currentRevision)
        )
        val eventDataList = events.map(EventSerializer::toEventData)
        client.appendToStream(getStreamName(accountId), options, eventDataList.iterator()).awaitResponse()
    }

    private suspend fun persistSnapshot(accountId: AccountId, state: AccountState, revision: Long) {
        val snapshot = AccountSnapshot(
            state = state,
            snapshotRevision = revision,
            timestampEpochMillis = System.currentTimeMillis()
        )
        val snapshotData = EventSerializer.toEventData(snapshot)
        client.appendToStream(
            getSnapshotStreamName(accountId),
            AppendToStreamOptions.get().expectedRevision(ExpectedRevision.any()),
            snapshotData
        ).awaitResponse()
    }

    private suspend fun readLatestSnapshot(snapshotStream: String): AccountSnapshotSource =
        runCatching {
            client.readStream(snapshotStream, ReadStreamOptions.get().backwards().fromEnd().maxCount(1))
                .awaitResponse()
                .events
                .firstOrNull()
                ?.let(EventSerializer::toAccountSnapshot)
                ?.let(AccountSnapshotSource::FromSnapshot)
                ?: AccountSnapshotSource.Initial
        }.getOrElse { ex ->
            if (isStreamNotFound(ex)) AccountSnapshotSource.Initial else throw ex
        }

    private suspend fun readEventsFrom(streamName: String, fromRevision: Long): List<AccountEvent> =
        runCatching {
            client.readStream(streamName, ReadStreamOptions.get().forwards().fromRevision(fromRevision))
                .awaitResponse()
                .events
                .map(EventSerializer::toAccountEvent)
        }.getOrElse { ex ->
            if (isStreamNotFound(ex)) emptyList() else throw ex
        }

    private fun isStreamNotFound(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            if (cause is StreamNotFoundException) return true
            cause = cause.cause
        }
        return false
    }

    private suspend fun <T> CompletableFuture<T>.awaitResponse(): T =
        withContext(Dispatchers.IO) { get() }
}
