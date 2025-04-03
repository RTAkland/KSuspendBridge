include(":ksuspend-annotations")
include(":ksuspend-compiler-plugin")
include(":ksuspend-runtime")
include(":ksuspend-gradle-plugin")
include(":ksuspend-test-plugin")

pluginManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
    }
}