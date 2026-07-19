import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    java
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    jacoco
}

group = "to.sava.comicripper"
version = "0.8.0"

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
    implementation("io.insert-koin:koin-compose:4.0.0")
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
                // MSIの上書きアップグレードを可能にするための固定UUID。バージョンを跨いで変更しないこと。
                upgradeUuid = "5A22A65E-EAC9-4228-B0E7-391B0A0D4C6A"
            }
        }
    }
}

tasks {
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
