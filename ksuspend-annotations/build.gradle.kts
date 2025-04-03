import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm {
        compilerOptions.jvmTarget = JvmTarget.JVM_1_8
    }
    mingwX64()
    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()
}

publishing {
    publications {
        create<MavenPublication>("mavenKotlin") {
            from(components["kotlin"])
        }
    }
}