/*
 * Copyright (c) 2021 Proton Technologies AG
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

package me.proton.core.eventmanager.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import me.proton.core.account.domain.entity.Account
import me.proton.core.account.domain.entity.AccountDetails
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.data.listener.ContactEventListener
import me.proton.core.eventmanager.data.listener.UserEventListener
import me.proton.core.eventmanager.domain.EventListener
import me.proton.core.eventmanager.domain.EventManager
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.EventManagerProvider
import me.proton.core.eventmanager.domain.entity.EventId
import me.proton.core.eventmanager.domain.entity.EventIdResponse
import me.proton.core.eventmanager.domain.entity.EventMetadata
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.eventmanager.domain.entity.State
import me.proton.core.eventmanager.domain.repository.EventMetadataRepository
import me.proton.core.eventmanager.domain.work.EventWorkerManager
import me.proton.core.presentation.app.AppLifecycleProvider
import org.junit.Before
import org.junit.Test

class EventManagerImplTest {

    private val coroutineScope = TestCoroutineScope()

    private lateinit var eventManagerFactor: EventManagerFactory
    private lateinit var eventManagerProvider: EventManagerProvider

    private lateinit var appLifecycleProvider: AppLifecycleProvider
    private lateinit var accountManager: AccountManager
    private lateinit var eventWorkerManager: EventWorkerManager
    private lateinit var eventMetadataRepository: EventMetadataRepository

    private lateinit var userEventListener: UserEventListener
    private lateinit var contactEventListener: ContactEventListener
    private lateinit var listeners: Set<EventListener<*, *>>

    private val user1 = Account(
        userId = UserId("user1"),
        username = "user1",
        email = "user1@protonmail.com",
        state = AccountState.Ready,
        sessionId = null,
        sessionState = null,
        details = AccountDetails(null)
    )
    private val user2 = Account(
        userId = UserId("user2"),
        username = "user2",
        email = "user2@protonmail.com",
        state = AccountState.Ready,
        sessionId = null,
        sessionState = null,
        details = AccountDetails(null)
    )
    private val accounts = listOf(user1, user2)

    private val user1Config = EventManagerConfig.Core(user1.userId)
    private val user2Config = EventManagerConfig.Core(user2.userId)

    private val eventId = "eventId"
    private val appState = MutableStateFlow(AppLifecycleProvider.State.Foreground)

    private lateinit var user1Manager: EventManager
    private lateinit var user2Manager: EventManager

    @Before
    fun before() {
        userEventListener = spyk(UserEventListener())
        contactEventListener = spyk(ContactEventListener())
        listeners = setOf<EventListener<*, *>>(userEventListener, contactEventListener)

        appLifecycleProvider = mockk {
            every { state } returns appState
        }
        accountManager = mockk {
            val userIdSlot = slot<UserId>()
            every { getAccount(capture(userIdSlot)) } answers {
                flowOf(accounts.firstOrNull { it.userId == userIdSlot.captured })
            }
        }
        eventWorkerManager = spyk()
        eventMetadataRepository = spyk()
        eventManagerFactor = mockk {
            val deserializerSlot = slot<EventDeserializer>()
            every { create(capture(deserializerSlot)) } answers {
                EventManagerImpl(
                    coroutineScope,
                    appLifecycleProvider,
                    accountManager,
                    eventWorkerManager,
                    eventMetadataRepository,
                    deserializerSlot.captured
                )
            }
        }

        eventManagerProvider = EventManagerProviderImpl(eventManagerFactor, listeners)
        user1Manager = eventManagerProvider.get(user1Config)
        user2Manager = eventManagerProvider.get(user2Config)

        coEvery { eventMetadataRepository.getLatestEventId(any(), any()) } returns
            EventIdResponse("{ \"EventID\": \"$eventId\" }")

        coEvery { eventMetadataRepository.getEvents(any(), any(), any()) } returns
            EventsResponse(TestEvents.coreFullEventsResponse)

        coEvery { eventMetadataRepository.update(any()) } returns Unit
        coEvery { eventMetadataRepository.updateState(any(), any(), any()) } returns Unit

        // GIVEN
        coEvery { eventMetadataRepository.get(user1Config) } returns
            listOf(EventMetadata(user1.userId, EventId(eventId), user1Config, createdAt = 1))

        coEvery { eventMetadataRepository.get(user2Config) } returns
            listOf(EventMetadata(user2.userId, EventId(eventId), user2Config, createdAt = 1))
    }

    @Test
    fun callCorrectPrepareUpdateDeleteCreate() = runBlocking {
        // WHEN
        user1Manager.process()
        user2Manager.process()
        // THEN
        coVerify(exactly = 2) { userEventListener.inTransaction(any()) }
        coVerify(exactly = 2) { contactEventListener.inTransaction(any()) }

        coVerify(exactly = 1) { userEventListener.onPrepare(user1.userId, any()) }
        coVerify(exactly = 1) { userEventListener.onUpdate(user1.userId, any()) }
        coVerify(exactly = 0) { userEventListener.onDelete(user1.userId, any()) }
        coVerify(exactly = 0) { userEventListener.onCreate(user1.userId, any()) }
        coVerify(exactly = 0) { userEventListener.onPartial(user1.userId, any()) }

        coVerify(exactly = 1) { contactEventListener.onPrepare(user1.userId, any()) }
        coVerify(exactly = 0) { contactEventListener.onUpdate(user1.userId, any()) }
        coVerify(exactly = 0) { contactEventListener.onDelete(user1.userId, any()) }
        coVerify(exactly = 1) { contactEventListener.onCreate(user1.userId, any()) }
        coVerify(exactly = 0) { contactEventListener.onPartial(user1.userId, any()) }

        coVerify(atLeast = 1) { eventMetadataRepository.updateState(user1Config, any(), State.NotifyPrepare) }
        coVerify(atLeast = 1) { eventMetadataRepository.updateState(user1Config, any(), State.NotifyEvents) }
        coVerify(atLeast = 1) { eventMetadataRepository.updateState(user1Config, any(), State.NotifyComplete) }
        coVerify(exactly = 1) { eventMetadataRepository.updateState(user1Config, any(), State.Completed) }

        coVerify(exactly = 1) { userEventListener.onPrepare(user2.userId, any()) }
        coVerify(exactly = 1) { userEventListener.onUpdate(user2.userId, any()) }
        coVerify(exactly = 0) { userEventListener.onDelete(user2.userId, any()) }
        coVerify(exactly = 0) { userEventListener.onCreate(user2.userId, any()) }
        coVerify(exactly = 0) { userEventListener.onPartial(user2.userId, any()) }

        coVerify(exactly = 1) { contactEventListener.onPrepare(user2.userId, any()) }
        coVerify(exactly = 0) { contactEventListener.onUpdate(user2.userId, any()) }
        coVerify(exactly = 0) { contactEventListener.onDelete(user2.userId, any()) }
        coVerify(exactly = 1) { contactEventListener.onCreate(user2.userId, any()) }
        coVerify(exactly = 0) { contactEventListener.onPartial(user2.userId, any()) }

        coVerify(atLeast = 1) { eventMetadataRepository.updateState(user2Config, any(), State.NotifyPrepare) }
        coVerify(atLeast = 1) { eventMetadataRepository.updateState(user2Config, any(), State.NotifyEvents) }
        coVerify(atLeast = 1) { eventMetadataRepository.updateState(user2Config, any(), State.NotifyComplete) }
        coVerify(exactly = 1) { eventMetadataRepository.updateState(user2Config, any(), State.Completed) }
    }

    @Test
    fun callOnPrepareThrowException() = runBlocking {
        // GIVEN
        coEvery { userEventListener.onPrepare(user1.userId, any()) } throws Exception("IOException")
        // WHEN
        user1Manager.process()
        // THEN
        coVerify(exactly = 2) { eventMetadataRepository.updateState(any(), any(), State.NotifyPrepare) }
        coVerify(exactly = 0) { userEventListener.onUpdate(user1.userId, any()) }
        coVerify(exactly = 0) { userEventListener.inTransaction(any()) }
    }

    @Test
    fun callOnUpdateThrowException() = runBlocking {
        // GIVEN
        coEvery { userEventListener.onUpdate(user1.userId, any()) } throws Exception("SqlForeignKeyException")
        // WHEN
        user1Manager.process()
        // THEN
        coVerify(exactly = 2) { eventMetadataRepository.updateState(any(), any(), State.NotifyPrepare) }
        coVerify(exactly = 2) { eventMetadataRepository.updateState(any(), any(), State.NotifyEvents) }
        coVerify(exactly = 1) { userEventListener.onUpdate(user1.userId, any()) }
        coVerify(exactly = 1) { userEventListener.inTransaction(any()) }
    }

    @Test
    fun preventMultiSubscribe() = runBlocking {
        // GIVEN
        user1Manager.subscribe(userEventListener)
        user1Manager.subscribe(userEventListener)
        user1Manager.subscribe(userEventListener)
        // WHEN
        user1Manager.process()
        // THEN
        coVerify(exactly = 1) { userEventListener.onPrepare(user1.userId, any()) }
        coVerify(exactly = 1) { userEventListener.onUpdate(user1.userId, any()) }
        coVerify(exactly = 0) { userEventListener.onDelete(user1.userId, any()) }
        coVerify(exactly = 0) { userEventListener.onCreate(user1.userId, any()) }
        coVerify(exactly = 0) { userEventListener.onPartial(user1.userId, any()) }
    }

    @Test
    fun preventEventIfNoUser() = runBlocking {
        // GIVEN
        coEvery { eventMetadataRepository.get(user1Config) } returns emptyList()
        // WHEN
        user1Manager.process()
        // THEN
        coVerify(exactly = 0) { userEventListener.onPrepare(any(), any()) }
        coVerify(exactly = 0) { userEventListener.onUpdate(any(), any()) }
        coVerify(exactly = 0) { userEventListener.onDelete(any(), any()) }
        coVerify(exactly = 0) { userEventListener.onCreate(any(), any()) }
        coVerify(exactly = 0) { userEventListener.onPartial(any(), any()) }
    }
}
