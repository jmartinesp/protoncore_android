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
package me.proton.core.accountmanager.data

import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runBlockingTest
import me.proton.core.account.domain.entity.Account
import me.proton.core.account.domain.entity.AccountDetails
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.account.domain.entity.SessionState
import me.proton.core.domain.entity.Product
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.session.Session
import me.proton.core.network.domain.session.SessionId
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class AccountManagerImplTest {

    private lateinit var accountManager: AccountManagerImpl

    private val session1 = Session(
        sessionId = SessionId("session1"),
        accessToken = "accessToken",
        refreshToken = "refreshToken",
        scopes = listOf("full", "calendar", "mail")
    )

    private val account1 = Account(
        userId = UserId("user1"),
        username = "username",
        email = "test@example.com",
        state = AccountState.Ready,
        sessionId = session1.sessionId,
        sessionState = SessionState.Authenticated,
        details = AccountDetails(null)
    )

    private val mocks = RepositoryMocks(session1, account1)

    @Before
    fun beforeEveryTest() {
        mocks.init()

        accountManager = AccountManagerImpl(
            Product.Calendar,
            mocks.accountRepository,
            mocks.authRepository,
            mocks.userManager
        )
    }

    @Test
    fun `add user with session`() = runBlockingTest {
        coEvery { mocks.accountRepository.getSessionIdOrNull(any()) } returns null

        accountManager.addAccount(account1, session1)

        coVerify(exactly = 1) { mocks.accountRepository.createOrUpdateAccountSession(any(), any()) }
    }

    @Test
    fun `add user with existing session`() = runBlockingTest {
        coEvery { mocks.accountRepository.getSessionIdOrNull(any()) } returns session1.sessionId
        coEvery { mocks.accountRepository.getAccountOrNull(any<SessionId>()) } returns account1

        accountManager.addAccount(account1, session1)

        coVerify(exactly = 1) { mocks.accountRepository.createOrUpdateAccountSession(any(), any()) }
        coVerify(exactly = 1) { mocks.accountRepository.deleteSession(any()) }
        coVerify(exactly = 1) { mocks.authRepository.revokeSession(any()) }
    }

    @Test
    fun `on handleTwoPassModeSuccess`() = runBlockingTest {
        mocks.setupAccountRepository()

        accountManager.handleTwoPassModeSuccess(account1.userId)

        val stateLists = accountManager.onAccountStateChanged().toList()
        assertEquals(1, stateLists.size)
        assertEquals(AccountState.TwoPassModeSuccess, stateLists[0].state)
    }

    @Test
    fun `on handleTwoPassModeFailed`() = runBlockingTest {
        mocks.setupAccountRepository()

        accountManager.handleTwoPassModeFailed(account1.userId)

        val stateLists = accountManager.onAccountStateChanged().toList()
        assertEquals(1, stateLists.size)
        assertEquals(AccountState.TwoPassModeFailed, stateLists[0].state)
    }

    @Test
    fun `on handleSecondFactorSuccess`() = runBlockingTest {
        mocks.setupAccountRepository()

        val newScopes = listOf("scope1", "scope2")

        accountManager.handleSecondFactorSuccess(session1.sessionId, newScopes)

        val sessionLists = accountManager.getSessions().toList()
        assertEquals(2, sessionLists.size)
        assertEquals(session1.scopes, sessionLists[0][0].scopes)
        assertEquals(newScopes, sessionLists[1][0].scopes)

        val sessionStateLists = accountManager.onSessionStateChanged().toList()
        assertEquals(2, sessionStateLists.size)
        assertEquals(SessionState.SecondFactorSuccess, sessionStateLists[0].sessionState)
        assertEquals(SessionState.Authenticated, sessionStateLists[1].sessionState)
    }

    @Test
    fun `on handleSecondFactorFailed`() = runBlockingTest {
        mocks.setupAccountRepository()
        mocks.setupAuthRepository()

        accountManager.handleSecondFactorFailed(session1.sessionId)

        val stateLists = accountManager.onAccountStateChanged().toList()
        assertEquals(1, stateLists.size)
        assertEquals(AccountState.Disabled, stateLists[0].state)

        val sessionStateLists = accountManager.onSessionStateChanged().toList()
        assertEquals(1, sessionStateLists.size)
        assertEquals(SessionState.SecondFactorFailed, sessionStateLists[0].sessionState)
    }
}
