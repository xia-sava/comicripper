# ComicRipper プロジェクト概要

## 概要

ComicRipperは、裁断したコミックをScanSnapでスキャンした画像ファイルを整理・管理するJavaFX/Kotlinデスクトップアプリケーション。

### 主要機能
- スキャンした画像（coverF*, coverS*, coverA*, page*）の自動分類・管理
- TesseractによるISBN読み取りと書籍情報自動取得（Amazon/ヨドバシ/Google Books）
- カバー画像から表紙部分の切り出し（Cutter）
- ファイル監視（JNotify）によるリアルタイム追加検知
- 「著者名/タイトル (巻数).zip」形式でのアーカイブ作成
- epub2comic.py によるEPUB展開（外部ユーティリティ）

### バージョン
- 現在: 0.7.5（major-restructuring ブランチ）
- master: 0.6.6（安定版）

## アーキテクチャ

2025年1月にモノリシック構造からレイヤードアーキテクチャへ移行済み。
後方互換性のため旧パスに typealias ファサードを維持。

```
src/main/kotlin/to/sava/comicripper/
├── domain/                          # ビジネスロジック層（UI非依存）
│   ├── model/Comic.kt              # ドメインモデル（画像キャッシュ・changeFlow・スレッドセーフコレクション含む）
│   └── service/FileWatcher.kt      # ファイル監視インターフェース
├── infrastructure/                  # 外部システム接続層
│   ├── repository/ComicRepository.kt  # ビジネスロジック + ComicStorage object 同居
│   └── service/JNotifyFileWatcher.kt  # JNotify によるファイル監視実装
├── application/                     # アプリケーション層
│   └── di/ApplicationModule.kt     # Koin DI設定
├── controller/                      # プレゼンテーション層
│   ├── MainController.kt           # メインウィンドウ（一覧・D&Dマージ・一括操作）
│   └── ComicController.kt          # コミックカード表示
├── ui/                               # Compose Desktop プレゼンテーション層（JavaFXから段階移行中）
│   ├── ComposeWindowHost.kt         # JVM常駐の Compose application スコープを保持し、JavaFX側から Compose ウィンドウの開閉を仲介するホスト
│   ├── ComposeExt.kt                # Compose 用拡張関数（アイコン Painter 生成など）
│   ├── ProgressOverlay.kt           # ウィンドウ内に被せる進捗オーバーレイの共通部品
│   ├── setting/SettingWindow.kt     # 設定画面（旧 SettingController + setting.fxml の置き換え）
│   ├── cutter/CutterWindow.kt       # カバー切り出しツール（旧 CutterController + cutter.fxml の置き換え）
│   └── detail/DetailWindow.kt       # 画像ビューア・メタデータ編集（旧 DetailController + detail.fxml の置き換え）
├── model/
│   ├── Comic.kt                    # typealias → domain.model.Comic
│   └── Setting.kt                  # アプリ設定シングルトン（Kotlin Flow）
├── repository/
│   ├── ComicRepository.kt          # typealias → infrastructure.repository
│   └── ComicStorage.kt             # typealias → infrastructure.repository
├── ext/ExtFunc.kt                  # 拡張関数（FXML読込・画像ユーティリティ・ダイアログ）
└── Main.kt                         # エントリポイント（Koin初期化・ライフサイクル管理）
```

### テスト
```
src/test/kotlin/to/sava/comicripper/
├── application/di/TestModule.kt              # テスト用Koin DI設定
└── infrastructure/service/
    ├── FileWatcherTest.kt                    # FileWatcher のテスト（JUnit5 + Koin）
    └── TestFileWatcher.kt                    # FileWatcher のテスト用モック実装
```

### リソース
- FXML: main, comic（+ parts/separator）
- CSS: common.css
- アイコン: icon.png, icon.ico

## 重要な技術的課題

### ファイル追加順序の重要性
```kotlin
// coverF が入ると ComicStorage.targetId が更新され、
// 以降の page ファイルはその Comic に追加される。
// 順序: coverF → page1 → page2 → coverF(次の巻) → page3...
// この順序が乱れるとページが間違った Comic に入る。
```

### ComicStorage の設計
- `infrastructure/repository/ComicRepository.kt` 内に `ComicStorage` object が同居
- MutableStateFlow<List<Comic>>（公開プロパティ名 `storage`）による観測可能なインメモリストレージ
- targetId で現在のファイル振り分け先を管理
- シングルトンのため、テスト時の状態リセットに注意が必要

### スレッド安全性
- Comic.files: CopyOnWriteArrayList
- Comic.thumbnails: ConcurrentHashMap
- JNotifyFileWatcher: synchronized キュー + コルーチン（200msバッチ）

## 技術スタック
- Kotlin 2.1.21, JavaFX 21
- Compose Desktop 1.8.2 (Material3)
- Koin 4.0.0（DI）, kotlinx-coroutines-swing 1.10.2
- JNotify 0.94（ファイル監視）, Jsoup 1.20.1（Webスクレイピング）, Gson 2.13.1, javax.json 1.1.4（一部APIのJSONパース）
- Gradle 8.12, Shadow JAR, JaCoCo
- テスト: JUnit5 + Koin Test + kotlinx-coroutines-test

## テスト戦略（方針）
- **Unit Test**: domain層の純粋なロジック
- **Integration Test**: infrastructure層とのやり取り
- **E2E Test**: ファイル監視からZIP作成までの一連の流れ

## 今後の展望

### 短期
1. 重要なビジネスロジックのテスト追加（ファイル振り分け・ZIP作成）
2. ComicRepository のテスト化（インメモリ実装・ファイルI/O抽象化）
3. Setting クラスの DI 対応

### 中期
1. 外部API処理の改善・統合テスト
2. javax.json を kotlinx.serialization 等へ置き換え検討

### 長期
1. Compose Desktop 移行（MVVM + Material3、コントローラ層の JavaFX 依存を置き換える本丸）。ComposeWindowHost 基盤を整備し、設定画面から画面単位での置き換えに着手済み
2. 画像処理の並列化・メモリ最適化
