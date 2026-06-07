# ═══════════════════════════════════════════════════════════════════════════
# test-all.ps1 — سكريبت الاختبار الشامل لمشروع SchoolApp Yemen
# الاستخدام: .\test-all.ps1
# ═══════════════════════════════════════════════════════════════════════════

$ErrorActionPreference = "Continue"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"

# ── إعدادات المشروع ──────────────────────────────────────────────────────────
$PROJ     = "C:\Users\osama\AndroidStudioProjects\SchoolAppyemen"
$WEB_DIR  = "C:\SchoolApp"
$WORKER   = "https://school-teacher-proxy.procorners-shop.workers.dev"
$GH_PAGES = "https://procorners-labs.github.io/school-app-yemen-web"
$ANDROID_REPO = "procorners-labs/school-app-yemen"
$WEB_REPO     = "procorners-labs/school-app-yemen-web"

# ── تتبع النتائج ─────────────────────────────────────────────────────────────
$passed = 0; $failed = 0; $warnings = 0
$startTime = Get-Date

function Pass   { param($msg) Write-Host "  ✅ $msg" -ForegroundColor Green;  $script:passed++ }
function Fail   { param($msg) Write-Host "  ❌ $msg" -ForegroundColor Red;    $script:failed++ }
function Warn   { param($msg) Write-Host "  ⚠️  $msg" -ForegroundColor Yellow; $script:warnings++ }
function Info   { param($msg) Write-Host "  ℹ️  $msg" -ForegroundColor Gray }
function Header { param($msg) Write-Host "`n╔══ $msg ══╗" -ForegroundColor Cyan }

Write-Host ""
Write-Host "╔═══════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   SchoolApp Yemen — سكريبت الاختبار الشامل              ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host "  وقت البدء: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host ""

# ════════════════════════════════════════════════════════════════════════════
Header "1. هيكل ملفات المشروع"
# ════════════════════════════════════════════════════════════════════════════

$requiredFiles = @(
    "app\build.gradle.kts",
    "app\src\main\AndroidManifest.xml",
    "app\src\main\java\com\proconrers\schoolappyemen\MainActivity.kt",
    "app\src\main\java\com\proconrers\schoolappyemen\TeacherActivity.kt",
    "app\src\main\java\com\proconrers\schoolappyemen\StudentActivity.kt",
    "app\src\main\java\com\proconrers\schoolappyemen\SplashActivity.kt",
    "app\src\main\java\com\proconrers\schoolappyemen\BaseWebViewActivity.kt",
    "app\src\main\java\com\proconrers\schoolappyemen\AppConfig.kt",
    "app\src\main\java\com\proconrers\schoolappyemen\WebViewSupport.kt",
    "app\src\main\java\com\proconrers\schoolappyemen\SchoolJsBridge.kt",
    "app\src\main\java\com\proconrers\schoolappyemen\NetworkReloadController.kt",
    "app\src\main\res\values\themes.xml",
    "app\src\main\res\xml\network_security_config.xml",
    "gradle\libs.versions.toml",
    "gradle.properties"
)

foreach ($f in $requiredFiles) {
    $full = Join-Path $PROJ $f
    if (Test-Path $full) { Pass $f } else { Fail "مفقود: $f" }
}

# ════════════════════════════════════════════════════════════════════════════
Header "2. إعدادات البناء"
# ════════════════════════════════════════════════════════════════════════════

$bk = Get-Content "$PROJ\app\build.gradle.kts" -Raw
$lv = Get-Content "$PROJ\gradle\libs.versions.toml" -Raw
$gp = Get-Content "$PROJ\gradle.properties" -Raw

# compileSdk / targetSdk
if ($bk -match "compileSdk\s*=\s*(\d+)") {
    $cSdk = $Matches[1]
    if ([int]$cSdk -ge 35) { Pass "compileSdk = $cSdk" } else { Fail "compileSdk = $cSdk (يجب ≥ 35)" }
}
if ($bk -match "targetSdk\s*=\s*(\d+)") {
    $tSdk = $Matches[1]
    if ([int]$tSdk -ge 35) { Pass "targetSdk = $tSdk" } else { Fail "targetSdk = $tSdk (يجب ≥ 35)" }
}
if ($bk -match "minSdk\s*=\s*(\d+)") {
    $mSdk = $Matches[1]
    if ([int]$mSdk -le 24) { Pass "minSdk = $mSdk" } else { Warn "minSdk = $mSdk (كان 24، تحقق من التوافق)" }
}

# versionCode / versionName
if ($bk -match "versionCode\s*=\s*(\d+)") { Pass "versionCode = $($Matches[1])" }
if ($bk -match 'versionName\s*=\s*"([^"]+)"') { Pass "versionName = $($Matches[1])" }

# multiDex
if ($bk -match "multiDexEnabled\s*=\s*true") { Pass "multiDexEnabled = true" } else { Warn "multiDexEnabled غير مُفعَّل" }

# AGP version
if ($lv -match 'agp\s*=\s*"([^"]+)"') {
    $agpVer = $Matches[1]
    Pass "AGP = $agpVer"
}

# Kotlin version
if ($lv -match 'kotlin\s*=\s*"([^"]+)"') { Pass "Kotlin = $($Matches[1])" }

# Java home
if ($gp -match "org\.gradle\.java\.home=(.+)") {
    $jh = $Matches[1].Trim()
    if (Test-Path $jh.Replace("C\:/","C:\").Replace("/","\")) { Pass "JAVA_HOME valide: $jh" }
    else { Fail "JAVA_HOME غير موجود: $jh" }
}

# إعداد classes٢.dex fix
if ($gp -match "Duser\.language=en") { Pass "Arabic dex fix مُفعَّل (user.language=en)" } else { Fail "Arabic dex fix مفقود" }

# in-process compiler
if ($gp -match "kotlin\.compiler\.execution\.strategy=in-process") { Pass "Kotlin in-process مُفعَّل (بدون daemon)" } else { Warn "Kotlin daemon strategy غير مضبوط" }

# ════════════════════════════════════════════════════════════════════════════
Header "3. AndroidManifest"
# ════════════════════════════════════════════════════════════════════════════

$mf = Get-Content "$PROJ\app\src\main\AndroidManifest.xml" -Raw

if ($mf -match "android\.permission\.INTERNET") { Pass "صلاحية INTERNET موجودة" } else { Fail "صلاحية INTERNET مفقودة" }
if ($mf -match "android\.permission\.ACCESS_NETWORK_STATE") { Pass "صلاحية NETWORK_STATE موجودة" } else { Fail "صلاحية NETWORK_STATE مفقودة" }
if ($mf -match "enableOnBackInvokedCallback") { Pass "enableOnBackInvokedCallback مُفعَّل (Predictive Back)" } else { Warn "enableOnBackInvokedCallback مفقود" }
if ($mf -match "windowSoftInputMode.*adjustResize") { Pass "windowSoftInputMode=adjustResize (لوحة المفاتيح)" } else { Warn "windowSoftInputMode غير مضبوط" }
if ($mf -match "networkSecurityConfig") { Pass "networkSecurityConfig مُحدَّد" } else { Fail "networkSecurityConfig مفقود" }
if ($mf -match "SplashActivity") { Pass "SplashActivity مُعرَّفة" } else { Fail "SplashActivity مفقودة" }
if ($mf -match "TeacherActivity") { Pass "TeacherActivity مُعرَّفة" } else { Fail "TeacherActivity مفقودة" }
if ($mf -match "StudentActivity") { Pass "StudentActivity مُعرَّفة" } else { Fail "StudentActivity مفقودة" }

# ════════════════════════════════════════════════════════════════════════════
Header "4. كود Kotlin — التحقق من الميزات الأساسية"
# ════════════════════════════════════════════════════════════════════════════

$base = Get-Content "$PROJ\app\src\main\java\com\proconrers\schoolappyemen\BaseWebViewActivity.kt" -Raw
$main = Get-Content "$PROJ\app\src\main\java\com\proconrers\schoolappyemen\MainActivity.kt" -Raw

# Edge-to-Edge
if ($base -match "enableEdgeToEdge") { Pass "BaseWebViewActivity: enableEdgeToEdge() مُستدعى" } else { Fail "enableEdgeToEdge() مفقود في BaseWebViewActivity" }
if ($main -match "enableEdgeToEdge") { Pass "MainActivity: enableEdgeToEdge() مُستدعى" } else { Fail "enableEdgeToEdge() مفقود في MainActivity" }

# WindowCompat
if ($base -match "WindowCompat.setDecorFitsSystemWindows") { Pass "WindowCompat.setDecorFitsSystemWindows مُضبوط" } else { Warn "WindowCompat.setDecorFitsSystemWindows مفقود" }

# Insets
if ($base -match "ViewCompat.setOnApplyWindowInsetsListener") { Pass "Window Insets listener مُضاف" } else { Warn "Window Insets listener مفقود" }

# Back dialog
if ($base -match "showLogoutConfirmDialog") { Pass "مربع حوار تأكيد الخروج (Teacher/Student)" } else { Fail "مربع حوار الخروج مفقود" }
if ($main -match "AlertDialog") { Pass "مربع حوار الخروج في MainActivity" } else { Fail "AlertDialog مفقود في MainActivity" }

# lastLoggedInUrl
if ($base -match "lastLoggedInUrl") { Pass "تتبّع lastLoggedInUrl (Swipe-to-Refresh صحيح)" } else { Warn "lastLoggedInUrl غير مُتتبَّع" }

# Network auto-reload
if (Test-Path "$PROJ\app\src\main\java\com\proconrers\schoolappyemen\NetworkReloadController.kt") {
    Pass "NetworkReloadController موجود (إعادة تحميل تلقائية)"
} else { Fail "NetworkReloadController مفقود" }

# JS Bridge
if (Test-Path "$PROJ\app\src\main\java\com\proconrers\schoolappyemen\SchoolJsBridge.kt") {
    $jb = Get-Content "$PROJ\app\src\main\java\com\proconrers\schoolappyemen\SchoolJsBridge.kt" -Raw
    if ($jb -match "saveBase64") { Pass "SchoolJsBridge: saveBase64 (تصدير Excel) موجود" } else { Warn "saveBase64 مفقود في الجسر" }
} else { Fail "SchoolJsBridge مفقود" }

# Splash
$splash = Get-Content "$PROJ\app\src\main\java\com\proconrers\schoolappyemen\SplashActivity.kt" -Raw
if ($splash -match "installSplashScreen") { Pass "SplashActivity: AndroidX SplashScreen API" } else { Fail "installSplashScreen مفقود" }

# WebViewSupport
$wvs = Get-Content "$PROJ\app\src\main\java\com\proconrers\schoolappyemen\WebViewSupport.kt" -Raw
if ($wvs -match "LOAD_CACHE_ELSE_NETWORK") { Pass "WebView: LOAD_CACHE_ELSE_NETWORK (offline support)" } else { Warn "cacheMode غير مضبوط" }
if ($wvs -match "domStorageEnabled\s*=\s*true") { Pass "domStorageEnabled (localStorage + IndexedDB)" } else { Fail "domStorageEnabled مفقود" }
if ($wvs -notmatch "databaseEnabled\s*=\s*true") { Pass "databaseEnabled محذوف (deprecated بشكل صحيح)" } else { Warn "databaseEnabled = true لا يزال موجوداً (deprecated)" }
if ($wvs -match "SchoolAppYemen") { Pass "User-Agent مُخصَّص (WebView detection)" } else { Warn "User-Agent غير مُعرَّف" }

# AppConfig
$ac = Get-Content "$PROJ\app\src\main\java\com\proconrers\schoolappyemen\AppConfig.kt" -Raw
if ($ac -match "workers\.dev") { Pass "AppConfig: Worker URLs (يعمل بدون VPN في اليمن)" } else { Warn "Worker URLs غير موجودة في AppConfig" }

# ════════════════════════════════════════════════════════════════════════════
Header "5. الويب Frontend"
# ════════════════════════════════════════════════════════════════════════════

$webFiles = @(
    "frontend\teacher\index.html",
    "frontend\student\index.html",
    "frontend\assets\gas-bridge.js",
    "frontend\assets\offline-sync.js",
    "frontend\assets\offline-db.js"
)

foreach ($f in $webFiles) {
    $full = Join-Path $WEB_DIR $f
    if (Test-Path $full) { Pass $f } else { Fail "مفقود: $f" }
}

# منصة المعلم
$teacher = Get-Content "$WEB_DIR\frontend\teacher\index.html" -Raw -ErrorAction SilentlyContinue
if ($teacher) {
    if ($teacher -match "teacherSession_v2") { Pass "Teacher: localStorage session (teacherSession_v2) ✓" } else { Fail "Teacher: localStorage session مفقود" }
    if ($teacher -match "function loadSession") { Pass "Teacher: loadSession() موجودة" } else { Fail "Teacher: loadSession() مفقودة" }
    if ($teacher -match "function clearSession") { Pass "Teacher: clearSession() موجودة" } else { Fail "Teacher: clearSession() مفقودة" }
    if ($teacher -match "function toggleSidebar") { Pass "Teacher: toggleSidebar() موجودة (القائمة الجانبية)" } else { Fail "Teacher: toggleSidebar() مفقودة" }
    if ($teacher -match "function showSection") { Pass "Teacher: showSection() موجودة (التنقل)" } else { Fail "Teacher: showSection() مفقودة" }
    if ($teacher -match "function renderGrades") { Pass "Teacher: renderGrades() (الدرجات)" } else { Fail "Teacher: renderGrades() مفقودة" }
    if ($teacher -match "function renderReports") { Pass "Teacher: renderReports() (التقارير)" } else { Fail "Teacher: renderReports() مفقودة" }
    if ($teacher -match "function renderCalendar") { Pass "Teacher: renderCalendar() (التقويم)" } else { Fail "Teacher: renderCalendar() مفقودة" }

    # تحقق عدم وجود نسخ مكررة
    $sessionCount = ([regex]::Matches($teacher, "function saveSession")).Count
    if ($sessionCount -eq 1) { Pass "Teacher: saveSession() غير مكررة ($sessionCount نسخة)" } else { Fail "Teacher: saveSession() مكررة ($sessionCount نسخ) — مشكلة!" }
}

# منصة الطالب
$student = Get-Content "$WEB_DIR\frontend\student\index.html" -Raw -ErrorAction SilentlyContinue
if ($student) {
    if ($student -match "studentSession_v2") { Pass "Student: localStorage session (studentSession_v2) ✓" } else { Fail "Student: localStorage session مفقود" }
    if ($student -match "function saveSession") { Pass "Student: saveSession() موجودة" } else { Fail "Student: saveSession() مفقودة" }
    if ($student -match "function loadSession") { Pass "Student: loadSession() موجودة" } else { Fail "Student: loadSession() مفقودة" }
    if ($student -match "restoredUser") { Pass "Student: استعادة الجلسة في DOMContentLoaded ✓" } else { Fail "Student: استعادة الجلسة مفقودة" }
}

# offline-sync.js
$offline = Get-Content "$WEB_DIR\frontend\assets\offline-sync.js" -Raw -ErrorAction SilentlyContinue
if ($offline) {
    if ($offline -match "teacherSession_v2") { Pass "offline-sync: يكتب teacherSession_v2 في localStorage ✓" } else { Fail "offline-sync: localStorage للمعلم مفقود" }
    if ($offline -match "studentSession_v2") { Pass "offline-sync: يكتب studentSession_v2 في localStorage ✓" } else { Fail "offline-sync: localStorage للطالب مفقود" }
    if ($offline -match "function flush") { Pass "offline-sync: مزامنة الطابور موجودة" } else { Fail "offline-sync: flush() مفقودة" }
}

# ════════════════════════════════════════════════════════════════════════════
Header "6. اتصال الشبكة"
# ════════════════════════════════════════════════════════════════════════════

$testUrls = @(
    @{ url = $WORKER;              name = "Cloudflare Worker (Teacher)" },
    @{ url = "$WORKER/student";    name = "Cloudflare Worker (Student)" }
)

foreach ($t in $testUrls) {
    try {
        $r = Invoke-WebRequest -Uri $t.url -TimeoutSec 10 -UseBasicParsing -ErrorAction Stop
        if ($r.StatusCode -lt 400) { Pass "$($t.name): HTTP $($r.StatusCode)" }
        else { Fail "$($t.name): HTTP $($r.StatusCode)" }
    } catch {
        Fail "$($t.name): لا يمكن الوصول — $($_.Exception.Message.Split("`n")[0])"
    }
}

# اختبار GAS API عبر Worker
try {
    $body = '{"fn":"checkVersion","args":[]}'
    $r = Invoke-RestMethod -Uri "$WORKER/gas/teacher" -Method Post -Body $body -ContentType "application/json" -TimeoutSec 10 -ErrorAction Stop
    Pass "Worker API: استجابة من /gas/teacher"
} catch {
    Warn "Worker API: $($_.Exception.Message.Split("`n")[0])"
}

# ════════════════════════════════════════════════════════════════════════════
Header "7. APK الناتج"
# ════════════════════════════════════════════════════════════════════════════

$apkPath = "$PROJ\app\release\app-release.apk"
if (Test-Path $apkPath) {
    $apk = Get-Item $apkPath
    $sizeMB = [math]::Round($apk.Length / 1MB, 1)
    Pass "app-release.apk موجود ($sizeMB MB)"
    if ($sizeMB -lt 15) { Pass "حجم APK معقول ($sizeMB MB < 15 MB)" } else { Warn "APK كبير ($sizeMB MB) — هل يوجد أصول زائدة؟" }
    $daysOld = [math]::Round(((Get-Date) - $apk.LastWriteTime).TotalDays, 0)
    Info "آخر بناء: $($apk.LastWriteTime.ToString('yyyy-MM-dd HH:mm')) (منذ $daysOld يوم)"
} else {
    Warn "app-release.apk غير موجود — شغّل: .\gradlew.bat assembleRelease"
}

# ════════════════════════════════════════════════════════════════════════════
Header "8. Git — حالة المستودعات"
# ════════════════════════════════════════════════════════════════════════════

# Android repo
Push-Location $PROJ
$branch = git rev-parse --abbrev-ref HEAD 2>&1
$lastCommit = git log --oneline -1 2>&1
$status = git status --porcelain 2>&1
if ($branch -eq "main") { Pass "Android: branch = main ✓" } else { Warn "Android: branch = $branch" }
Pass "Android: آخر commit — $lastCommit"
if (-not $status) { Pass "Android: لا يوجد تغييرات غير مُحدَّثة" } else { Warn "Android: يوجد تغييرات غير مُحدَّثة:`n$(($status | Select-Object -First 5) -join "`n")" }
Pop-Location

# Web repo
Push-Location $WEB_DIR
$wBranch = git rev-parse --abbrev-ref HEAD 2>&1
$wCommit = git log --oneline -1 2>&1
$wStatus = git status --porcelain 2>&1
if ($wBranch -eq "main") { Pass "Web: branch = main ✓" } else { Warn "Web: branch = $wBranch" }
Pass "Web: آخر commit — $wCommit"
if (-not $wStatus) { Pass "Web: لا يوجد تغييرات غير مُحدَّثة" } else { Warn "Web: يوجد تغييرات غير مُحدَّثة" }
Pop-Location

# ════════════════════════════════════════════════════════════════════════════
Header "9. بناء سريع (Kotlin compile فقط)"
# ════════════════════════════════════════════════════════════════════════════

Push-Location $PROJ
Write-Host "  ⏳ تجميع Kotlin..." -ForegroundColor Gray
$buildOut = .\gradlew.bat compileDebugKotlin 2>&1
$buildStr = ($buildOut | Out-String)
if ($buildStr -match "BUILD SUCCESSFUL") {
    Pass "Kotlin compile: ناجح ✓"
    if ($buildStr -match "UP-TO-DATE|from cache") { Info "استُخدم الكاش (أسرع)" }
} elseif ($buildStr -match "BUILD FAILED") {
    $errLines = $buildOut | Where-Object { $_ -match "error:|Exception|Could not" } | Select-Object -First 3
    Fail "Kotlin compile فشل: $($errLines -join ' | ')"
} else {
    Warn "لم يُكتشف ناتج البناء — شغّل يدوياً: .\gradlew.bat compileDebugKotlin"
}
Pop-Location

# ════════════════════════════════════════════════════════════════════════════
Header "10. themes.xml و network_security_config.xml"
# ════════════════════════════════════════════════════════════════════════════

$themes = Get-Content "$PROJ\app\src\main\res\values\themes.xml" -Raw
if ($themes -match "Theme.SchoolApp.Splash") { Pass "Theme.SchoolApp.Splash (SplashScreen API)" } else { Fail "Theme.SchoolApp.Splash مفقود" }
if ($themes -match "windowOptOutEdgeToEdgeEnforcement") { Pass "windowOptOutEdgeToEdgeEnforcement مُضبوط" } else { Warn "windowOptOutEdgeToEdgeEnforcement غير موجود" }

$nsc = Get-Content "$PROJ\app\src\main\res\xml\network_security_config.xml" -Raw -ErrorAction SilentlyContinue
if ($nsc) {
    # workers.dev مؤمَّن عبر <certificates src="system"/> — لا يحتاج إدخالاً صريحاً
    if ($nsc -match 'certificates src="system"') { Pass "network_security_config: System CA يُغطي workers.dev تلقائياً ✓" } else { Warn "network_security_config: لا System CA — تحقق من workers.dev" }
    if ($nsc -match "google\.com") { Pass "network_security_config: نطاقات Google مُصرَّح بها" } else { Warn "نطاقات Google غير مُصرَّح بها في network_security_config" }
}

# ════════════════════════════════════════════════════════════════════════════
# ملخص النتائج
# ════════════════════════════════════════════════════════════════════════════

$elapsed = [math]::Round(((Get-Date) - $startTime).TotalSeconds, 1)
$total = $passed + $failed + $warnings

Write-Host ""
Write-Host "╔═══════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║                    ملخص النتائج                          ║" -ForegroundColor Cyan
Write-Host "╠═══════════════════════════════════════════════════════════╣" -ForegroundColor Cyan
Write-Host ("║  ✅ ناجح   : {0,-5} ║  ❌ فاشل   : {1,-5} ║  ⚠️  تحذير : {2,-5} ║" -f $passed, $failed, $warnings) -ForegroundColor Cyan
Write-Host "╠═══════════════════════════════════════════════════════════╣" -ForegroundColor Cyan

if ($failed -eq 0 -and $warnings -eq 0) {
    Write-Host "║  🏆 المشروع سليم 100% — جاهز للنشر                      ║" -ForegroundColor Green
} elseif ($failed -eq 0) {
    Write-Host "║  ✅ لا أخطاء — $warnings تحذير للمراجعة                     ║" -ForegroundColor Yellow
} else {
    Write-Host "║  🔴 يوجد $failed خطأ يجب إصلاحها قبل النشر               ║" -ForegroundColor Red
}

Write-Host ("║  ⏱  الوقت المستغرق: {0,-5}ث  |  الفحوصات: {1}             ║" -f $elapsed, $total) -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""
