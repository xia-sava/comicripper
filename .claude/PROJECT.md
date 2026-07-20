# ComicRipper プロジェクト概要

## 概要

ComicRipperは、裁断したコミックをScanSnapでスキャンした画像ファイルを整理・管理するCompose Desktop/Kotlinデスクトップアプリケーション。

### 主要機能
- スキャンした画像（coverF*, coverS*, coverA*, page*）の自動分類・管理
- TesseractによるISBN読み取りと書籍情報自動取得（Amazon/ヨドバシ/Google Books）
- カバー画像から表紙部分の切り出し（Cutter）
- ファイル監視（java.nio.file.WatchService）によるリアルタイム追加検知
- 「著者名/タイトル (巻数).zip」形式でのアーカイブ作成
- epub2comic.py によるEPUB展開（外部ユーティリティ）
- 操作失敗のトースト通知（ErrorToast）

### バージョン
- 現在: 0.9.0

## アーキテクチャ

2025年1月にモノリシック構造からレイヤードアーキテクチャへ移行、2026年にJavaFXからCompose Desktopへ全面移行済み
（JavaFXプラグイン・依存は完全に除去済み）。旧JavaFXコントローラ向けに残していた後方互換用 typealias
ファサード（`model.Comic`/`repository.ComicRepository`/`repository.ComicStorage`）も、参照元のコントローラ
削除に伴い除去済み。全箇所`domain.model.Comic`/`infrastructure.repository.*`を直接参照する。

```
src/main/kotlin/to/sava/comicripper/
├── domain/                          # ビジネスロジック層（UI非依存）
│   ├── model/Comic.kt              # ドメインモデル（画像キャッシュ・changeFlow・スレッドセーフコレクション含む）
│   └── service/FileWatcher.kt      # ファイル監視インターフェース
├── infrastructure/                  # 外部システム接続層
│   ├── repository/ComicRepository.kt  # ビジネスロジック + ComicStorage（Koin single）同居
│   └── service/NioFileWatcher.kt   # java.nio.file.WatchService によるファイル監視実装
├── application/                     # アプリケーション層
│   ├── ApplicationScope.kt         # ウィンドウを閉じても完走させる処理用の共有CoroutineScope（Koin single）
│   └── di/ApplicationModule.kt     # Koin DI設定
├── ui/                               # Compose Desktop プレゼンテーション層（アプリの全画面）
│   ├── ComposeWindowHost.kt         # JVM常駐の Compose application スコープを保持し、key指定でウィンドウの開閉を仲介するホスト。
│   │                                  ウィンドウ単位の未捕捉例外を隔離する例外ハンドラと、ホスト終了通知(onTerminated)を持つ
│   ├── ComposeExt.kt                # Compose 用拡張関数（アイコン Painter 生成、初回表示時の前面化と
│   │                                  Compose コンテンツへのフォーカス付与など）
│   ├── ComicRipperTheme.kt          # 共通テーマ（高密度化・グリーン系配色）
│   ├── CompactControls.kt           # 高密度な共通コントロール（CompactButton/CompactOutlinedTextField/CompactSlider）
│   ├── ComicRipperWindow.kt         # owner有無でトップレベルWindow/非モーダルオーナー付きDialogWindowを切り替える共通ウィンドウ
│   ├── ProgressOverlay.kt           # ウィンドウ内に被せる進捗オーバーレイの共通部品
│   ├── TextAreaOverlay.kt           # ウィンドウ内に被せる複数行テキスト入力オーバーレイの共通部品
│   ├── ErrorToast.kt                # 操作失敗を知らせるトースト（画面下部・自動消滅）の共通部品
│   ├── main/MainWindow.kt           # アプリのルートウィンドウ（一覧・D&Dマージ・一括操作・メモリ監視・epub展開）
│   ├── main/ComicCard.kt            # コミックカード表示・ドラッグ&ドロップの起点
│   ├── main/ComicDragState.kt       # カード間ドラッグ&ドロップの状態（ウィンドウ座標系でのヒットテスト）
│   ├── setting/SettingWindow.kt     # 設定画面
│   ├── cutter/CutterWindow.kt       # カバー切り出しツール
│   └── detail/DetailWindow.kt       # 画像ビューア・メタデータ編集
├── model/Setting.kt                # アプリ設定（Kotlin Flow、Koin single、JSON永続化と旧形式からの自動移行）
├── ext/ExtFunc.kt                  # 拡張関数（Loader、workFilename のみ）
└── Main.kt                         # エントリポイント（トップレベル fun main()。Koin初期化・
                                       CountDownLatchによるプロセス生存管理・ライフサイクル管理）
```

### テスト
```
src/test/kotlin/to/sava/comicripper/
├── application/di/TestModule.kt              # テスト用Koin DI設定
├── domain/model/ComicTest.kt                 # Comic のテスト（changeFlow・merge・ファイル管理・画像LRUキャッシュ）
├── model/SettingTest.kt                      # Setting のsave/load・Flow連動・旧形式からの移行・破損時退避のテスト
├── ui/
│   ├── main/ComicCardTest.kt                 # 表示用文字列省略・サイズ計算のテスト
│   ├── main/ComicDragStateTest.kt            # D&D状態のテスト
│   └── cutter/CutterWindowTest.kt            # 画像表示矩形計算のテスト
└── infrastructure/
    ├── repository/
    │   ├── ComicRepositoryTest.kt             # ComicRepository のテスト（振り分け・正規化・保存復元・
    │   │                                        merge/release・reScanFiles・cutCover・zipComic・一括命名・
    │   │                                        構造ファイルの旧形式移行・破損時退避等）
    │   ├── ComicStorageTest.kt                 # ComicStorage のテスト
    │   └── ComicTestHelper.kt                  # テスト用ダミーJPEG生成・ディレクトリ設定ヘルパ
    └── service/
        ├── FileWatcherTest.kt                 # FileWatcher のテスト（JUnit5 + Koin）
        ├── NioFileWatcherTest.kt              # 実ファイルシステムに対するWatchService統合テスト
        └── TestFileWatcher.kt                 # FileWatcher のテスト用モック実装
```
テストは計108件。

### リソース
- アイコン: icon.png, icon.ico
（FXML・common.cssはJavaFX除去に伴い削除済み）

## 重要な技術的課題

### ファイル追加順序の重要性
```kotlin
// coverF が入ると ComicStorage.targetId が更新され、
// 以降の page ファイルはその Comic に追加される。
// 順序: coverF → page1 → page2 → coverF(次の巻) → page3...
// この順序が乱れるとページが間違った Comic に入る。
```

### ComicStorage の設計
- `infrastructure/repository/ComicRepository.kt` 内に `ComicStorage` クラスが同居し、Koinの`single`として
  アプリ全体で単一インスタンスを共有する
- MutableStateFlow<List<Comic>>（公開プロパティ名 `storage`）による観測可能なインメモリストレージ
- targetId で現在のファイル振り分け先を管理
- Koinスコープ内で単一インスタンスのため、テスト時の状態リセットに注意が必要

### スレッド安全性
- Comic.files: CopyOnWriteArrayList
- Comic.thumbnails: ConcurrentHashMap
- Comic.imageCache: 最終アクセス順LRU（LinkedHashMap + Collections.synchronizedMap、容量10）
- NioFileWatcher: WatchService の take() ブロッキング待機 + 200msバッチウィンドウ。
  監視開始失敗時は監視なしでアプリ起動を続行する

## 永続化・ログ

- 設定: `%LOCALAPPDATA%\ComicRipper\setting.json`（非Windowsは `~/.local/state/ComicRipper/`）。
  `@Serializable` データクラス経由のJSON形式で、一時ファイル書き込み + ATOMIC_MOVE により
  書きかけ破損を防ぐ。旧形式（`~/.comicripper.json`、さらに旧い `~/.comicripper` Properties形式）は
  初回ロード時に自動移行して `.bak` を残す
- 構造ファイル: `<workDirectory>/.comicripperStructure.json`。workDirectoryごとのデータのため
  作業ディレクトリ直下に置く。旧Properties形式からの自動移行は設定と同方式
- どちらもパース失敗時は該当ファイルを `.broken` へ退避してから既定値で続行する
  （上書き保存による手修復余地の喪失を防ぐ）
- ログ: `%LOCALAPPDATA%\ComicRipper\logs\comicripper.log`（kotlin-logging + logback、
  5MB×3世代のサイズローテーション。テスト実行時は logback-test.xml でコンソールのみに分離）

## 技術スタック
- Kotlin 2.4.0（serialization コンパイラプラグイン込み）
- Compose Multiplatform 1.11.1（Material3 は `org.jetbrains.compose.material3:material3:1.9.0` へ直接依存）。
  JavaFXは完全に除去済み
- Koin 4.1.1（DI）+ koin-compose, kotlinx-coroutines-swing 1.11.0（Dispatchers.MainをAWT EDTに
  解決するために必須）。`ComicRepository`/`Setting`/`ComicStorage`/`ApplicationScope`はいずれも
  Koinの`single`登録で、Composable内は`koinInject()`、composition root（Main.kt）は
  `KoinJavaComponent.get()`経由で取得し単一インスタンスを共有する。domain層（`Comic.kt`）は
  Koinに依存させず、Main.ktから起動時に一度配線する`workDirectoryProvider`
  （関数型のcompanion var）で作業ディレクトリを解決する
- ファイル監視は java.nio.file.WatchService（JDK標準、ネイティブDLL依存なし）
- Jsoup 1.22.2（Webスクレイピング）, kotlinx-serialization-json 1.11.0（Google Books APIのJSON解析と
  設定・構造ファイルのJSON永続化）
- kotlin-logging 8.0.4 + logback-classic 1.5.38（ロギング）
- Gradle 9.6.1（Version Catalog `gradle/libs.versions.toml` で依存を一元管理）, JaCoCo
- 配布: Compose Desktopの`nativeDistributions`（jpackageベース）でWindows向けexe/msiを生成。
  JDK同梱・起動オプション埋め込み済みで、単一ファイル化のためのShadow JARは廃止した
- テスト: JUnit 6 + Koin Test + kotlinx-coroutines-test

## テスト戦略（方針）
- **Unit Test**: domain層の純粋なロジック
- **Integration Test**: infrastructure層とのやり取り
- **E2E Test**: ファイル監視からZIP作成までの一連の流れ

## 今後の展望

以下は意図的に別ブランチ/別セッションで進める方針の項目（このブランチのスコープには含めない）。

### 中期
1. 外部API処理の改善・統合テスト（`searchISBN`/`ocrISBN`のテスト化。外部サイトへの実通信が絡むため
   HTTPモック基盤の整備が前提）
2. GitHub ActionsでのMSI自動ビルド＋アプリからの自動更新。バージョニング・署名（未署名だとSmartScreen
   警告が出る）等の設計が別途必要

### 長期
1. 画像処理の並列化・メモリ最適化
2. GraalVM Native Image化の実現性検証（Compose DesktopのSkia binding・JNotifyのJNI依存がネックになりうる
   ため、小さなスパイクでの検証が前提。exe/msi化とは無関係の別軸の話）

## 完了した移行

- **Compose Desktop 移行**（MVVM + Material3、JavaFX依存を置き換える本丸）: 完了。設定→カッタ→詳細→
  メイン画面の順に画面単位で置き換え、最後にJavaFXプラグイン・関連コードを除去した。詳細な移行過程・
  設計判断は`.claude/COMPOSE_MIGRATION.local.md`（gitignore対象）を参照。
- **javax.json → kotlinx.serialization 置き換え**: 完了。ついでに未使用だったGson依存も除去した。
- **Koin利用箇所の統一**: 完了。`ComicRepository`を`single`登録していたのにUI層が各々
  `remember { ComicRepository() }`で別インスタンスを生成していた不整合を修正し、全箇所Koin経由の
  取得に統一した。
- **ComicRepositoryのテスト拡充**: 完了。`removeFiles`/`removeFile`・`getNameList`/`setNameList`・
  `ocrISBN`の早期returnにテストを追加（`searchISBN`は実通信絡みのため対象外）。
- **Setting/ComicStorageのKoin DI対応**: 完了。両者を`object`から`class`へ変更しKoinの`single`登録に、
  `ComicRepository`はコンストラクタ注入に変更した。UI層は`koin-compose`の`koinInject()`に統一（既存の
  `ComicRepository`注入箇所も揃えた）。domain層（`Comic.kt`）はKoinに依存させず、composition root
  （Main.kt）から起動時に一度配線する`workDirectoryProvider`（既存の`thumbnailLoader`等と同じ関数型
  companion varパターン）で作業ディレクトリを解決する。
- **Compose Desktopネイティブ配布の整備**: 完了。`nativeDistributions`でWindows向けexe/msi
  （jpackageベース、JDK同梱）を生成できるようにし、手動起動コマンドで指定していたJVMオプションは
  パッケージ済みランチャーへ埋め込んだ。Shadow JARは役割が無くなったため除去した。MSIの
  `upgradeUuid`も固定発行済み。
- **後方互換用typealiasファサードの除去**: 完了。旧JavaFXコントローラ向けの
  `model.Comic`/`repository.ComicRepository`/`repository.ComicStorage`を除去し、全箇所
  `domain.model.Comic`/`infrastructure.repository.*`への直接参照に統一した。
- **依存基盤の近代化**: 完了。Gradle 9.6.1 / Kotlin 2.4.0 / Compose Multiplatform 1.11.1 /
  Koin 4.1.1 / JUnit 6 へ更新し、依存バージョンを Version Catalog に集約した。あわせて
  JNotify を java.nio.file.WatchService へ置き換え（ネイティブDLL依存を解消）、println を
  kotlin-logging + logback へ置き換え、`Comic.imageCache` を最終アクセス順LRUに変更、
  ZIP格納を STORED 化（JPEG再圧縮の回避）、分散していた CoroutineScope を `ApplicationScope`
  へ統一、操作失敗のトースト通知（ErrorToast）を追加した。
- **永続化のJSON化とアプリデータディレクトリ移行**: 完了。設定・構造ファイルを
  Properties形式から `@Serializable` データクラス経由のJSON形式へ移行し、保存先を
  ホームディレクトリ直下の dotfile から `%LOCALAPPDATA%\ComicRipper\` へ移した。
  旧形式ファイルは初回ロード時に自動移行して `.bak` を残す。
