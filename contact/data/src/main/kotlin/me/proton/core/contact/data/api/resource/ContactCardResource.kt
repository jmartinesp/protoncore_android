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

package me.proton.core.contact.data.api.resource

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.core.contact.domain.entity.ContactCard
import me.proton.core.contact.domain.entity.ContactCardType

@Serializable
data class ContactCardResource(
    @SerialName("Type")
    val type: Int,
    @SerialName("Data")
    val data: String,
    @SerialName("Signature")
    val signature: String? = null
)

fun ContactCardResource.toContactCard(): ContactCard {
    return when (ContactCardType.enumOf(type)?.enum) {
        ContactCardType.ClearText -> ContactCard.ClearText(data)
        ContactCardType.Signed -> ContactCard.Signed(data, signature!!)
        ContactCardType.Encrypted -> ContactCard.Encrypted(data, signature!!)
        null -> throw IllegalArgumentException("Unknown contact card type $this")
    }
}

fun ContactCard.toContactCardResource(): ContactCardResource {
    return when (this) {
        is ContactCard.ClearText -> ContactCardResource(ContactCardType.ClearText.value, data, null)
        is ContactCard.Encrypted -> ContactCardResource(ContactCardType.Encrypted.value, data, signature)
        is ContactCard.Signed -> ContactCardResource(ContactCardType.Signed.value, data, signature)
    }
}
