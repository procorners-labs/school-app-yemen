<#
.SYNOPSIS
    SchoolApp Yemen - Build, Test & Generate AAB
    Al-Ibda' International Schools
.USAGE
    .\build-and-test.ps1
#>

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding          = [System.Text.Encoding]::UTF8
$ErrorActionPreference   = "Continue"

# ── Paths & Config ───────────────────────────────────────────────────────────
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"
$PROJ          = "C:\Users\osama\AndroidStudioProjects\SchoolAppyemen"
$WEB_DIR       = "C:\SchoolApp"
$WORKER_URL    = "https://school-teacher-proxy.procorners-shop.workers.dev"
$PLAY_CONSOLE  = "https://play.google.com/console"

Set-Location $PROJ

# ── Result counters ──────────────────────────────────────────────────────────
$script:ok   = 0
$script:fail = 0
$script:warn = 0

function Pass($m)   { Write-Host "  [OK]  $m" -ForegroundColor Green;  $script:ok++ }
function Fail($m)   { Write-Host "  [!!]  $m" -ForegroundColor Red;    $script:fail++ }
function Warn($m)   { Write-Host "  [>>]  $m" -ForegroundColor Yellow; $script:warn++ }
function Info($m)   { Write-Host "  [--]  $m" -ForegroundColor Gray }
function Title($m)  { Write-Host "`n=== $m ===" -ForegroundColor Cyan }
function Banner($m) { Write-Host $m -ForegroundColor Cyan }

$ts = Get-Date
Banner ""
Banner "======================================================"
Banner "   SchoolApp Yemen  |  Build + Test + AAB Generator  "
Banner "======================================================"
Banner "   Start: $($ts.ToString('yyyy-MM-dd HH:mm:ss'))      "
Banner ""

# ════════════════════════════════════════════════════════════════════════════
Title "1. Project Files"
# ════════════════════════════════════════════════════════════════════════════
$files = @(
    "app\build.gradle.kts",
    "app\src\main\AndroidManifest.xml",
    "gradle\libs.versions.toml",
    "gradle.properties",
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
    "app\src\main\res\xml\network_security_config.xml"
)
foreach ($f in $files) {
    if (Test-Path (Join-Path $PROJ $f)) { Pass $f } else { Fail "MISSING: $f" }
}

# ════════════════════════════════════════════════════════════════════════════
Title "2. Build Configuration"
# ════════════════════════════════════════════════════════════════════════════
$bk = Get-Content "$PROJ\app\build.gradle.kts" -Raw
$lv = Get-Content "$PROJ\gradle\libs.versions.toml" -Raw
$gp = Get-Content "$PROJ\gradle.properties" -Raw

# compileSdk
if ($bk -match "compileSdk\s*=\s*(\d+)") {
    $cs = [int]$Matches[1]
    if ($cs -ge 35) { Pass "compileSdk = $cs" } else { Fail "compileSdk = $cs (needs >= 35)" }
}
# targetSdk
if ($bk -match "targetSdk\s*=\s*(\d+)") {
    $ts2 = [int]$Matches[1]
    if ($ts2 -ge 35) { Pass "targetSdk = $ts2" } else { Fail "targetSdk = $ts2 (needs >= 35)" }
}
# minSdk
if ($bk -match "minSdk\s*=\s*(\d+)") {
    $ms = [int]$Matches[1]
    if ($ms -eq 24) { Pass "minSdk = $ms" } else { Warn "minSdk = $ms (expected 24)" }
}
# versionCode
if ($bk -match "versionCode\s*=\s*(\d+)") { Pass "versionCode = $($Matches[1])" }
# versionName
if ($bk -match 'versionName\s*=\s*"([\d\.]+)"') { Pass "versionName = $($Matches[1])" }
# multiDex
if ($bk -match "multiDexEnabled\s*=\s*true") { Pass "multiDexEnabled = true" } else { Warn "multiDexEnabled missing" }
# AGP
if ($lv -match 'agp\s*=\s*"([\d\.]+)"') { Pass "AGP = $($Matches[1])" }
# Kotlin
if ($lv -match 'kotlin\s*=\s*"([\d\.]+)"') { Pass "Kotlin = $($Matches[1])" }
# Java home
if ($gp -match "org\.gradle\.java\.home=(.+)") {
    $jh = $Matches[1].Trim().Replace("C\:/","C:\").Replace("/","\")
    if (Test-Path $jh) { Pass "JAVA_HOME valid: $jh" } else { Fail "JAVA_HOME not found: $jh" }
}
# Arabic dex fix
if ($gp -match "Duser\.language=en") { Pass "Arabic dex fix active (user.language=en)" } else { Fail "Arabic dex fix MISSING" }
# In-process compiler
if ($gp -match "kotlin\.compiler\.execution\.strategy=in-process") { Pass "Kotlin in-process (no daemon)" } else { Warn "Kotlin strategy not set" }
# suppressUnsupportedCompileSdk
if ($gp -match "suppressUnsupportedCompileSdk") { Pass "suppressUnsupportedCompileSdk set" } else { Warn "suppressUnsupportedCompileSdk missing" }

# ════════════════════════════════════════════════════════════════════════════
Title "3. AndroidManifest.xml"
# ════════════════════════════════════════════════════════════════════════════
$mf = Get-Content "$PROJ\app\src\main\AndroidManifest.xml" -Raw
@{
    "INTERNET permission"             = "android\.permission\.INTERNET"
    "ACCESS_NETWORK_STATE permission" = "android\.permission\.ACCESS_NETWORK_STATE"
    "enableOnBackInvokedCallback"     = "enableOnBackInvokedCallback"
    "windowSoftInputMode adjustResize"= "windowSoftInputMode.*adjustResize"
    "networkSecurityConfig"           = "networkSecurityConfig"
    "SplashActivity defined"          = "SplashActivity"
    "TeacherActivity defined"         = "TeacherActivity"
    "StudentActivity defined"         = "StudentActivity"
}.GetEnumerator() | Sort-Object Key | ForEach-Object {
    if ($mf -match $_.Value) { Pass $_.Key } else { Fail "MISSING: $($_.Key)" }
}

# ════════════════════════════════════════════════════════════════════════════
Title "4. Kotlin Code Features"
# ════════════════════════════════════════════════════════════════════════════
$base   = Get-Content "$PROJ\app\src\main\java\com\proconrers\schoolappyemen\BaseWebViewActivity.kt" -Raw
$mainKt = Get-Content "$PROJ\app\src\main\java\com\proconrers\schoolappyemen\MainActivity.kt" -Raw
$wvs    = Get-Content "$PROJ\app\src\main\java\com\proconrers\schoolappyemen\WebViewSupport.kt" -Raw
$splash = Get-Content "$PROJ\app\src\main\java\com\proconrers\schoolappyemen\SplashActivity.kt" -Raw
$ac     = Get-Content "$PROJ\app\src\main\java\com\proconrers\schoolappyemen\AppConfig.kt" -Raw
$jb     = Get-Content "$PROJ\app\src\main\java\com\proconrers\schoolappyemen\SchoolJsBridge.kt" -Raw

# Edge-to-Edge
if ($base   -match "enableEdgeToEdge") { Pass "BaseWebViewActivity: enableEdgeToEdge()" } else { Fail "enableEdgeToEdge missing in Base" }
if ($mainKt -match "enableEdgeToEdge") { Pass "MainActivity: enableEdgeToEdge()" }        else { Fail "enableEdgeToEdge missing in Main" }
if ($base   -match "WindowCompat\.setDecorFitsSystemWindows") { Pass "WindowCompat.setDecorFitsSystemWindows" } else { Warn "WindowCompat missing" }
if ($base   -match "setOnApplyWindowInsetsListener") { Pass "WindowInsets listener set" } else { Warn "WindowInsets listener missing" }

# Back press
if ($base   -match "showLogoutConfirmDialog") { Pass "Logout confirm dialog (Teacher/Student)" } else { Fail "Logout dialog missing" }
if ($mainKt -match "AlertDialog") { Pass "Exit confirm dialog (MainActivity)" }             else { Fail "Exit dialog missing in Main" }

# Swipe-to-refresh
if ($base -match "lastLoggedInUrl") { Pass "lastLoggedInUrl tracking (Swipe-to-Refresh)" } else { Warn "lastLoggedInUrl missing" }

# WebView settings
if ($wvs -match "LOAD_CACHE_ELSE_NETWORK") { Pass "WebView: LOAD_CACHE_ELSE_NETWORK (offline)" }    else { Warn "cacheMode not set" }
if ($wvs -match "domStorageEnabled\s*=\s*true") { Pass "domStorageEnabled (localStorage+IndexedDB)" } else { Fail "domStorageEnabled missing" }
if ($wvs -notmatch "databaseEnabled\s*=\s*true") { Pass "databaseEnabled removed (deprecated OK)" }  else { Warn "databaseEnabled=true still present" }
if ($wvs -match "SchoolAppYemen") { Pass "Custom User-Agent set" } else { Warn "User-Agent not customised" }

# Other features
if ($splash -match "installSplashScreen") { Pass "SplashActivity: AndroidX SplashScreen API" } else { Fail "installSplashScreen missing" }
if ($ac     -match "workers\.dev")        { Pass "AppConfig: Cloudflare Worker URLs (no-VPN)" } else { Warn "Worker URLs missing in AppConfig" }
if ($jb     -match "saveBase64")          { Pass "SchoolJsBridge: saveBase64 (Excel export)" }  else { Warn "saveBase64 missing in Bridge" }

# NetworkReloadController
if (Test-Path "$PROJ\app\src\main\java\com\proconrers\schoolappyemen\NetworkReloadController.kt") {
    Pass "NetworkReloadController present (auto-reload on reconnect)"
} else { Fail "NetworkReloadController missing" }

# ════════════════════════════════════════════════════════════════════════════
Title "5. Web Frontend"
# ════════════════════════════════════════════════════════════════════════════
@(
    "frontend\teacher\index.html",
    "frontend\student\index.html",
    "frontend\assets\gas-bridge.js",
    "frontend\assets\offline-sync.js",
    "frontend\assets\offline-db.js"
) | ForEach-Object {
    if (Test-Path "$WEB_DIR\$_") { Pass $_ } else { Fail "MISSING: $_" }
}

$teacher = Get-Content "$WEB_DIR\frontend\teacher\index.html" -Raw -ErrorAction SilentlyContinue
if ($teacher) {
    if ($teacher -match "teacherSession_v2")       { Pass "Teacher: localStorage session (teacherSession_v2)" } else { Fail "Teacher: localStorage session missing" }
    if ($teacher -match "function loadSession")    { Pass "Teacher: loadSession() present" }   else { Fail "Teacher: loadSession() missing" }
    if ($teacher -match "function clearSession")   { Pass "Teacher: clearSession() present" }  else { Fail "Teacher: clearSession() missing" }
    if ($teacher -match "function toggleSidebar")  { Pass "Teacher: toggleSidebar() (sidebar)" } else { Fail "Teacher: toggleSidebar() missing" }
    if ($teacher -match "function showSection")    { Pass "Teacher: showSection() (navigation)" } else { Fail "Teacher: showSection() missing" }
    if ($teacher -match "function renderGrades")   { Pass "Teacher: renderGrades()" }          else { Fail "Teacher: renderGrades() missing" }
    if ($teacher -match "function renderReports")  { Pass "Teacher: renderReports()" }         else { Fail "Teacher: renderReports() missing" }
    if ($teacher -match "function renderCalendar") { Pass "Teacher: renderCalendar()" }        else { Fail "Teacher: renderCalendar() missing" }
    # Check no duplicate saveSession
    $cnt = ([regex]::Matches($teacher, "function saveSession")).Count
    if ($cnt -eq 1) { Pass "Teacher: saveSession() not duplicated ($cnt)" } else { Fail "Teacher: saveSession() duplicated $cnt times!" }
}

$student = Get-Content "$WEB_DIR\frontend\student\index.html" -Raw -ErrorAction SilentlyContinue
if ($student) {
    if ($student -match "studentSession_v2")    { Pass "Student: localStorage session (studentSession_v2)" } else { Fail "Student: localStorage session missing" }
    if ($student -match "function saveSession") { Pass "Student: saveSession() present" }  else { Fail "Student: saveSession() missing" }
    if ($student -match "function loadSession") { Pass "Student: loadSession() present" }  else { Fail "Student: loadSession() missing" }
    if ($student -match "restoredUser")         { Pass "Student: auto-restore on reload" } else { Fail "Student: auto-restore missing" }
}

$offline = Get-Content "$WEB_DIR\frontend\assets\offline-sync.js" -Raw -ErrorAction SilentlyContinue
if ($offline) {
    if ($offline -match "teacherSession_v2") { Pass "offline-sync: writes teacherSession_v2 to localStorage" } else { Fail "offline-sync: teacher localStorage missing" }
    if ($offline -match "studentSession_v2") { Pass "offline-sync: writes studentSession_v2 to localStorage" } else { Fail "offline-sync: student localStorage missing" }
    if ($offline -match "function flush")    { Pass "offline-sync: flush() (outbox sync)" }                    else { Fail "offline-sync: flush() missing" }
}

# ════════════════════════════════════════════════════════════════════════════
Title "6. Network Connectivity"
# ════════════════════════════════════════════════════════════════════════════
@(
    @{ url = $WORKER_URL;           name = "Worker (Teacher portal)" },
    @{ url = "$WORKER_URL/student"; name = "Worker (Student portal)" }
) | ForEach-Object {
    try {
        $r = Invoke-WebRequest -Uri $_.url -TimeoutSec 10 -UseBasicParsing -ErrorAction Stop
        if ($r.StatusCode -lt 400) { Pass "$($_.name): HTTP $($r.StatusCode)" }
        else { Fail "$($_.name): HTTP $($r.StatusCode)" }
    } catch { Fail "$($_.name): unreachable - $($_.Exception.Message.Split("`n")[0])" }
}

# ════════════════════════════════════════════════════════════════════════════
Title "7. Themes & Security Config"
# ════════════════════════════════════════════════════════════════════════════
$themes = Get-Content "$PROJ\app\src\main\res\values\themes.xml" -Raw
if ($themes -match "Theme.SchoolApp.Splash")              { Pass "Theme.SchoolApp.Splash (SplashScreen API)" } else { Fail "Splash theme missing" }
if ($themes -match "windowOptOutEdgeToEdgeEnforcement")   { Pass "windowOptOutEdgeToEdgeEnforcement set" }      else { Warn "windowOptOutEdgeToEdgeEnforcement missing" }

$nsc = Get-Content "$PROJ\app\src\main\res\xml\network_security_config.xml" -Raw -ErrorAction SilentlyContinue
if ($nsc) {
    if ($nsc -match 'certificates src="system"') { Pass "network_security: System CA covers workers.dev" } else { Warn "No System CA in network_security_config" }
    if ($nsc -match "google\.com")               { Pass "network_security: Google domains allowed" }        else { Warn "Google domains not explicitly listed" }
}

# ════════════════════════════════════════════════════════════════════════════
Title "8. Git Status"
# ════════════════════════════════════════════════════════════════════════════
$branch  = git rev-parse --abbrev-ref HEAD 2>&1
$commit  = git log --oneline -1 2>&1
$dirty   = git status --porcelain 2>&1 | Where-Object { $_ -notmatch "^.M .idea" }

if ($branch -eq "main") { Pass "Android repo: branch = main" } else { Warn "Android repo: branch = $branch" }
Pass "Last commit: $commit"
if (-not $dirty) { Pass "Working tree clean (ignoring .idea)" } else { Warn "Uncommitted changes: $($dirty.Count) files" }

Push-Location $WEB_DIR
$wBranch = git rev-parse --abbrev-ref HEAD 2>&1
$wCommit = git log --oneline -1 2>&1
$wDirty  = git status --porcelain 2>&1
if ($wBranch -eq "main") { Pass "Web repo: branch = main" } else { Warn "Web repo: branch = $wBranch" }
Pass "Web last commit: $wCommit"
if (-not $wDirty) { Pass "Web working tree clean" } else { Warn "Web: uncommitted changes" }
Pop-Location

# ════════════════════════════════════════════════════════════════════════════
Title "9. APK Status"
# ════════════════════════════════════════════════════════════════════════════
$apk = Get-Item "$PROJ\app\release\app-release.apk" -ErrorAction SilentlyContinue
if ($apk) {
    $sz   = [math]::Round($apk.Length/1MB, 1)
    $days = [math]::Round(((Get-Date) - $apk.LastWriteTime).TotalDays, 0)
    Pass "app-release.apk: $sz MB"
    if ($sz -lt 15) { Pass "APK size OK ($sz MB < 15 MB)" } else { Warn "APK large: $sz MB" }
    Info "Built: $($apk.LastWriteTime.ToString('yyyy-MM-dd HH:mm')) ($days days ago)"
} else { Warn "app-release.apk not found - will be built below" }

# ════════════════════════════════════════════════════════════════════════════
Title "10. Kotlin Compile Check"
# ════════════════════════════════════════════════════════════════════════════
Write-Host "  Compiling Kotlin (debug)..." -ForegroundColor Gray
$out = .\gradlew.bat compileDebugKotlin 2>&1 | Out-String
if ($out -match "BUILD SUCCESSFUL") {
    Pass "Kotlin compile: SUCCESSFUL"
    if ($out -match "UP-TO-DATE|from cache") { Info "Cache used (fast)" }
} elseif ($out -match "BUILD FAILED") {
    $errMsg = ($out -split "`n" | Where-Object { $_ -match "error:" } | Select-Object -First 3) -join " | "
    Fail "Kotlin compile FAILED: $errMsg"
} else {
    Warn "Build result unclear - run manually: .\gradlew.bat compileDebugKotlin"
}

# ════════════════════════════════════════════════════════════════════════════
# PRINT SUMMARY BEFORE BUILD
# ════════════════════════════════════════════════════════════════════════════
$elapsed = [math]::Round(((Get-Date) - (Get-Date $ts)).TotalSeconds, 1)
Banner ""
Banner "======================================================"
Banner ("  Tests: {0} OK  |  {1} FAIL  |  {2} WARN  |  {3}s" -f $script:ok, $script:fail, $script:warn, $elapsed)
Banner "======================================================"
Banner ""

if ($script:fail -gt 0) {
    Banner "  [!!] Fix the $($script:fail) failure(s) above before building AAB."
    Banner "  Aborting AAB build to avoid shipping broken code."
    Banner ""
    exit 1
}

# ════════════════════════════════════════════════════════════════════════════
Title "11. Building Release AAB for Google Play"
# ════════════════════════════════════════════════════════════════════════════
Banner ""
Banner "  Building signed release bundle (AAB)..."
Banner "  This replaces assembleRelease - AAB is required by Google Play."
Banner ""

$buildStart = Get-Date
$buildOut   = .\gradlew.bat bundleRelease 2>&1
$buildStr   = $buildOut | Out-String
$buildSec   = [math]::Round(((Get-Date) - $buildStart).TotalSeconds, 0)

if ($buildStr -match "BUILD SUCCESSFUL") {
    Banner ""
    Banner "  ======================================================"
    Banner "  [OK]  AAB BUILD SUCCESSFUL in ${buildSec}s"
    Banner "  ======================================================"
    Banner ""

    # Find AAB
    $aab = Get-Item "$PROJ\app\build\outputs\bundle\release\app-release.aab" -ErrorAction SilentlyContinue
    if ($aab) {
        $aabMB = [math]::Round($aab.Length/1MB, 1)
        Banner "  AAB file  : $($aab.FullName)"
        Banner "  Size      : $aabMB MB"
        Banner "  Built at  : $($aab.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss'))"
        Banner ""
        Banner "  ======================================================"
        Banner "  NEXT STEP: Upload to Google Play"
        Banner "  ======================================================"
        Banner ""
        Banner "  1. Open Play Console:"
        Banner "     $PLAY_CONSOLE"
        Banner ""
        Banner "  2. Go to: Release -> Production (or Testing track)"
        Banner ""
        Banner "  3. Click: Create new release -> Add app bundle"
        Banner ""
        Banner "  4. Upload this file:"
        Banner "     $($aab.FullName)"
        Banner ""
        Banner "  5. Fill in release notes -> Review -> Rollout"
        Banner ""

        # Also copy AAB to Desktop for easy access
        $desktop = [Environment]::GetFolderPath("Desktop")
        $dest    = "$desktop\app-release-$(Get-Date -Format 'yyyyMMdd').aab"
        Copy-Item $aab.FullName $dest -Force
        Banner "  [Copied to Desktop]: $dest"
        Banner ""

        # Open Play Console automatically
        try {
            Start-Process $PLAY_CONSOLE
            Banner "  [Opened]: Google Play Console in browser"
        } catch { }
    } else {
        Warn "AAB file not found after build - check build output above"
    }

    # Commit version bump if needed
    $uncommitted = git status --porcelain 2>&1 | Where-Object { $_ -match "build\.gradle" }
    if ($uncommitted) {
        Banner ""
        Banner "  Committing build.gradle changes..."
        git add app/build.gradle.kts gradle.properties
        git commit -m "chore: release build $(Get-Date -Format 'yyyy-MM-dd')" 2>&1 | Out-Null
        git push origin main 2>&1 | Out-Null
        Banner "  [Pushed] changes to GitHub"
    }

} else {
    Banner ""
    Banner "  [!!]  AAB BUILD FAILED"
    Banner ""
    $errLines = $buildOut | Where-Object { $_ -match "error:|FAILED|Exception" } | Select-Object -First 10
    $errLines | ForEach-Object { Banner "  $_" }
    Banner ""
    Banner "  Run manually for full output:"
    Banner "  .\gradlew.bat bundleRelease --stacktrace"
    exit 1
}

# ════════════════════════════════════════════════════════════════════════════
# FINAL SUMMARY
# ════════════════════════════════════════════════════════════════════════════
$totalSec = [math]::Round(((Get-Date) - (Get-Date $ts)).TotalSeconds, 0)
Banner ""
Banner "======================================================"
Banner "  FINAL RESULT"
Banner "======================================================"
Banner ("  Tests    : {0} OK  |  {1} FAIL  |  {2} WARN" -f $script:ok, $script:fail, $script:warn)
Banner "  AAB      : Ready for Google Play"
Banner "  Total    : ${totalSec}s"
Banner "======================================================"
Banner ""
