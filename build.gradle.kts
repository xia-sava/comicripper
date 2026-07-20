import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    jacoco
}

group = "to.sava.comicripper"
version = "0.8.1"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jsoup)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit5)
    // Gradle 9.0以降、JUnit Platform launcherはビルトインではなく明示的な依存が必要。
    testRuntimeOnly(libs.junit.platform.launcher)
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
