/*
 * Copyright © 2025 RTAkland
 * Author: RTAkland
 * Date: 2025/4/3
 */

package cn.rtast.ksuspend.bridge.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

open class KSuspendBridgeGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        kotlinCompilation.dependencies {
            implementation("cn.rtast.ksuspend.bridge:ksuspend-annotations:0.0.1")
        }
        return kotlinCompilation.target.project.provider {
            mutableListOf(SubpluginOption("enabled", "true"))
        }
    }

    override fun getCompilerPluginId(): String = "cn.rtast.ksuspend.bridge"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "cn.rtast.ksuspend.bridge",
        artifactId = "ksuspend-compiler-plugin",
        version = "0.0.1"
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun apply(target: Project) {
//        target.dependencies()
    }
}