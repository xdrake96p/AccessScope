plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "dev.accessscope"
version = "1.0.5"

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
    version.set("2024.2.3")
    type.set("IC")
    pluginName.set("AccessScope")
}

tasks {
    prepareSandbox {
        dependsOn(":cli:fatJar")
        from("${rootProject.projectDir}/cli/build/libs/cli-1.0.0-all.jar") {
            into("AccessScope/lib")
            rename { "AccessScope-cli.jar" }
        }
    }
    processResources {
        dependsOn(":cli:fatJar")
        from("${rootProject.projectDir}/cli/build/libs/cli-1.0.0-all.jar") {
            rename { "AccessScope-cli.jar" }
        }
    }
    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("999.*")
    }
    buildPlugin {
        archiveFileName.set("AccessScope-${project.version}.zip")
    }
}
