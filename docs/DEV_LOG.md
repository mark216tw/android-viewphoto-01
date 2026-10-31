# 開發紀錄

最新紀錄放最上面。每次開發工作都要更新，不要事後補寫。

---

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
