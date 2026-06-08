# ═══════════════════════════════════════════════════════════════════════════
# build-release.ps1 — بناء AAB نظيف ونسخه لسطح المكتب
# الاستخدام: .\build-release.ps1
# يمكن تشغيله من موجه الأوامر (PowerShell) مباشرةً دون فتح Android Studio
# ═══════════════════════════════════════════════════════════════════════════

param(
    [switch]$SkipClean   # لتخطي مسح الكاش (أسرع لكن أقل موثوقية)
)

$ErrorActionPreference = "Stop"
$projectDir = $PSScriptRoot
$gradlew    = Join-Path $projectDir "gradlew.bat"
$outputAab  = Join-Path $projectDir "app\build\outputs\bundle\release\app-release.aab"

Write-Host ""
Write-Host "══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  SchoolApp Yemen — بناء إصدار جديد" -ForegroundColor Cyan
Write-Host "══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# قراءة رقم الإصدار من build.gradle.kts
$buildFile   = Join-Path $projectDir "app\build.gradle.kts"
$versionCode = (Select-String -Path $buildFile -Pattern 'versionCode\s*=\s*(\d+)').Matches[0].Groups[1].Value
$versionName = (Select-String -Path $buildFile -Pattern 'versionName\s*=\s*"([^"]+)"').Matches[0].Groups[1].Value
Write-Host "📦 الإصدار: versionCode=$versionCode | versionName=$versionName" -ForegroundColor Green
Write-Host ""

# مسح الكاش إن لم يُطلَب تخطيه
if (-not $SkipClean) {
    Write-Host "🧹 مسح الكاش..." -ForegroundColor Yellow
    Remove-Item -Recurse -Force ".gradle\configuration-cache" -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force "app\build\kotlin"             -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force "app\build\snapshot"           -ErrorAction SilentlyContinue
    Write-Host "   ✓ تم" -ForegroundColor Gray
    Write-Host ""
}

# التحقق من keystore.properties
$ksFile = Join-Path $projectDir "keystore.properties"
if (-not (Test-Path $ksFile)) {
    Write-Host "❌ keystore.properties غير موجود!" -ForegroundColor Red
    Write-Host "   أنشئه بالمحتوى التالي:" -ForegroundColor Yellow
    Write-Host "   storeFile=C:/Users/osama/schoolapp.jks" -ForegroundColor Gray
    Write-Host "   storePassword=123456" -ForegroundColor Gray
    Write-Host "   keyAlias=schoolapp" -ForegroundColor Gray
    Write-Host "   keyPassword=123456" -ForegroundColor Gray
    exit 1
}
Write-Host "🔑 keystore.properties موجود ✓" -ForegroundColor Green

# بناء AAB
Write-Host ""
Write-Host "🔨 بدء البناء (bundleRelease)..." -ForegroundColor Cyan
$startTime = Get-Date
& $gradlew bundleRelease
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "❌ فشل البناء! راجع الأخطاء أعلاه." -ForegroundColor Red
    exit 1
}
$duration = [math]::Round(((Get-Date) - $startTime).TotalSeconds)

# نسخ AAB لسطح المكتب
$today   = (Get-Date).ToString("yyyyMMdd")
$dstName = "SchoolApp-v$versionName-$today.aab"
$dstPath = Join-Path ([Environment]::GetFolderPath("Desktop")) $dstName
Copy-Item $outputAab $dstPath

$sizeMB = [math]::Round((Get-Item $dstPath).Length / 1MB, 2)

Write-Host ""
Write-Host "══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  ✅ تم البناء بنجاح!" -ForegroundColor Green
Write-Host "══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  📂 الملف  : $dstPath" -ForegroundColor White
Write-Host "  📦 الحجم  : $sizeMB MB" -ForegroundColor White
Write-Host "  ⏱️ الوقت  : $duration ثانية" -ForegroundColor White
Write-Host "  🏷️ الإصدار: versionCode=$versionCode | versionName=$versionName" -ForegroundColor White
Write-Host ""
Write-Host "الخطوة التالية:" -ForegroundColor Yellow
Write-Host "  play.google.com/console → تطبيقك → الإصدار → Production → رفع AAB" -ForegroundColor Gray
Write-Host ""

# فتح سطح المكتب تلقائياً لرؤية الملف
Invoke-Item ([Environment]::GetFolderPath("Desktop"))
