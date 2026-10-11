package com.bank.account.infra.serialization

import com.bank.account.domain.model.AccountState
import kotlinx.serialization.Serializable

@Serializable
data class AccountSnapshot(
    val state: AccountState,
    val snapshotRevision: Long,
    val timestampEpochMillis: Long
)
