package com.bank.account.domain

import com.bank.account.domain.model.AccountCommand
import com.bank.account.domain.model.AccountEvent
import com.bank.account.domain.model.AccountId
import com.bank.account.domain.model.AccountState
import com.bank.account.domain.model.InsufficientFundsException
import com.bank.account.domain.model.InvalidAmountException
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class AccountDeciderTest {

    private val accountId = AccountId("acc-123")

    @Test
    fun initial_state_has_zero_balance_and_is_not_initialized() {
        val state = accountDecider.initialState

        state.accountId shouldBe null
        state.balance shouldBe BigDecimal.ZERO
        state.isInitialized shouldBe false
    }

    @Test
    fun deposit_emits_deposited_event(): Unit = runBlocking {
        val amount = BigDecimal("100.50")
        val command = AccountCommand.Deposit(accountId, amount)

        val events = accountDecider.decide(command, accountDecider.initialState).toList()

        events shouldHaveSize 1
        events[0] shouldBe AccountEvent.Deposited(accountId, amount)
    }

    @Test
    fun evolve_deposited_event_updates_state() {
        val amount = BigDecimal("100.50")
        val event = AccountEvent.Deposited(accountId, amount)

        val newState = accountDecider.evolve(accountDecider.initialState, event)

        newState.accountId shouldBe accountId
        newState.balance shouldBe amount
        newState.isInitialized shouldBe true
    }

    @Test
    fun deposit_with_zero_or_negative_amount_throws_invalid_amount_exception() {
        val zeroCommand = AccountCommand.Deposit(accountId, BigDecimal.ZERO)
        val negativeCommand = AccountCommand.Deposit(accountId, BigDecimal("-10.00"))

        assertThrows<InvalidAmountException> {
            runBlocking { accountDecider.decide(zeroCommand, accountDecider.initialState).toList() }
        }

        assertThrows<InvalidAmountException> {
            runBlocking { accountDecider.decide(negativeCommand, accountDecider.initialState).toList() }
        }
    }

    @Test
    fun withdraw_with_sufficient_balance_emits_withdrawn_event(): Unit = runBlocking {
        val currentState = AccountState(
            accountId = accountId,
            balance = BigDecimal("100.00"),
            isInitialized = true
        )
        val command = AccountCommand.Withdraw(accountId, BigDecimal("40.00"))

        val events = accountDecider.decide(command, currentState).toList()

        events shouldHaveSize 1
        events[0] shouldBe AccountEvent.Withdrawn(accountId, BigDecimal("40.00"))
    }

    @Test
    fun evolve_withdrawn_event_decrements_balance() {
        val startState = AccountState(
            accountId = accountId,
            balance = BigDecimal("100.00"),
            isInitialized = true
        )
        val event = AccountEvent.Withdrawn(accountId, BigDecimal("40.00"))

        val newState = accountDecider.evolve(startState, event)

        newState.accountId shouldBe accountId
        newState.balance shouldBe BigDecimal("60.00")
        newState.isInitialized shouldBe true
    }

    @Test
    fun withdraw_exceeding_balance_throws_insufficient_funds_exception() {
        val currentState = AccountState(
            accountId = accountId,
            balance = BigDecimal("50.00"),
            isInitialized = true
        )
        val command = AccountCommand.Withdraw(accountId, BigDecimal("50.01"))

        val exception = assertThrows<InsufficientFundsException> {
            runBlocking { accountDecider.decide(command, currentState).toList() }
        }

        exception.currentBalance shouldBe BigDecimal("50.00")
        exception.requestedAmount shouldBe BigDecimal("50.01")
    }

    @Test
    fun withdraw_with_zero_or_negative_amount_throws_invalid_amount_exception() {
        val currentState = AccountState(
            accountId = accountId,
            balance = BigDecimal("100.00"),
            isInitialized = true
        )
        val zeroCommand = AccountCommand.Withdraw(accountId, BigDecimal.ZERO)
        val negativeCommand = AccountCommand.Withdraw(accountId, BigDecimal("-5.00"))

        assertThrows<InvalidAmountException> {
            runBlocking { accountDecider.decide(zeroCommand, currentState).toList() }
        }

        assertThrows<InvalidAmountException> {
            runBlocking { accountDecider.decide(negativeCommand, currentState).toList() }
        }
    }

    @Test
    fun folding_events_sequentially_computes_exact_balance() {
        val events = listOf(
            AccountEvent.Deposited(accountId, BigDecimal("100.00")),
            AccountEvent.Deposited(accountId, BigDecimal("50.25")),
            AccountEvent.Withdrawn(accountId, BigDecimal("30.25")),
            AccountEvent.Deposited(accountId, BigDecimal("20.00"))
        )

        val finalState = events.fold(accountDecider.initialState) { state, event ->
            accountDecider.evolve(state, event)
        }

        finalState.accountId shouldBe accountId
        finalState.balance shouldBe BigDecimal("140.00")
        finalState.isInitialized shouldBe true
    }
}
