/*
 * Copyright (c) 2020 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.core.auth.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.account.domain.entity.AccountType
import me.proton.core.auth.domain.AccountWorkflowHandler
import me.proton.core.auth.domain.usecase.PerformSecondFactor
import me.proton.core.auth.domain.usecase.SetupAccountCheck
import me.proton.core.auth.domain.usecase.SetupAccountCheck.Result.ChangePasswordNeeded
import me.proton.core.auth.domain.usecase.SetupAccountCheck.Result.ChooseUsernameNeeded
import me.proton.core.auth.domain.usecase.SetupAccountCheck.Result.NoSetupNeeded
import me.proton.core.auth.domain.usecase.SetupAccountCheck.Result.SetupInternalAddressNeeded
import me.proton.core.auth.domain.usecase.SetupAccountCheck.Result.SetupPrimaryKeysNeeded
import me.proton.core.auth.domain.usecase.SetupAccountCheck.Result.TwoPassNeeded
import me.proton.core.auth.domain.usecase.SetupAccountCheck.Result.UserCheckError
import me.proton.core.auth.domain.usecase.SetupInternalAddress
import me.proton.core.auth.domain.usecase.SetupPrimaryKeys
import me.proton.core.auth.domain.usecase.UnlockUserPrimaryKey
import me.proton.core.crypto.common.keystore.EncryptedString
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.ApiException
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.session.SessionProvider
import me.proton.core.presentation.viewmodel.ProtonViewModel
import me.proton.core.user.domain.UserManager
import javax.inject.Inject

@HiltViewModel
class SecondFactorViewModel @Inject constructor(
    private val accountWorkflow: AccountWorkflowHandler,
    private val performSecondFactor: PerformSecondFactor,
    private val unlockUserPrimaryKey: UnlockUserPrimaryKey,
    private val setupAccountCheck: SetupAccountCheck,
    private val setupPrimaryKeys: SetupPrimaryKeys,
    private val setupInternalAddress: SetupInternalAddress,
    private val sessionProvider: SessionProvider
) : ProtonViewModel() {

    private val _state = MutableSharedFlow<State>(replay = 1, extraBufferCapacity = 3)

    val state = _state.asSharedFlow()

    sealed class State {
        object Idle : State()
        object Processing : State()
        sealed class Success : State() {
            data class UserUnLocked(val userId: UserId) : Success()
        }

        sealed class Need : State() {
            data class ChangePassword(val userId: UserId) : Need()
            data class TwoPassMode(val userId: UserId) : Need()
            data class ChooseUsername(val userId: UserId) : Need()
        }

        sealed class Error : State() {
            data class UserCheckError(val error: SetupAccountCheck.UserCheckResult.Error) : Error()
            data class CannotUnlockPrimaryKey(val error: UserManager.UnlockResult.Error) : Error()
            data class Message(val message: String?) : Error()
            object Unrecoverable : Error()
        }
    }

    fun stopSecondFactorFlow(
        userId: UserId
    ): Job = viewModelScope.launch {
        val sessionId = sessionProvider.getSessionId(userId)
        checkNotNull(sessionId) { "No session id for this user." }
        accountWorkflow.handleSecondFactorFailed(sessionId)
    }

    fun startSecondFactorFlow(
        userId: UserId,
        password: EncryptedString,
        requiredAccountType: AccountType,
        isTwoPassModeNeeded: Boolean,
        secondFactorCode: String
    ) = flow {
        emit(State.Processing)

        val sessionId = sessionProvider.getSessionId(userId)
        checkNotNull(sessionId) { "No session id for this user." }

        val scopeInfo = performSecondFactor.invoke(sessionId, secondFactorCode)
        accountWorkflow.handleSecondFactorSuccess(sessionId, scopeInfo.scopes)

        // Check if setup keys is needed and if it can be done directly.
        when (val result = setupAccountCheck.invoke(userId, isTwoPassModeNeeded, requiredAccountType)) {
            is UserCheckError -> checkFailed(userId, result.error)
            is TwoPassNeeded -> twoPassMode(userId)
            is ChangePasswordNeeded -> changePassword(userId)
            is ChooseUsernameNeeded -> chooseUsername(userId)
            is SetupPrimaryKeysNeeded -> setupPrimaryKeys(userId, password, requiredAccountType)
            is SetupInternalAddressNeeded -> setupInternalAddress(userId, password)
            is NoSetupNeeded -> unlockUserPrimaryKey(userId, password)
        }.let {
            emit(it)
        }
    }.catch { error ->
        if (error.isUnrecoverableError()) {
            emit(State.Error.Unrecoverable)
        } else {
            emit(State.Error.Message(error.message))
        }
    }.onEach { state ->
        _state.tryEmit(state)
    }.launchIn(viewModelScope)

    private suspend fun checkFailed(
        userId: UserId,
        error: SetupAccountCheck.UserCheckResult.Error
    ): State {
        accountWorkflow.handleAccountDisabled(userId)
        return State.Error.UserCheckError(error)
    }

    private suspend fun twoPassMode(
        userId: UserId,
    ): State {
        accountWorkflow.handleTwoPassModeNeeded(userId)
        return State.Need.TwoPassMode(userId)
    }

    private suspend fun changePassword(
        userId: UserId
    ): State {
        accountWorkflow.handleAccountDisabled(userId)
        return State.Need.ChangePassword(userId)
    }

    private suspend fun unlockUserPrimaryKey(
        userId: UserId,
        password: EncryptedString
    ): State {
        val result = unlockUserPrimaryKey.invoke(userId, password)
        return if (result == UserManager.UnlockResult.Success) {
            accountWorkflow.handleAccountReady(userId)
            State.Success.UserUnLocked(userId)
        } else {
            accountWorkflow.handleUnlockFailed(userId)
            State.Error.CannotUnlockPrimaryKey(result as UserManager.UnlockResult.Error)
        }
    }

    private suspend fun setupPrimaryKeys(
        userId: UserId,
        password: EncryptedString,
        requiredAccountType: AccountType
    ): State {
        setupPrimaryKeys.invoke(userId, password, requiredAccountType)
        return unlockUserPrimaryKey(userId, password)
    }

    private suspend fun setupInternalAddress(
        userId: UserId,
        password: EncryptedString
    ): State {
        val result = unlockUserPrimaryKey.invoke(userId, password)
        return if (result is UserManager.UnlockResult.Success) {
            setupInternalAddress.invoke(userId)
            accountWorkflow.handleAccountReady(userId)
            State.Success.UserUnLocked(userId)
        } else {
            accountWorkflow.handleUnlockFailed(userId)
            State.Error.CannotUnlockPrimaryKey(result as UserManager.UnlockResult.Error)
        }
    }

    private suspend fun chooseUsername(
        userId: UserId
    ): State {
        accountWorkflow.handleCreateAddressNeeded(userId)
        return State.Need.ChooseUsername(userId)
    }

    private fun Throwable.isUnrecoverableError(): Boolean {
        if (this is ApiException && error is ApiResult.Error.Http) {
            val httpCode = (error as ApiResult.Error.Http).httpCode
            return httpCode in listOf(HTTP_ERROR_UNAUTHORIZED, HTTP_ERROR_BAD_REQUEST)
        }
        return false
    }

    companion object {
        const val HTTP_ERROR_UNAUTHORIZED = 401
        const val HTTP_ERROR_BAD_REQUEST = 400
    }
}
