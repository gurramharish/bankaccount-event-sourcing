package com.bank.account.infra.eventstore

import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBConnectionString

object EventStoreClientFactory {

    const val DEFAULT_CONNECTION_STRING = "esdb://localhost:2113?tls=false"

    fun create(connectionString: String = DEFAULT_CONNECTION_STRING): EventStoreDBClient {
        val settings = EventStoreDBConnectionString.parseOrThrow(connectionString)
        return EventStoreDBClient.create(settings)
    }
}
