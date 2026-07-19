import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    java
    kotlin("jvm") version "2.1.21"
    id("com.gradleup.shadow") version "8.3.6"
    id("org.jetbrains.compose") version "1.8.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    jacoco
}

group = "to.sava.comicripper"
version = "0.7.5"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("net.contentobjects.jnotify:jnotify:0.94")
    implementation("org.jsoup:jsoup:1.20.1")
    implementation("io.insert-koin:koin-core:4.0.0")
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("io.insert-koin:koin-test:4.0.0")
    testImplementation("io.insert-koin:koin-test-junit5:4.0.0")
}

kotlin {
    jvmToolchain {
        this.languageVersion.set(JavaLanguageVersion.of(21))
    }
}

compose.desktop {
    application {
        mainClass = "to.sava.comicripper.MainKt"
        // 起動オプション（旧来の手動起動コマンドで指定していたもの）をパッケージ済みランチャーへ埋め込む。
        jvmArgs("-Xmx16g", "-Dhttps.protocols=TLSv1,TLSv1.1,TLSv1.2")

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "ComicRipper"
            packageVersion = version.toString()
            description = "スキャンしたコミック画像の整理・管理ツール"
            vendor = "to.sava"

            // モジュール不足によるパッケージ後の実行時エラーを避けるため、JDK全体を同梱する。
            includeAllModules = true

            windows {
                iconFile.set(project.file("src/main/resources/to/sava/comicripper/icon.ico"))
                shortcut = true
                menu = true
            }
        }
    }
}

tasks {
    named<ShadowJar>("shadowJar") {
        archiveFileName.set("comicripper.jar")
        mergeServiceFiles()
        // application プラグインを外したため、Main-Class を明示する。
        manifest {
            attributes["Main-Class"] = "to.sava.comicripper.MainKt"
        }
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
