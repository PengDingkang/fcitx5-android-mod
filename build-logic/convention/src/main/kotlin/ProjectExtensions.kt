/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024-2026 Fcitx5 for Android Contributors
 */

import com.android.build.api.dsl.ApkSigningConfig
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.the
import java.io.File
import java.io.FileInputStream
import java.util.Properties
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun Project.runCmd(cmd: String, defaultValue: String = ""): String {
    val output = providers.exec {
        commandLine = cmd.split(" ")
    }
    return if (output.result.get().exitValue == 0) {
        output.standardOutput.asText.get().trim()
    } else {
        defaultValue
    }
}

val Project.libs get() = the<LibrariesForLibs>()

val Project.assetsDir: File
    get() = file("src/main/assets").also { it.mkdirs() }

val Project.cleanTask: Task
    get() = tasks.getByName("clean")

val Project.cmakeVersion
    get() = ep("CMAKE_VERSION", "cmakeVersion") { Versions.defaultCMake }

val Project.ndkVersion
    get() = ep("NDK_VERSION", "ndkVersion") { Versions.defaultNDK }

val Project.buildToolsVersion
    get() = ep("BUILD_TOOLS_VERSION", "buildTools") { Versions.defaultBuildTools }

val Project.buildVersionName
    get() = ep("BUILD_VERSION_NAME", "buildVersionName") {
        runCmd("git describe --tags --long --always", Versions.baseVersionName)
    }

val Project.buildBaseVersionCode
    get() = ep("BUILD_BASE_VERSION_CODE", "buildBaseVersionCode") {
        Versions.defaultBaseVersionCode.toString()
    }.toInt()

val Project.buildCommitHash
    get() = ep("BUILD_COMMIT_HASH", "buildCommitHash") {
        runCmd("git rev-parse HEAD", "N/A")
    }

val Project.buildTimestamp
    get() = ep("BUILD_TIMESTAMP", "buildTimestamp") {
        System.currentTimeMillis().toString()
    }

val Project.buildAbiOverride: String?
    get() = epn("BUILD_ABI", "buildABI") ?: localBuildProperty("buildABI")

val Project.signKeyBase64: String?
    get() = if (ciSigningBuild) epn("SIGN_KEY_BASE64", "signKeyBase64") else null

val Project.signKeyFile: String?
    get() = signingProperty("SIGN_KEY_FILE", "signKeyFile", "storeFile", "signKeyFile")

private fun File.loadPropertiesOrEmpty(): Properties {
    return Properties().apply {
        if (this@loadPropertiesOrEmpty.isFile) {
            FileInputStream(this@loadPropertiesOrEmpty).use(::load)
        }
    }
}

private val Project.ciSigningBuild: Boolean
    get() = System.getenv("GITHUB_ACTIONS").equals("true", ignoreCase = true) ||
            System.getenv("CI").equals("true", ignoreCase = true)

private val Project.localBuildPropertiesFile: File
    get() = rootProject.file("local.properties")

private val Project.localBuildProperties: Properties
    get() = localBuildPropertiesFile.loadPropertiesOrEmpty()

private fun Properties.firstNonBlank(vararg names: String): String? {
    return names.firstNotNullOfOrNull { name ->
        getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
    }
}

private fun Project.localBuildProperty(vararg names: String): String? {
    return localBuildProperties.firstNonBlank(*names)
}

private val Project.localSigningPropertiesFile: File
    get() = rootProject.file("keystore.properties")

private val Project.localSigningProperties: Properties
    get() = localSigningPropertiesFile.loadPropertiesOrEmpty()

private fun Project.localSigningProperty(vararg names: String): String? {
    return localSigningProperties.firstNonBlank(*names)
}

private fun Project.signingProperty(env: String, prop: String, vararg localNames: String): String? {
    return if (ciSigningBuild) {
        epn(env, prop)
    } else {
        localSigningProperty(*localNames)
    }
}

private fun Project.resolveSigningFile(path: String): File {
    val file = File(path)
    return if (file.isAbsolute) file else rootProject.file(path)
}

private var signKeyTempFile: File? = null

val Project.signKey: File?
    get() {
        signKeyFile?.let {
            val file = resolveSigningFile(it)
            if (file.exists()) return file
        }
        @OptIn(ExperimentalEncodingApi::class)
        signKeyBase64?.let {
            if (signKeyTempFile?.exists() == true) {
                return signKeyTempFile
            }
            val buildDir = layout.buildDirectory.asFile.get()
            buildDir.mkdirs()
            val file = File.createTempFile("sign-", ".ks", buildDir)
            try {
                file.writeBytes(Base64.decode(it))
                file.deleteOnExit()
                signKeyTempFile = file
                return file
            } catch (e: Exception) {
                println(e.localizedMessage ?: e.stackTraceToString())
                file.delete()
            }
        }
        return null
    }

val Project.signKeyPwd: String?
    get() = signingProperty("SIGN_KEY_PWD", "signKeyPwd", "storePassword", "signKeyPwd")

val Project.signKeyAlias: String?
    get() = signingProperty("SIGN_KEY_ALIAS", "signKeyAlias", "keyAlias", "signKeyAlias")

val Project.signKeyKeyPwd: String?
    get() = signingProperty(
        "SIGN_KEY_KEY_PWD",
        "signKeyKeyPwd",
        "keyPassword",
        "signKeyKeyPwd"
    ) ?: signKeyPwd

fun NamedDomainObjectContainer<out ApkSigningConfig>.fromProjectEnv(project: Project): ApkSigningConfig? {
    val keyFile = project.signKey ?: return null
    val name = "release"
    return findByName(name) ?: create(name) {
        storeFile = keyFile
        storePassword = project.signKeyPwd
        keyAlias = project.signKeyAlias
        keyPassword = project.signKeyKeyPwd
    }
}
