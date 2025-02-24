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

package me.proton.core.contact.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.proton.core.contact.data.local.db.entity.ContactEmailLabelEntity
import me.proton.core.contact.domain.entity.ContactEmailId
import me.proton.core.data.room.db.BaseDao

@Dao
abstract class ContactEmailLabelDao: BaseDao<ContactEmailLabelEntity>() {
    @Transaction
    @Query("SELECT labelId FROM ContactEmailLabelEntity WHERE contactEmailId = :contactEmailId")
    abstract fun observeAllLabels(contactEmailId: ContactEmailId): Flow<List<String>>

    @Query("DELETE FROM ContactEmailLabelEntity WHERE contactEmailId IN (:contactEmailIds)")
    abstract suspend fun deleteAllLabels(vararg contactEmailIds: ContactEmailId)
}
