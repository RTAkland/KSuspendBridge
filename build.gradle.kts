plugins {
    kotlin("jvm") version "2.1.20" apply false
    kotlin("multiplatform") version "2.1.20" apply false
    kotlin("kapt") version "2.1.20" apply false
    id("maven-publish")
}

subprojects {
    group = "cn.rtast.ksuspend.bridge"
    version = "0.0.1"

    repositories {
        mavenCentral()
        maven("https://repo.maven.rtast.cn/releases")
    }

    apply(plugin = "maven-publish")

    publishing {
        repositories {
            mavenLocal()
        }
    }
}