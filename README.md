# mini相片瀏覽器

`mini相片瀏覽器` 是一款以 Kotlin 與 Jetpack Compose 開發的 Android 相片瀏覽及編輯應用程式。它能讀取裝置授權的圖片、依相片或資料夾瀏覽、查看圖片資訊，並提供畫筆、螢光筆、橡皮擦、模糊、裁切、旋轉、水平翻轉、分享與另存新檔等功能。

## 主要功能

- 依時間瀏覽裝置相片，或依資料夾分類檢視。
- 單擊切換瀏覽控制列，雙擊放大或還原，雙指縮放與拖曳。
- 照片瀏覽最大放大 20 倍，縮放時以雙指中心為定位點。
- 照片瀏覽控制列隱藏時，同步進入沉浸式全螢幕瀏覽。
- 支援 Android 圖片 `VIEW` 與 `EDIT` Intent，可由系統截圖編輯清單啟動。
- 畫筆、螢光筆、橡皮擦與模糊工具使用一致的固定螢幕粗細。
- 記住各工具粗細，以及畫筆與螢光筆最後使用的顏色。
- 支援復原、重做、裁切、旋轉、水平翻轉、分享及另存 JPEG。
- 支援淺色、深色及跟隨系統外觀。
- 圖庫以分頁方式載入，並監聽 MediaStore 變更自動刷新。
- 依拍攝日期排序，沒有拍攝日期時改用加入媒體庫日期。
- 圖片只在裝置本機處理，不會上傳到遠端服務。

## 系統需求

- Android 8.0（API 26）以上。
- 建置環境：JDK 17、Android SDK 35。
- 建議使用可支援 Android Gradle Plugin 8.8.2 的 Android Studio。

## 快速開始

```powershell
.\gradlew.bat assembleDebug
```

Debug APK 會產生於：

```text
app/build/outputs/apk/debug/app-debug.apk
```

測試發行版本：

```powershell
.\gradlew.bat assemblePrerelease
```

Prerelease APK 會產生於：

```text
app/build/outputs/apk/prerelease/app-prerelease.apk
```

> `prerelease` 會啟用 R8 壓縮，但使用 Android Debug 金鑰簽署，只適合測試，不可作為正式上架版本。

執行測試與品質檢查：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug
```

## 文件

- [使用指南](docs/USER_GUIDE.md)
- [系統架構與技術文件](docs/ARCHITECTURE.md)
- [系統設計文件](docs/SYSTEM_DESIGN.md)
- [建置與發行指南](docs/BUILD_AND_RELEASE.md)
- [變更紀錄](CHANGELOG.md)

## 專案結構

```text
app/src/main/java/com/miniphoto/viewer/
|-- MainActivity.kt
|-- MainViewModel.kt
|-- data/
|   |-- Photo.kt
|   `-- PhotoRepository.kt
`-- ui/
    |-- GalleryApp.kt
    |-- PhotoEditorScreen.kt
    |-- ZoomablePhoto.kt
    `-- theme/Theme.kt
```

## 隱私與權限

應用程式只讀取使用者授權的圖片。Android 13 以上使用 `READ_MEDIA_IMAGES`，Android 14 另支援使用者選取的媒體存取權；較舊版本使用對應的外部儲存權限。Android 8、9 只在儲存或刪除時要求寫入權限。分享暫存檔透過 `FileProvider` 提供，編輯輸出儲存在 `Pictures/MiniPhotoViewer`；含透明度的圖片輸出為 PNG，其餘輸出為 JPEG。

## 授權

本專案採用 [MIT License](LICENSE)。

Copyright (c) 2026 mark216tw
