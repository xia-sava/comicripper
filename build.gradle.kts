import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    kotlin("jvm") version "2.1.21"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.gradleup.shadow") version "8.3.6"
    jacoco
}

group = "to.sava.comicripper"
version = "0.7.2"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.10.2")
    implementation("org.glassfish:javax.json:1.1.4")
    implementation("net.contentobjects.jnotify:jnotify:0.94")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.jsoup:jsoup:1.20.1")
    implementation("io.insert-koin:koin-core:4.0.0")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("io.insert-koin:koin-test:4.0.0")
    testImplementation("io.insert-koin:koin-test-junit5:4.0.0")
}

kotlin {
    jvmToolchain {
        this.languageVersion.set(JavaLanguageVersion.of(21))
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
    test {
        useJUnitPlatform()
        finalizedBy(jacocoTestReport)
    }
    jacocoTestReport {
        dependsOn(test)
        reports {
            html.required.set(true)
            xml.required.set(true)
        }
    }
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.swing")
}
