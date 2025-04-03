plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    implementation(gradleApi())
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.20")
    implementation(project(":ksuspend-compiler-plugin"))
}

gradlePlugin {
    plugins {
        create("ksuspendBridge") {
            id = "cn.rtast.ksuspend.bridge"
            implementationClass = "cn.rtast.ksuspend.bridge.gradle.KSuspendBridgeGradlePlugin"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
