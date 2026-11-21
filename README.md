# ChargeF**k

充電に、無駄な感情を。Kotlinで作ったAndroid用のしょうもないオーバーレイアプリ。

## 使い方

1. APKをAndroid 8.0以降の端末にインストール。
2. 「オーバーレイを許可する」から「他のアプリの上に表示」を許可。
3. アプリに戻り「モヤを起動する」。通知も許可すると停止操作が便利です。
4. 充電ケーブルを抜き差し。端の薄いピンクのモヤが毎回びくっと反応します。
5. 素早く続けるとモヤが濃く、揺れも激しくなります。約6回で3回明滅し、画面全体がピンクに。約6.5秒で自然に元に戻ります。

間が空くと熱量が下がります。「抜き差しを試す」を連打すると、ケーブルなしでアプリ内のプレビューを確認できます。「停止してモヤを消す」または常駐通知の「停止」で終了します。起動は手動で、端末再起動時に自動起動しません。

## ビルド・検証

JDK 17以降、Android SDK 36を用意してAndroid Studioで開くか、SDKの場所を`local.properties`に設定してください。

```properties
sdk.dir=/your/path/to/Android/Sdk
```

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

`ChargeEngineTest`では、速い操作と遅い操作の差、約6回での到達、重複イベント、減衰、演出終了とリセットを検証します。

## 実装

- `MainActivity.kt`: 起動・停止・権限案内・アプリ内デモ。
- `OverlayService.kt`: ユーザーが起動するForeground Service。バッテリー状態のシステムイベントを受信し、実際に電源接続状態が変わった場合にだけ反応。画面ON時は150ms間隔でも最新の状態を確認し、Androidによるイベント配送待ちで高速な操作を取りこぼすのを抑えます。バッテリー残量や温度だけの変化は無視します。
- `MistView.kt`: Canvasの放射グラデーションによるモヤ。熱量で濃さ・広がり・揺れを増幅。画面OFF時は描画を停止。
- `ChargeEngine.kt`: 単調時計で計算する独立した反応ロジック。熱量しきい値6、1.4秒未満の操作に加点、毎秒0.3減衰。

演出は3回、約1.25Hzのピンクの明滅 → 全面ピンク → フェード。操作を通すため、オーバーレイウィンドウの不透明度は0.78です。画面全体をピンクで覆った際も背景が少し透けます。Androidの仕様でステータスバーなどのシステムUIや、オーバーレイを拒否するアプリの上には表示できない場合があります。

Androidの実装仕様: [透過オーバーレイとタッチの制限](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE)、[Foreground ServiceのspecialUse](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)。
