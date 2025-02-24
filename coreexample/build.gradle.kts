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

import studio.forface.easygradle.dsl.*
import studio.forface.easygradle.dsl.android.*
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("dagger.hilt.android.plugin")
}

android(
    version = Version(1, 18, 0),
    useViewBinding = true
)
{
    defaultConfig {
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
                // arguments["room.incremental"] = "true"
            }
        }

        val localProperties = Properties().apply {
            try {
                load(FileInputStream("local.properties"))
            } catch (e: java.io.FileNotFoundException) {
                logger.warn("No local.properties found")
            }
        }
        val proxyToken: String? = localProperties.getProperty("PROXY_TOKEN")
        val host: String = localProperties.getProperty("HOST")?: "proton.black"

        buildConfigField("String", "PROXY_TOKEN", proxyToken.toBuildConfigValue())
        buildConfigField("String", "HOST", host.toBuildConfigValue())
    }
    sourceSets.getByName("androidTest") {
        // Add schema for android tests
        assets.srcDirs("$projectDir/schemas")
    }
}

dependencies {

    implementation(

        project(Module.kotlinUtil),
        project(Module.presentation),
        project(Module.network),
        project(Module.domain),
        project(Module.data),
        project(Module.dataRoom),

        // Features
        project(Module.account),
        project(Module.accountManager),
        project(Module.accountManagerDagger),
        project(Module.auth),
        project(Module.contact),
        project(Module.contactHilt),
        project(Module.crypto),
        project(Module.domain),
        project(Module.eventManager),
        project(Module.gopenpgp),
        project(Module.humanVerification),
        project(Module.key),
        project(Module.user),
        project(Module.mailMessage),
        project(Module.mailSettings),
        project(Module.payment),
        project(Module.country),
        project(Module.plan),
        project(Module.userSettings),

        `kotlin-jdk7`,
        `coroutines-android`,

        // Android
        `activity`,
        `appcompat`,
        `android-work-runtime`,
        `constraint-layout`,
        `fragment`,
        `gotev-cookieStore`,
        `hilt-android`,
        `hilt-androidx-workManager`,
        `lifecycle-extensions`,
        `lifecycle-viewModel`,
        `material`,

        // Other
        `room-ktx`,
        `retrofit`,
        `timber`,
        `ez-vcard`
    )

    kapt(
        `hilt-android-compiler`,
        `hilt-androidx-compiler`,
        `room-compiler`,
        `lifecycle-compiler`
    )

    // Test
    testImplementation(project(Module.androidTest))
    androidTestImplementation(project(Module.androidInstrumentedTest))

    // Lint - off temporary
    // lintChecks(project(Module.lint))
}
