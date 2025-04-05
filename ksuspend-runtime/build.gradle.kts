import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
}

kotlin {
    explicitApi()
    jvm {
        compilerOptions.jvmTarget = JvmTarget.JVM_1_8
    }
    mingwX64()
    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
        }
    }
}
//
//dependencies {
//    api(project(":ksuspend-annotations"))
//}
//
//tasks.compileKotlin {
//    compilerOptions.jvmTarget = JvmTarget.JVM_1_8
//}
//
//tasks.compileJava {
//    sourceCompatibility = "1.8"
//    targetCompatibility = "1.8"
//}
//
//
//publishing {
//    publications {
//        create<MavenPublication>("maven") {
//            from(components["java"])
//        }
//    }
//}