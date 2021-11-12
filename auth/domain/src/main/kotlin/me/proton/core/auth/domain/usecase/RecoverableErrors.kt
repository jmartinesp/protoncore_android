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

package me.proton.core.auth.domain.usecase

import me.proton.core.auth.domain.usecase.signup.PerformCreateExternalEmailUser
import me.proton.core.auth.domain.usecase.signup.PerformCreateUser
import me.proton.core.network.domain.ApiException
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.ResponseCodes

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_CONFLICT = 409

/** Error from [SetupPrimaryKeys]. */
fun Throwable.primaryKeyExists(): Boolean {
    val httpError = (this as? ApiException)?.error as? ApiResult.Error.Http
    return httpError?.httpCode == HTTP_BAD_REQUEST && httpError.proton?.code == ResponseCodes.NOT_ALLOWED
}

/** Error from [PerformCreateUser] or [PerformCreateExternalEmailUser]. */
fun Throwable.userAlreadyExists(): Boolean {
    val httpError = (this as? ApiException)?.error as? ApiResult.Error.Http
    return httpError?.httpCode == HTTP_CONFLICT && httpError.proton?.code == ResponseCodes.USER_CREATE_NAME_INVALID
}
