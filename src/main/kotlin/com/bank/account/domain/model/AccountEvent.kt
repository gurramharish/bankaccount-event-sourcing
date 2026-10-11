package com.bank.account.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
sealed interface AccountEvent {
    val accountId: AccountId
    val amount: BigDecimal

    @Serializable
    @SerialName("Deposited")
    data class Deposited(
        override val accountId: AccountId,
        @Serializable(with = BigDecimalSerializer::class)
        override val amount: BigDecimal
    ) : AccountEvent

    @Serializable
    @SerialName("Withdrawn")
    data class Withdrawn(
        override val accountId: AccountId,
        @Serializable(with = BigDecimalSerializer::class)
        override val amount: BigDecimal
    ) : AccountEvent
}
