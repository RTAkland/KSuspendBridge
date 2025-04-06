plugins {
    kotlin("jvm")
    kotlin("kapt")
}

dependencies {
    kapt("com.google.auto.service:auto-service:1.1.1")
    compileOnly(kotlin("compiler-embeddable"))
    compileOnly(kotlin("compiler"))
    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
    compileOnly(project(":ksuspend-annotations"))
    compileOnly(project(":ksuspend-runtime"))
    compileOnly(kotlin("stdlib"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

tasks.compileKotlin {
    compilerOptions.freeCompilerArgs.addAll("-Xdump-directory=${project.layout.buildDirectory}/ir", "-Xphases-to-dump=ALL")
}