plugins {
    kotlin("jvm")
    id("cn.rtast.ksuspend.bridge") version "0.0.1"
}

repositories {
    mavenLocal()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("cn.rtast.ksuspend.bridge:ksuspend-runtime:0.0.1")
}