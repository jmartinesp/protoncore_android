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

package me.proton.core.user.data

import kotlinx.coroutines.flow.Flow
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.arch.DataResult
import me.proton.core.domain.entity.SessionUserId
import me.proton.core.key.domain.entity.key.PrivateAddressKey
import me.proton.core.key.domain.extension.primary
import me.proton.core.key.domain.repository.PrivateKeyRepository
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.entity.AddressId
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.user.domain.extension.firstInternalOrNull
import me.proton.core.user.domain.extension.hasMigratedKey
import me.proton.core.user.domain.repository.UserAddressRepository
import me.proton.core.user.domain.repository.UserRepository
import me.proton.core.user.domain.signKeyList

class UserAddressManagerImpl(
    private val userRepository: UserRepository,
    private val userAddressRepository: UserAddressRepository,
    private val privateKeyRepository: PrivateKeyRepository,
    private val userAddressKeySecretProvider: UserAddressKeySecretProvider,
    private val cryptoContext: CryptoContext
) : UserAddressManager {

    override fun getAddressesFlow(
        sessionUserId: SessionUserId,
        refresh: Boolean
    ): Flow<DataResult<List<UserAddress>>> = userAddressRepository.getAddressesFlow(sessionUserId, refresh = refresh)

    override suspend fun getAddresses(
        sessionUserId: SessionUserId,
        refresh: Boolean
    ): List<UserAddress> = userAddressRepository.getAddresses(sessionUserId, refresh = refresh)

    override suspend fun getAddress(
        sessionUserId: SessionUserId,
        addressId: AddressId,
        refresh: Boolean
    ): UserAddress? = userAddressRepository.getAddress(sessionUserId, addressId, refresh = refresh)

    override suspend fun setupInternalAddress(
        sessionUserId: SessionUserId,
        username: String,
        domain: String
    ): UserAddress {
        // Check if internal UserAddress already exist, and if needed create remotely.
        val userAddresses = userAddressRepository.getAddresses(sessionUserId)
        val userAddress = userAddresses.firstInternalOrNull() ?: createAddress(sessionUserId, username, domain)
        return createAddressKey(sessionUserId, userAddress.addressId, isPrimary = true)
    }

    private suspend fun createAddress(
        sessionUserId: SessionUserId,
        username: String,
        domain: String
    ) = userAddressRepository.createAddress(sessionUserId, username, domain)

    private suspend fun createAddressKey(
        sessionUserId: SessionUserId,
        addressId: AddressId,
        isPrimary: Boolean
    ): UserAddress {
        // Get User to get Primary Private Key.
        val user = userRepository.getUser(sessionUserId)
        val userPrimaryKey = user.keys.primary()
        checkNotNull(userPrimaryKey) { "User Primary Key doesn't exist." }

        val userAddresses = userAddressRepository.getAddresses(sessionUserId)
        val userAddress = userAddresses.firstOrNull { it.addressId == addressId }
        check(userAddress != null) { "User Address id doesn't exist." }

        // Check if key already exist.
        if (userAddress.keys.isNotEmpty()) return userAddress

        // If User have at least one migrated UserAddressKey (new key format), let's continue like this.
        val generateOldAddressKeyFormat = !userAddresses.hasMigratedKey()

        // Generate new UserAddressKey from user PrivateKey (according old vs new format).
        val userAddressKey = userAddressKeySecretProvider.generateUserAddressKey(
            generateOldFormat = generateOldAddressKeyFormat,
            userAddress = userAddress,
            userPrivateKey = userPrimaryKey.privateKey,
            isPrimary = isPrimary
        )
        val userAddressWithKeys = userAddress.copy(keys = userAddress.keys.plus(userAddressKey))
        // Create the new generated UserAddressKey, remotely.
        privateKeyRepository.createAddressKey(
            sessionUserId = sessionUserId,
            key = PrivateAddressKey(
                addressId = addressId.id,
                privateKey = userAddressKey.privateKey,
                token = userAddressKey.token,
                signature = userAddressKey.signature,
                signedKeyList = userAddressWithKeys.signKeyList(cryptoContext),
            )
        )
        return checkNotNull(userAddressRepository.getAddress(sessionUserId, addressId, refresh = true))
    }

    override suspend fun updateAddress(
        sessionUserId: SessionUserId,
        addressId: AddressId,
        displayName: String?,
        signature: String?
    ): UserAddress = userAddressRepository.updateAddress(sessionUserId, addressId, displayName, signature)

    override suspend fun updateOrder(
        sessionUserId: SessionUserId,
        addressIds: List<AddressId>,
    ): List<UserAddress> = userAddressRepository.updateOrder(sessionUserId, addressIds)
}
