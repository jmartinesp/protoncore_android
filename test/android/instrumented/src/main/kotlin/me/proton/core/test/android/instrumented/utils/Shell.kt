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

package me.proton.core.test.android.instrumented.utils

import androidx.test.platform.app.InstrumentationRegistry
import me.proton.core.test.android.instrumented.ProtonTest.Companion.getTargetContext
import me.proton.core.util.kotlin.toInt
import org.junit.runner.Description
import java.io.File

object Shell {

    private val automation = InstrumentationRegistry.getInstrumentation().uiAutomation!!

    /**
     * Deletes artifacts folder from /sdcard/Download.
     */
    fun deleteDownloadArtifactsFolder() {
        val downloadArtifactsPath = getTargetContext().getExternalFilesDir(null)!!.absolutePath
        automation.executeShellCommand("rm -rf $downloadArtifactsPath")
    }

    /**
     * Sets up device in ready for automation mode.
     * Animations turned off, long press timeout is set to 2 seconds, notifications popups are disabled.
     */
    fun setupDevice(shouldDisableNotifications: Boolean) {
        automation.executeShellCommand("settings put secure long_press_timeout 2000")
        automation.executeShellCommand("settings put global animator_duration_scale 0.0")
        automation.executeShellCommand("settings put global transition_animation_scale 0.0")
        automation.executeShellCommand("settings put global window_animation_scale 0.0")
        automation.executeShellCommand("settings put global heads_up_notifications_enabled ${shouldDisableNotifications.toInt()}")
    }

    /**
     * Can be used to test file sharing from outside of the app.
     * @param mimeType - file mime type
     * @param fileName - name of the file to share
     */
    fun sendShareFileIntent(mimeType: String, fileName: String) {
        automation
            .executeShellCommand(
                "am start -a android.intent.action.SEND -t $mimeType " +
                    "--eu android.intent.extra.STREAM " +
                    "file:///data/data/${getTargetContext().packageName}/files/$fileName " +
                    " --grant-read-uri-permission"
            )
    }
    /**
     * Clears logcat
     */
    fun clearLogcat() {
        automation.executeShellCommand("logcat -c")
    }

    /**
     * Saves file with provided [Description]
     */
    fun saveToFile(description: Description?) {
        val logcatArtifactsPath = "${getTargetContext().filesDir.path}/artifacts/logcat"
        val logcatFile = File(logcatArtifactsPath, "${description?.methodName}-logcat.txt")
        automation.executeShellCommand("run-as ${getTargetContext().packageName} -d -f $logcatFile")
    }
}
