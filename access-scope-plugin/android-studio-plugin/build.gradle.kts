plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "dev.accessscope"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
}

kotlin {
    jvmToolchain(17)
}

intellij {
    version.set("2023.3.8")
    type.set("IC")
}

tasks {
    prepareSandbox {
        from("${rootProject.projectDir}/cli/build/libs/cli-1.0.0-all.jar") {
            into("access-scope-plugin/lib")
            rename { "access-scope-cli.jar" }
        }
    }
    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("243.*")
    }
}
