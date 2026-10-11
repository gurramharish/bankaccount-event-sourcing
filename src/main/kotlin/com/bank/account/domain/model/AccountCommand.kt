package com.bank.account.domain.model

import java.math.BigDecimal

sealed interface AccountCommand {
    val accountId: AccountId

    data class Deposit(
        override val accountId: AccountId,
        val amount: BigDecimal
    ) : AccountCommand

    data class Withdraw(
        override val accountId: AccountId,
        val amount: BigDecimal
    ) : AccountCommand
}
