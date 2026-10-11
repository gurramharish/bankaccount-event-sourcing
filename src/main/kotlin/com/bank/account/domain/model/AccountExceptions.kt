package com.bank.account.domain.model

import java.math.BigDecimal

sealed class AccountException(message: String) : RuntimeException(message)

class InsufficientFundsException(
    val currentBalance: BigDecimal,
    val requestedAmount: BigDecimal
) : AccountException("Insufficient funds: current balance is $currentBalance, requested $requestedAmount")

class InvalidAmountException(
    val amount: BigDecimal
) : AccountException("Invalid amount: $amount. Amount must be strictly greater than zero")
