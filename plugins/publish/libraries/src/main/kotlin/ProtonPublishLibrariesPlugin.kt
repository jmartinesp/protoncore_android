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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.extra
// import org.gradle.kotlin.dsl.named
// import org.jetbrains.dokka.gradle.DokkaPlugin
// import org.jetbrains.dokka.gradle.DokkaTask
import studio.forface.easygradle.dsl.*
import studio.forface.easygradle.publish.publish
import studio.forface.easygradle.publish.version
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Setup Publishing for whole Project.
 *
 * Setup sub-projects by generating KDoc, generating aar, updating readme, sign and publish new versions to Maven.
 */
abstract class ProtonPublishLibrariesPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.extensions.create(PublishLibrariesExtension.NAME, PublishLibrariesExtension::class.java, target)
        target.setupPublishing()

        target.subprojects {
            apply<ProtonPublishLibrariesPlugin>()
        }
    }
}

private fun Project.setupPublishing() {
    afterEvaluate {

        if (libVersion != null) {
            archivesBaseName = archiveName

            // Setup maven publish/signing config
            val mavenPassword = System.getenv()["MAVEN_PASSWORD"]
            publish {
                version = libVersion!!
                developers(applyDevelopers)
                licenses(applyLicences)
                baseUrl = System.getenv()["MAVEN_BASE_URL"] ?: "none"
                username = System.getenv()["MAVEN_USER"] ?: "none"
                password = mavenPassword ?: "none"
                signingEnabled = mavenPassword != null
                signingAsciiKey = System.getenv()["MAVEN_SIGNING_KEY"] ?: "none"
                signingPassword = System.getenv()["MAVEN_SIGNING_KEY_PASSWORD"] ?: "none"
                // Workaround, waiting on EasyPublish & vanniktech plugin update.
                stagingProfile = "me.proton"
                extra["POM_URL"] = "https://github.com/ProtonMail/protoncore_android"
                extra["SONATYPE_NEXUS_USERNAME"] = username
                extra["SONATYPE_NEXUS_PASSWORD"] = password
                extra["RELEASE_REPOSITORY_URL"] = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                extra["SNAPSHOT_REPOSITORY_URL"] = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            }

            with(ReleaseManager(this)) {
                if (mavenPassword != null) {
                    val uploadNewRelease = tasks.register("uploadNewRelease") {
                        dependsOn(tasks.named("uploadArchives"))
                    }
                    val closeNewRelease = tasks.register("closeNewRelease") {
                        mustRunAfter(uploadNewRelease)
                        doFirst {
                            project.rootProject.tasks.getByName("closeAndReleaseRepository") {
                                actions.forEach { it.execute(this) }
                            }
                        }
                    }
                    val updateNewRelease = tasks.register("updateNewRelease") {
                        mustRunAfter(closeNewRelease)
                        doFirst {
                            updateReadme()
                            printToNewReleasesFile()
                        }
                    }
                    tasks.register("publishNewRelease") {
                        if (isNewVersion) {
                            dependsOn(uploadNewRelease)
                            dependsOn(closeNewRelease)
                            dependsOn(updateNewRelease)
                        }
                    }
                } else {
                    tasks.register("updateReadme") {
                        doFirst { updateReadme() }
                    }
                }
            }
        }
    }
}

/**
 * This class will organize libraries release.
 *
 * It can:
 * * Move jar/aar archives into '/folderName/<libName>'
 * * Generate KDoc if new library is available
 * * Update readme with new version
 * * Print update to new_releases.tmp
 *
 * @param forceRefresh Generally all the processes are executed only of [Project.libVersion] is different from the one
 *   in the readme. If this parameter is set to `true`, they will run in any case.
 *   Default is `false`
 *
 * @author Davide Farella
 */
class ReleaseManager internal constructor(
    project: Project,
    forceRefresh: Boolean = false
) : Project by project {

    private val prevVersion = README_VERSION_REGEX.find(README_FILE.readText())?.groupValues?.get(1)
        ?: throw IllegalArgumentException("Cannot find version for $name: $README_VERSION_REGEX")
    private val versionName = libVersion!!.versionName

    val isNewVersion = forceRefresh || prevVersion != versionName
    private val shouldRefresh = forceRefresh || isNewVersion

    /** Move jar/aar archives into '/[folderName]/<libName>' */
    fun moveArchives(folderName: String) {
        // Setup folder
        val newDir = File(rootDir, folderName + File.separator + name)
        if (!newDir.exists()) newDir.mkdirs()

        moveJars(into = newDir)
        moveAars(into = newDir)
    }

    private fun moveJars(into: File) {
        JAR_DIRECTORY.listFiles()?.forEach { file ->
            val newFile = File(into, file.name.replace("-$versionName", ""))
            // If file is absent
            if (!newFile.exists()) {
                file.copyTo(newFile)
            }
            // DO NOT DELETE JAR FILE, IT'S REQUIRED BY JetifyTransform
        }
    }

    private fun moveAars(into: File) {
        AAR_DIRECTORY.listFiles()?.forEach { file ->
            if ("release" in file.name) {
                val newFile = File(
                    into,
                    file.name
                        .replace("-release", "")
                        .replace("-$versionName", "")
                )
                // If file is absent
                if (!newFile.exists()) {
                    file.copyTo(newFile)
                }
            }
            file.delete()
        }
    }

    /** Update readme with new version */
    fun updateReadme() {
        if (shouldRefresh) {
            val timestamp = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))

            README_FILE.writeText(
                README_FILE.readText()
                    .replace(README_VERSION_REGEX, readmeVersion(humanReadableName, versionName, timestamp))
            )
        }
    }

    /** Print release to new_releases.tmp */
    fun printToNewReleasesFile() {
        if (shouldRefresh) {
            NEW_RELEASES_FILE.writeText(
                "${NEW_RELEASES_FILE.readText()}$humanReadableName $versionName\n"
            )
        }
    }

    private companion object {
        val Project.JAR_DIRECTORY get() = File(buildDir, "libs")
        val Project.AAR_DIRECTORY get() = File(buildDir, "outputs" + File.separator + "aar")
        val Project.README_FILE get() = File(rootDir, "README.md")
        val Project.README_VERSION_REGEX get() =
            readmeVersion("^$humanReadableName", "(.+)", "(.+)").toRegex(RegexOption.MULTILINE)
        val Project.NEW_RELEASES_FILE get() = File(rootDir, "new_releases.tmp")
            .also { file ->
                // 10 min lifetime
                if (file.lastModified() < System.currentTimeMillis() - 10 * 60 * 1000) file.delete()
                if (!file.exists()) file.createNewFile()
            }

        fun readmeVersion(name: String, version: String, timestamp: String) =
            """$name: \*\*$version\*\* - _released on: ${timestamp}_"""
    }
}

