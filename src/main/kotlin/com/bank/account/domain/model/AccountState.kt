package com.bank.account.domain.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class AccountState(
    val accountId: AccountId? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val balance: BigDecimal = BigDecimal.ZERO,
    val isInitialized: Boolean = false
)
