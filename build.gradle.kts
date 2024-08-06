import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    kotlin("jvm") version "1.6.10"
    application
    id("org.openjfx.javafxplugin") version "0.0.12"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "to.sava.comicripper"
version = "0.6.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("no.tornado:tornadofx:1.7.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.6.1")
    implementation("net.contentobjects.jnotify:jnotify:0.94")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("org.jsoup:jsoup:1.15.3")

    testImplementation("junit", "junit", "4.12")
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(11))
    }
}

application {
    mainClass.set("to.sava.comicripper.Main")
}

tasks {
    named<ShadowJar>("shadowJar") {
        archiveFileName.set("comicripper.jar")
        mergeServiceFiles()
    }
}

javafx {
    version = "13"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.swing")
}
