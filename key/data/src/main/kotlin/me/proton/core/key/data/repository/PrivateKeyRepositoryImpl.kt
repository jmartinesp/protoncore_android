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

package me.proton.core.key.data.repository

import me.proton.core.crypto.common.pgp.Armored
import me.proton.core.crypto.common.srp.Auth
import me.proton.core.domain.entity.SessionUserId
import me.proton.core.key.data.api.KeyApi
import me.proton.core.key.data.api.request.AuthRequest
import me.proton.core.key.data.api.request.CreateAddressKeyRequest
import me.proton.core.key.data.api.request.PrivateKeyRequest
import me.proton.core.key.data.api.request.SetupInitialKeysRequest
import me.proton.core.key.data.api.request.SignedKeyListRequest
import me.proton.core.key.data.api.request.UpdateKeysForPasswordChangeRequest
import me.proton.core.key.domain.entity.key.Key
import me.proton.core.key.domain.entity.key.PrivateAddressKey
import me.proton.core.key.domain.repository.PrivateKeyRepository
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.isSuccess
import me.proton.core.util.kotlin.toInt

class PrivateKeyRepositoryImpl(
    private val provider: ApiProvider
) : PrivateKeyRepository {

    private fun PrivateAddressKey.creationRequest(): CreateAddressKeyRequest {
        val signedKeyList = checkNotNull(signedKeyList) { "Signed key list for key creation is null"}
        return CreateAddressKeyRequest(
            addressId = addressId,
            privateKey = privateKey.key,
            primary = privateKey.isPrimary.toInt(),
            token = token,
            signature = signature,
            signedKeyList = SignedKeyListRequest(
                signedKeyList.data,
                signedKeyList.signature
            )
        )
    }


    override suspend fun setupInitialKeys(
        sessionUserId: SessionUserId,
        primaryKey: Armored,
        primaryKeySalt: String,
        addressKeys: List<PrivateAddressKey>,
        auth: Auth
    ) {
        return provider.get<KeyApi>(sessionUserId).invoke {
            setupInitialKeys(
                SetupInitialKeysRequest(
                    primaryKey = primaryKey,
                    keySalt = primaryKeySalt,
                    auth = AuthRequest.from(auth),
                    addressKeys = addressKeys.map { key -> key.creationRequest() }
                )
            )
        }.throwIfError()
    }

    override suspend fun createAddressKey(
        sessionUserId: SessionUserId,
        key: PrivateAddressKey
    ) {
        return provider.get<KeyApi>(sessionUserId).invoke {
            val request = key.creationRequest()
            if (key.token == null || key.signature == null) {
                createAddressKeyOld(request)
            } else {
                createAddressKey(request)
            }
        }.throwIfError()
    }

    override suspend fun updatePrivateKeys(
        sessionUserId: SessionUserId,
        keySalt: String,
        clientEphemeral: String,
        clientProof: String,
        srpSession: String,
        secondFactorCode: String,
        auth: Auth?,
        keys: List<Key>?,
        userKeys: List<Key>?,
        organizationKey: String
    ): Boolean {
        return provider.get<KeyApi>(sessionUserId).invoke {
            updatePrivateKeys(
                UpdateKeysForPasswordChangeRequest(
                    keySalt = keySalt,
                    clientEphemeral = clientEphemeral,
                    clientProof = clientProof,
                    srpSession = srpSession,
                    twoFactorCode = secondFactorCode,
                    auth = if (auth != null) AuthRequest.from(auth) else null,
                    keys = keys?.map {
                        PrivateKeyRequest(privateKey = it.privateKey, id = it.keyId.id)
                    },
                    userKeys = userKeys?.map {
                        PrivateKeyRequest(privateKey = it.privateKey, id = it.keyId.id)
                    },
                    organizationKey = organizationKey
                )
            ).isSuccess()
        }.valueOrThrow
    }
}
