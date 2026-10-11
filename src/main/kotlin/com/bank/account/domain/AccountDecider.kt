package com.bank.account.domain

import com.bank.account.domain.model.AccountCommand
import com.bank.account.domain.model.AccountEvent
import com.bank.account.domain.model.AccountState
import com.bank.account.domain.model.InsufficientFundsException
import com.bank.account.domain.model.InvalidAmountException
import com.fraktalio.fmodel.domain.Decider
import kotlinx.coroutines.flow.flowOf
import java.math.BigDecimal

val accountDecider: Decider<AccountCommand, AccountState, AccountEvent> = Decider(
    decide = { command, state ->
        when (command) {
            is AccountCommand.Deposit -> {
                if (command.amount <= BigDecimal.ZERO) {
                    throw InvalidAmountException(command.amount)
                }
                flowOf(AccountEvent.Deposited(command.accountId, command.amount))
            }
            is AccountCommand.Withdraw -> {
                if (command.amount <= BigDecimal.ZERO) {
                    throw InvalidAmountException(command.amount)
                }
                if (state.balance < command.amount) {
                    throw InsufficientFundsException(
                        currentBalance = state.balance,
                        requestedAmount = command.amount
                    )
                }
                flowOf(AccountEvent.Withdrawn(command.accountId, command.amount))
            }
        }
    },
    evolve = { state, event ->
        when (event) {
            is AccountEvent.Deposited -> state.copy(
                accountId = event.accountId,
                balance = state.balance.add(event.amount),
                isInitialized = true
            )
            is AccountEvent.Withdrawn -> state.copy(
                accountId = event.accountId,
                balance = state.balance.subtract(event.amount),
                isInitialized = true
            )
        }
    },
    initialState = AccountState()
)
