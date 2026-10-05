# 建置與發行指南

## 環境需求

- JDK 17
- Android SDK Platform 35
- 可執行 Gradle Wrapper 的 Windows、macOS 或 Linux 環境

檢查環境：

```powershell
java -version
.\gradlew.bat --version
```

## Build Types

### debug

供日常開發，使用 Debug 金鑰，未啟用 R8 壓縮。

```powershell
.\gradlew.bat assembleDebug
```

### prerelease

供測試發行，版本名稱為 `1.0.0-prerelease`，啟用 R8 程式碼壓縮與資源縮減，並使用 Debug 金鑰簽署。

```powershell
.\gradlew.bat clean assemblePrerelease
```

輸出：

```text
app/build/outputs/apk/prerelease/app-prerelease.apk
```

檢查簽章：

```powershell
apksigner verify --verbose --print-certs app/build/outputs/apk/prerelease/app-prerelease.apk
```

> Debug 金鑰不是正式發行金鑰，不得用於 Google Play 正式上架。

## 品質檢查

```powershell
.\gradlew.bat lintDebug lintPrerelease
```

建議發行前至少驗證：

1. Android 8、10、13、14 或以上版本的圖片權限流程。
2. 相片與資料夾載入。
3. 雙擊縮放、手勢縮放與圖片切換。
4. 所有編輯工具、復原、重做、旋轉、翻轉及裁切。
5. 一般儲存、分享、刪除與外部 `EDIT` Intent。
6. 淺色及深色模式。

## GitHub Pre-release

建議標籤格式：

```text
v1.0.0-prerelease
```

使用 GitHub CLI：

```powershell
gh release create v1.0.0-prerelease app/build/outputs/apk/prerelease/app-prerelease.apk --prerelease --title "1.0.0-prerelease" --notes "Pre-release 版本"
```

## 正式發行前必要調整

- 建立並安全保存正式 signing key。
- 新增正式 Release Build Type 或 signingConfig。
- 更新 `versionCode` 與 `versionName`。
- 完成自動化測試、實機回歸及隱私政策檢查。
- 不得沿用 prerelease 的 Debug 簽章作為正式簽章。
