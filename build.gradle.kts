import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    kotlin("jvm") version "1.4.0"
    id("application")
    id("org.openjfx.javafxplugin") version "0.0.8"
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

group = "to.sava.comicripper"
version = "0.2"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("no.tornado:tornadofx:1.7.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.3.6")
    implementation("net.contentobjects.jnotify:jnotify:0.94")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("org.jsoup:jsoup:1.13.1")
    implementation("net.sourceforge.htmlunit:htmlunit:2.40.0")

    testImplementation("junit", "junit", "4.12")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

application {
    mainClassName = "to.sava.comicripper.Main"
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    named<ShadowJar>("shadowJar") {
        archiveBaseName.set("app")
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to application.mainClassName))
        }
    }
}

javafx {
    version = "13"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.swing")
}
