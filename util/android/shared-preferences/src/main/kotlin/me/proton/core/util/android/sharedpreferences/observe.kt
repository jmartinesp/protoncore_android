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

package me.proton.core.util.android.sharedpreferences

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * @return [Flow] that emits at every change in the receiver [SharedPreferences]
 */
fun SharedPreferences.observe(): Flow<SharedPreferencesChangeValue> = callbackFlow {

    // Emit initial value
    send(SharedPreferencesChangeValue(this@observe, null))

    val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        offer(SharedPreferencesChangeValue(sharedPreferences, key))
    }
    registerOnSharedPreferenceChangeListener(listener)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}

/**
 * @return [Flow] that emits at every change related to the given [key] in the receiver [SharedPreferences]
 */
inline fun <reified T> SharedPreferences.observe(key: String): Flow<T?> =
    observe()
        .map { it.sharedPreferences.get<T>(key) }
        .distinctUntilChanged()

/**
 * The model emitted then from [SharedPreferences.observe] on each change
 * @param key the key of the changed value
 *  it can be null only at initial emission, before any change happened
 */
data class SharedPreferencesChangeValue(
    val sharedPreferences: SharedPreferences,
    val key: String?
)
