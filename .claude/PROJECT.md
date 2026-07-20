# ComicRipper プロジェクト概要

## 概要

ComicRipperは、裁断したコミックをScanSnapでスキャンした画像ファイルを整理・管理するCompose Desktop/Kotlinデスクトップアプリケーション。

### 主要機能
- スキャンした画像（coverF*, coverS*, coverA*, page*）の自動分類・管理
- TesseractによるISBN読み取りと書籍情報自動取得（Amazon/ヨドバシ/Google Books）
- カバー画像から表紙部分の切り出し（Cutter）
- ファイル監視（JNotify）によるリアルタイム追加検知
- 「著者名/タイトル (巻数).zip」形式でのアーカイブ作成
- epub2comic.py によるEPUB展開（外部ユーティリティ）

### バージョン
- 現在: 0.7.6

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
│   └── service/JNotifyFileWatcher.kt  # JNotify によるファイル監視実装
├── application/                     # アプリケーション層
│   └── di/ApplicationModule.kt     # Koin DI設定
├── ui/                               # Compose Desktop プレゼンテーション層（アプリの全画面）
│   ├── ComposeWindowHost.kt         # JVM常駐の Compose application スコープを保持し、key指定でウィンドウの開閉を仲介するホスト。
│   │                                  ウィンドウ単位の未捕捉例外を隔離する例外ハンドラと、ホスト終了通知(onTerminated)を持つ
│   ├── ComposeExt.kt                # Compose 用拡張関数（アイコン Painter 生成、初回表示時の前面化など）
│   ├── ComicRipperTheme.kt          # 共通テーマ（高密度化・グリーン系配色）
│   ├── CompactControls.kt           # 高密度な共通コントロール（CompactButton/CompactOutlinedTextField/CompactSlider）
│   ├── ComicRipperWindow.kt         # owner有無でトップレベルWindow/非モーダルオーナー付きDialogWindowを切り替える共通ウィンドウ
│   ├── ProgressOverlay.kt           # ウィンドウ内に被せる進捗オーバーレイの共通部品
│   ├── TextAreaOverlay.kt           # ウィンドウ内に被せる複数行テキスト入力オーバーレイの共通部品
│   ├── main/MainWindow.kt           # アプリのルートウィンドウ（一覧・D&Dマージ・一括操作・メモリ監視・epub展開）
│   ├── main/ComicCard.kt            # コミックカード表示・ドラッグ&ドロップの起点
│   ├── main/ComicDragState.kt       # カード間ドラッグ&ドロップの状態（ウィンドウ座標系でのヒットテスト）
│   ├── setting/SettingWindow.kt     # 設定画面
│   ├── cutter/CutterWindow.kt       # カバー切り出しツール
│   └── detail/DetailWindow.kt       # 画像ビューア・メタデータ編集
├── model/Setting.kt                # アプリ設定（Kotlin Flow、Koin single）
├── ext/ExtFunc.kt                  # 拡張関数（Loader、workFilename のみ）
└── Main.kt                         # エントリポイント（トップレベル fun main()。Koin初期化・
                                       CountDownLatchによるプロセス生存管理・ライフサイクル管理）
```

### テスト
```
src/test/kotlin/to/sava/comicripper/
├── application/di/TestModule.kt              # テスト用Koin DI設定
├── domain/model/ComicTest.kt                 # Comic のテスト（changeFlow・merge・ファイル管理）
├── model/SettingTest.kt                      # Setting のsave/load・Flow連動のテスト
└── infrastructure/
    ├── repository/
    │   ├── ComicRepositoryTest.kt             # ComicRepository のテスト（振り分け・正規化・保存復元・
    │   │                                        merge/release・reScanFiles・cutCover・zipComic・一括命名等）
    │   ├── ComicStorageTest.kt                 # ComicStorage のテスト
    │   └── ComicTestHelper.kt                  # テスト用ダミーJPEG生成・ディレクトリ設定ヘルパ
    └── service/
        ├── FileWatcherTest.kt                 # FileWatcher のテスト（JUnit5 + Koin）
        └── TestFileWatcher.kt                 # FileWatcher のテスト用モック実装
```

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
- JNotifyFileWatcher: synchronized キュー + コルーチン（200msバッチ）

## 技術スタック
- Kotlin 2.1.21
- Compose Desktop 1.8.2 (Material3)。JavaFXは完全に除去済み
- Koin 4.0.0（DI）+ koin-compose 4.0.0, kotlinx-coroutines-swing 1.10.2（Dispatchers.MainをAWT EDTに
  解決するために必須）。`ComicRepository`/`Setting`/`ComicStorage`はいずれもKoinの`single`登録で、
  Composable内は`koinInject()`、composition root（Main.kt）は`KoinJavaComponent.get()`経由で取得し
  単一インスタンスを共有する。domain層（`Comic.kt`）はKoinに依存させず、Main.ktから起動時に一度
  配線する`workDirectoryProvider`（関数型のcompanion var）で作業ディレクトリを解決する
- JNotify 0.94（ファイル監視）, Jsoup 1.20.1（Webスクレイピング）, kotlinx-serialization-json 1.7.3（Google Books APIのJSON解析）
- Gradle 8.12, JaCoCo
- 配布: Compose Desktopの`nativeDistributions`（jpackageベース）でWindows向けexe/msiを生成。
  JDK同梱・起動オプション埋め込み済みで、単一ファイル化のためのShadow JARは廃止した
- テスト: JUnit5 + Koin Test + kotlinx-coroutines-test

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
