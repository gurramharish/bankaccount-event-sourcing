package com.bank.account.domain.model

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class AccountId(val value: String) {
    init {
        require(value.isNotBlank()) { "AccountId cannot be blank" }
    }
}
