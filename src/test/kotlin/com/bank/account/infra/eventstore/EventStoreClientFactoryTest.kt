package com.bank.account.infra.eventstore

import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EventStoreClientFactoryTest {

    @Test
    fun create_with_default_connection_string_initializes_client() {
        val client = EventStoreClientFactory.create()
        client.shouldNotBeNull()
    }

    @Test
    fun create_with_custom_connection_string_initializes_client() {
        val client = EventStoreClientFactory.create("esdb://127.0.0.1:2113?tls=false")
        client.shouldNotBeNull()
    }

    @Test
    fun create_with_invalid_connection_string_throws_exception() {
        assertThrows<Exception> {
            EventStoreClientFactory.create("invalid-connection-string")
        }
    }
}
