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

package me.proton.core.crypto.android.srp

import android.util.Base64
import com.proton.gopenpgp.crypto.ClearTextMessage
import com.proton.gopenpgp.srp.Auth
import com.proton.gopenpgp.srp.Proofs
import com.proton.gopenpgp.srp.Srp
import me.proton.core.crypto.common.srp.SrpCrypto
import me.proton.core.crypto.common.srp.SrpProofs
import java.security.SecureRandom

/**
 * Implementation of the [SrpCrypto] interface which returns the generated proofs based on the SRP library.
 */
class GOpenPGPSrpCrypto : SrpCrypto {

    /**
     * Generates SRP Proofs for login.
     */
    override fun generateSrpProofs(
        username: String,
        password: ByteArray,
        version: Long,
        salt: String,
        modulus: String,
        serverEphemeral: String
    ): SrpProofs {
        val auth = Auth(
            version,
            username,
            password,
            salt,
            modulus,
            serverEphemeral
        )
        return auth.generateProofs(SRP_BIT_LENGTH.toLong()).toSrpProofs()
    }

    override fun calculatePasswordVerifier(
        username: String,
        password: ByteArray,
        modulusId: String,
        modulus: String
    ): me.proton.core.crypto.common.srp.Auth {
        val salt = ByteArray(10)
        SecureRandom().nextBytes(salt)
        val base64Modulus = Base64.decode(ClearTextMessage(modulus).data, Base64.DEFAULT)
        val hashedPassword: ByteArray = Srp.hashPassword(
            CURRENT_AUTH_VERSION.toLong(),
            password,
            username,
            salt,
            base64Modulus
        )
        val passwordVerifier = PasswordVerifier(version = CURRENT_AUTH_VERSION, modulusId = modulusId, salt = salt)
        return passwordVerifier.generateAuth(SRP_BIT_LENGTH, base64Modulus, hashedPassword)
    }

    companion object {
        const val SRP_BIT_LENGTH: Int = 2048
        const val CURRENT_AUTH_VERSION = 4
    }
}

internal fun Proofs.toSrpProofs(): SrpProofs = SrpProofs(
    clientEphemeral,
    clientProof,
    expectedServerProof
)
