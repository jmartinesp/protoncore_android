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

package me.proton.android.core.coreexample.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.proton.android.core.coreexample.db.AppDatabase
import me.proton.core.account.data.db.AccountDatabase
import me.proton.core.contact.data.local.db.ContactDatabase
import me.proton.core.eventmanager.data.db.EventMetadataDatabase
import me.proton.core.humanverification.data.db.HumanVerificationDatabase
import me.proton.core.key.data.db.KeySaltDatabase
import me.proton.core.key.data.db.PublicAddressDatabase
import me.proton.core.mailsettings.data.db.MailSettingsDatabase
import me.proton.core.user.data.db.AddressDatabase
import me.proton.core.user.data.db.UserDatabase
import me.proton.core.usersettings.data.db.OrganizationDatabase
import me.proton.core.usersettings.data.db.UserSettingsDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppDatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.buildDatabase(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppDatabaseBindsModule {
    @Binds
    abstract fun provideAccountDatabase(appDatabase: AppDatabase): AccountDatabase

    @Binds
    abstract fun provideUserDatabase(appDatabase: AppDatabase): UserDatabase

    @Binds
    abstract fun provideAddressDatabase(appDatabase: AppDatabase): AddressDatabase

    @Binds
    abstract fun provideKeySaltDatabase(appDatabase: AppDatabase): KeySaltDatabase

    @Binds
    abstract fun providePublicAddressDatabase(appDatabase: AppDatabase): PublicAddressDatabase

    @Binds
    abstract fun provideHumanVerificationDatabase(appDatabase: AppDatabase): HumanVerificationDatabase

    @Binds
    abstract fun provideMailSettingsDatabase(appDatabase: AppDatabase): MailSettingsDatabase

    @Binds
    abstract fun provideUserSettingsDatabase(appDatabase: AppDatabase): UserSettingsDatabase

    @Binds
    abstract fun provideOrganizationDatabase(appDatabase: AppDatabase): OrganizationDatabase

    @Binds
    abstract fun provideContactDatabase(appDatabase: AppDatabase): ContactDatabase

    @Binds
    abstract fun provideEventMetadataDatabase(appDatabase: AppDatabase): EventMetadataDatabase
}
