# 貢獻指南

感謝協助改善 `mini相片瀏覽器`。

## 開發流程

1. Fork repository 並從主要分支建立功能分支。
2. 使用 JDK 17 與 Android SDK 35 建置專案。
3. 維持既有 Kotlin、Jetpack Compose 與 Material 3 寫法。
4. 修改完成後執行：

```powershell
.\gradlew.bat assembleDebug lintDebug
```

5. Pull Request 應清楚說明行為變更、驗證方式及畫面變更。

## 問題回報

請提供 Android 版本、裝置型號、重現步驟、預期結果與實際結果。若問題涉及圖片，請先移除私人資訊。

## 授權

提交貢獻即表示同意以本專案的 MIT License 發布該貢獻。
