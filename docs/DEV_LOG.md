# 開發紀錄

最新紀錄放最上面。每次開發工作都要更新，不要事後補寫。

---

## 2026-11-08 - v1.1.7（versionCode 11）

### 目標
讓小工具控制圖示平時自動隱藏，點擊底部控制區域才顯示，並在閒置數秒後再次隱藏。

### 變更
- `PhotoWidgetStateStore.kt`：新增每個小工具的 `controlsVisible` 狀態。
- `PhotoWidgetProvider.kt`：
  - 新增 `ACTION_SHOW_CONTROLS`、`ACTION_HIDE_CONTROLS`。
  - 未操作時圖示以 `INVISIBLE` 隱藏，整條底部區域仍可點擊以顯示圖示。
  - 顯示後 4 秒未操作自動隱藏；切換照片或重新整理會重新計時。
  - 系統更新、小工具尺寸變更或 App 回到前景時回到隱藏狀態。
- `README.md`、`docs/USER_GUIDE.md`：補充控制圖示自動隱藏與點擊顯示的操作說明。
- 版本更新為 `versionCode 11`、`versionName 1.1.7`。

### 問題與解法
- RemoteViews 無法在 Launcher 內直接執行計時動畫，改以 Handler 延遲送出隱藏廣播；程序若被系統回收，圖示可能維持顯示直到下次更新。

### 待辦／已知限制
- 自動隱藏計時依賴 App 程序存活，極端情況下可能延後隱藏。
- 尚未在不同 Launcher 與省電模式下實測點擊區域與自動隱藏時機。

## 2026-11-08 - v1.1.6（versionCode 10）

### 目標
將小工具控制圖示顏色調淺，在沒有底色與漸層的情況下仍保持可見。

### 變更
- `widget_control_tint.xml`：啟用圖示由 `#4D4D4D` 改為 `#8C8C8C`，停用圖示改為 `#808C8C8C`。
- 版本更新為 `versionCode 10`、`versionName 1.1.6`。

### 問題與解法
- 無。

### 待辦／已知限制
- 淺灰圖示在明亮相片上對比仍可能偏低。

## 2026-11-08 - v1.1.5（versionCode 9）

### 目標
移除小工具控制列底部漸層，讓按鈕區域完全透明，只保留深灰圖示。

### 變更
- `photo_widget.xml`：`widget_controls` 背景改為 `@android:color/transparent`。
- 刪除不再使用的 `widget_controls_scrim.xml`。
- 版本更新為 `versionCode 9`、`versionName 1.1.5`。

### 問題與解法
- 無。

### 待辦／已知限制
- 底部已無漸層與底色，深灰圖示在暗色相片上可能較不清楚。

## 2026-11-08 - v1.1.4（versionCode 8）

### 目標
將小工具控制條底色改為全透明，只保留底部漸層與深灰圖示。

### 變更
- `widget_control_bar.xml`：底色由 `#1AFFFFFF` 改為 `#00000000`。
- 版本更新為 `versionCode 8`、`versionName 1.1.4`。

### 問題與解法
- 無。

### 待辦／已知限制
- 尚未在不同桌布亮度下確認深灰圖示是否仍足夠清楚。

## 2026-11-08 - v1.1.3（versionCode 7）

### 目標
將小工具的三個控制按鈕整合為單一底部控制條，並讓底條更淡、更透明，降低視覺存在感。

### 變更
- `photo_widget.xml`：新增 `widget_control_bar` 容器，三個按鈕改為透明背景，共用同一個圓角底條。
- 新增 `widget_control_bar.xml`：`#1AFFFFFF`（約 10% 白）圓角底條。
- 移除原本的 `widget_control_background.xml`。
- 按鈕高度維持 36dp，圖示維持深灰 `#4D4D4D`。
- 版本更新為 `versionCode 7`、`versionName 1.1.3`。

### 問題與解法
- 若三個按鈕各自保有背景，即使降低透明度仍會看起來像三個獨立按鈕；改由外層容器統一繪製底條，按鈕只保留圖示。

### 待辦／已知限制
- 尚未在不同桌布亮度與 Launcher 下實測底條與深灰圖示的辨識度。

## 2026-11-08 - v1.1.2（versionCode 6）

### 目標
讓小工具控制列保持可見但不搶眼：提高按鈕背景透明度、圖示改為深灰色，並略微降低按鈕高度。

### 變更
- `widget_control_background.xml`：半透明底色由 `#40000000` 改為淺色 `#26FFFFFF`（約 15% 白）。
- `widget_control_tint.xml`：啟用圖示改為深灰 `#4D4D4D`，停用圖示改為半透明深灰 `#804D4D4D`。
- `photo_widget.xml`：按鈕高度由 40dp 降為 36dp。
- 底部漸層 `widget_controls_scrim` 維持不變。
- 版本更新為 `versionCode 6`、`versionName 1.1.2`。

### 問題與解法
- 深灰圖示若搭配原本的深色底，在暗部會不易辨識；改為淺色半透明底以維持對比，同時降低視覺存在感。

### 待辦／已知限制
- 尚未在不同 Launcher、桌布顏色與螢幕密度下實測按鈕可辨識度。

## 2026-11-08 - v1.1.1（versionCode 5）

### 目標
改善相片小工具底部控制列的操作手感：按鈕加大、圖示線條加粗，同時保留底部漸層並讓按鈕維持半透明疊在相片上。

### 變更
- `res/layout/photo_widget.xml`：按鈕高度由 32dp 提高至 40dp，縮小按鈕間距與左右留白，圖示留白由 7dp 改為 6dp；訊息文字增加底部留白避免被控制列遮住。
- `ic_widget_previous.xml`、`ic_widget_next.xml`：改為描邊式箭頭，線寬 2.8，圓角端點。
- `ic_widget_refresh.xml`：保留原圖形並加上同色描邊，提高視覺粗細。
- `widget_control_background.xml`：半透明背景由 `#59000000` 調整為 `#40000000`。
- 保留 `widget_controls_scrim` 底部漸層。
- 版本更新為 `versionCode 5`、`versionName 1.1.1`。

### 問題與解法
- 2x2 小工具寬度有限，改以增加高度、減少間距與留白來擴大點擊面積，避免按鈕過窄。

### 待辦／已知限制
- 尚未在不同 Launcher 與螢幕密度下實測按鈕實際點擊尺寸。

## 2026-11-08 - v1.1.0（versionCode 4）

### 目標
新增主畫面 2x2 相片小工具。使用者可自行調整小工具大小來放大相片，以左右按鈕切換照片，並可手動重新整理；重新整理後回到第一張照片。

### 變更
- 新增 `widget/PhotoWidgetProvider.kt`：以 `AppWidgetProvider` 與 `RemoteViews` 顯示相片，提供上一張、下一張、重新整理與點擊開啟 App。
- 新增 `widget/PhotoWidgetStateStore.kt`：依 `appWidgetId` 保存目前相片索引。
- 新增 `widget/PhotoWidgetLogic.kt`：索引邊界處理與圖片取樣計算。
- 新增 `widget/PhotoWidgetUpdater.kt`：App 回到前景或權限變更時觸發小工具更新。
- 新增 `util/MediaPermissions.kt`：集中相片權限判斷，`MainActivity` 改用共用函式。
- 新增 `res/layout/photo_widget.xml`、`res/xml/photo_widget_info.xml`、小工具圖示與背景。
- 新增 `res/values/strings.xml`，小工具文案改由資源檔提供。
- `AndroidManifest.xml`：註冊小工具 receiver 與 provider metadata。
- 新增 `PhotoWidgetLogicTest.kt`：測試索引邊界與圖片取樣。
- 版本更新為 `versionCode 4`、`versionName 1.1.0`。

### 問題與解法
- 桌面小工具無法使用 Compose 的 `HorizontalPager` 或雙指縮放，改以 RemoteViews 按鈕切換相片；縮放需求改由調整小工具尺寸實現。
- `RemoteViews` 傳遞 Bitmap 受 Binder 大小限制，小工具圖片限制長邊 512px 並使用 RGB_565。
- 小工具無法自行跳出權限對話框，未授權時顯示提示，點擊後開啟 App 授權。
- 小工具以 `goAsync` 搭配 Mutex 序列化更新，避免連續點擊造成索引競爭。

### 待辦／已知限制
- 小工具僅載入最近 500 張相片，超過部分需開啟 App 瀏覽。
- 桌面小工具不支援雙指縮放與手勢滑動，這是 Android AppWidget 平台限制。
- 尚未在不同廠牌 Launcher 與 Android 8、12、13、14 實機驗證尺寸與權限流程。

## 2026-10-31 - v1.0.2-prerelease（versionCode 3）

### 目標
修正直向照片放大後的拖曳範圍，將最大縮放提高至 30 倍，並建置 Pre-release APK 發佈。

### 變更
- `ZoomablePhoto.kt`：依 Coil 解碼後尺寸計算拖曳邊界，最大縮放改為 30 倍。
- `PhotoEditorScreen.kt`：編輯器同步使用 30 倍縮放上限。
- `ZoomTransformTest.kt`：新增直向照片邊界測試並更新最大倍率測試。
- `README.md`、`docs/USER_GUIDE.md`、`docs/BUILD_AND_RELEASE.md`：更新縮放倍率與發行指令。
- 新增 `RELEASE_NOTES.md`，並整理 `CHANGELOG.md`。

### 問題與解法
- EXIF 旋轉照片的 MediaStore 尺寸可能與實際顯示方向不同，導致垂直邊界被低估；改用 Coil 解碼後的圖片比例計算邊界。

### 待辦／已知限制
- 尚未在實體裝置驗證不同廠牌相機的 EXIF 方向資料與 30 倍縮放操作手感。

## 2026-10-31 - v1.0.1（versionCode 2）

### 目標
改善直向照片放大後的瀏覽範圍，並將最大縮放倍率提高至 25 倍。

### 變更
- `ZoomablePhoto.kt`：依 Coil 解碼後尺寸計算拖曳邊界，並將最大縮放統一為 25 倍。
- `PhotoEditorScreen.kt`：編輯器同步使用 25 倍縮放上限。
- `ZoomTransformTest.kt`：新增直向照片邊界測試並更新最大倍率測試。

### 問題與解法
- EXIF 旋轉照片的 MediaStore 尺寸可能與實際顯示方向不同，改用 Coil 解碼後的圖片比例計算邊界。

### 待辦／已知限制
- 尚未在實體裝置驗證不同廠牌相機產生的 EXIF 方向資料。
