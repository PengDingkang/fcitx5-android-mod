/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.tasks.ProcessLibraryArtProfileTask
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

@Suppress("unused")
class AndroidLibConventionPlugin : AndroidBaseConventionPlugin() {

    override fun apply(target: Project) {
        target.pluginManager.apply(target.libs.plugins.android.library.get().pluginId)

        super.apply(target)

        target.extensions.configure<LibraryExtension> {
            buildTypes {
                create("dev") {
                    initWith(getByName("debug"))
                    matchingFallbacks += listOf("debug", "release")
                }
            }
        }

        // disable baseline profile tasks
        target.tasks.withType<ProcessLibraryArtProfileTask> { enabled = false }
    }

}
