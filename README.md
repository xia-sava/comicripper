
## コミックを裁断して ScanSnap でスキャンしたやつを最終的に zip にするのに便利にするやつ ##

うちではコミックをスキャンする時に，

 * coverF*.jpg → カバーを長尺スキャン
 * coverS*.jpg → 帯を長尺スキャン
 * page*.jpg → 本体をスキャン（カラー or モノクロ）

という命名規則にしている．

で，coverF のうち表紙画像だけを切り出してトップサムネイルを作っている．

これら jpg を単一 zip にまとめて， <著者名>/<単行本名> (<巻数>).zip として保存している．

この辺の作業をなるべく省力化したくて作ったもんである．


### アプリ動作環境 ###

基本的に Java なので run anywhere かもしれないけど，
実際うちでしか確認してないので知らん．

なくても動くかもしれないけど，いちおう以下のアプリが動く環境が必要．

 * JNotify
 * Tesseract

Tesseract は ISBN を読むだけなので Japanese pack は要らないと思う．


### 使用アイコン ###

Icons made by <a href="https://www.flaticon.com/free-icon/cut_2091550" title="srip">srip</a> from <a href="https://www.flaticon.com/" title="Flaticon">www.flaticon.com</a>
