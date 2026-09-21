# Jelly Gallery 📸

超小型スマートフォン **Unihertz Jelly Star (Android 13)** から最新の **Android 16 搭載メインスマホ** まで快適に使える、軽量・高速・片手特化型のシンプルギャラリーアプリです。

## 特徴

- 📱 **Jelly Star & 通常スマホ両対応のレスポンシブ UI**
  - Jelly Star (3.0インチ / 480×854) では見やすい **2列グリッド** を標準採用。
  - メイン機などの通常画面では画面幅に合わせて自動調整。
  - ピンチイン/アウトで自由に変更可能（1〜5列）。
- 👆 **片手操作特化のサムゾーン (Thumb Zone)**
  - 画面下部に配置された 48dp 以上の大型アクションボタン（お気に入り・移動・共有・削除）。
  - 下からサッと引き出せるアルバム切り替え＆ファイル移動ボトムシート。
- 📁 **ファイル移動対応 (Scoped Storage 準拠)**
  - Android 11〜16 のプライバシー仕様に完全準拠し、アルバム間や新規アルバムへの移動が可能。
- 🎬 **アプリ内動画再生 (Media3 / ExoPlayer)**
  - 外部アプリに飛ばすことなく、インラインで即座に動画をシームレス再生。
- ⚡ **完全ローカル・バッテリー低消費**
  - クラウド同期や余計な通信は一切なし。
  - 有機EL / バッテリー容量（2,000mAh）に優しい AMOLED ダークテーマ。
- 🔄 **スマートな回転制御**
  - 一覧画面は片手で持ちやすい縦向き固定。
  - フルスクリーンビューアー時のみセンサー自動回転で画面いっぱいに表示。

## 技術スタック

- **UI**: Jetpack Compose, Material 3
- **言語**: Kotlin 2.0+
- **画像読み込み**: Coil 2.7
- **動画再生**: AndroidX Media3 ExoPlayer 1.5
- **メディア管理**: MediaStore API (Scoped Storage)
- **ターゲット**: Android 16 (API 35〜36), 最小要件 Android 13 (API 33)

## ビルド & インストール手順

### Android Studio で開く場合
1. Android Studio を起動し、`Open` からこのディレクトリ（`jelly-gallery`）を選択します。
2. 初回 Gradle Sync 完了後、Jelly Star またはエミュレータを接続して `Run` を押します。

### コマンドラインで APK をビルドする場合
```bash
./gradlew assembleDebug
```
生成先: `app/build/outputs/apk/debug/app-debug.apk`

ADB でインストール:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
