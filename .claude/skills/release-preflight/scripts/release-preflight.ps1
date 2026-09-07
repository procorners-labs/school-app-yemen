<#
  release-preflight — بوّابةُ ما قبل الرفع: «تُرفع أم لا تُرفع؟»

  🔴 قراءةٌ محضة: لا تبني، لا ترفع، لا ترقّي، ولا تلمس ملفَّ مشروع.
  ⚠️ ولا تُصلح شيئاً — تقول ما هو معطوبٌ وتتوقّف. القرارُ للمالك.

  رموزُ الخروج: 0 = يُرفع · 2 = لا يُرفع (نتيجةٌ حمراء) · 3 = تعذّر القياس.
#>
[CmdletBinding()]
param(
    [string] $Path = (Get-Location).Path,
    # تخطّي فحص Play (شبكة) — للتشغيل بلا اتّصال. 🔴 والنتيجةُ حينها **ناقصة** لا خضراء.
    [switch] $Offline,
    [switch] $Json
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding           = [System.Text.Encoding]::UTF8
$ErrorActionPreference    = "Stop"

$checks = @()   # كلُّ فحصٍ: name · state (pass/fail/unmeasured) · detail
function Add-Check($name, $state, $detail) {
    $script:checks += [pscustomobject]@{ name = $name; state = $state; detail = $detail }
}

# ── جذرُ المشروع: لا مسارَ مثبَّت ──────────────────────────────────────────
function Find-Root([string]$start) {
    $d = Resolve-Path -LiteralPath $start -ErrorAction SilentlyContinue
    if (-not $d) { return $null }
    $cur = Get-Item -LiteralPath $d
    for ($i = 0; $i -lt 40 -and $cur; $i++) {
        if (Test-Path (Join-Path $cur.FullName "gradlew")) { return $cur.FullName }
        $cur = $cur.Parent
    }
    return $null
}
$PROJ = Find-Root $Path
if (-not $PROJ) { Write-Host "[X] لا مشروع أندرويد (لا gradlew في أيّ أصل): $Path" -ForegroundColor Red; exit 3 }

# ── الهوية: تُقرأ وقتَ التشغيل، ولا نسخةَ منها هنا تتقادم ─────────────────
$gradleFile = Join-Path $PROJ "app\build.gradle.kts"
$g = if (Test-Path $gradleFile) { Get-Content $gradleFile -Raw } else { "" }
$appId = $null; $vCode = $null; $vName = $null
if ($g -match 'applicationId\s*=\s*"([^"]+)"') { $appId = $Matches[1] }
if ($g -match 'versionCode\s*=\s*(\d+)')       { $vCode = [int]$Matches[1] }
if ($g -match 'versionName\s*=\s*"([^"]+)"')   { $vName = $Matches[1] }

$AAB = Join-Path $PROJ "app\build\outputs\bundle\release\app-release.aab"
if (-not (Test-Path $AAB)) {
    Write-Host "[X] لا حزمةَ إصدارٍ في مجلد البناء." -ForegroundColor Red
    Write-Host "    ابنِ أوّلاً:  .\gradlew :app:bundleRelease" -ForegroundColor Yellow
    exit 3
}

# ── فكُّ الحزمة إلى مجلدٍ مؤقّت (يُنظَّف دائماً) ────────────────────────────
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("preflight_" + [guid]::NewGuid().ToString("N").Substring(0,8))
try {
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::ExtractToDirectory($AAB, $tmp)
$bundleManifest = Join-Path $tmp "base\manifest\AndroidManifest.xml"
$bundleDex      = Join-Path $tmp "base\dex\classes.dex"
function Read-Bytes-Text($p) {
    if (-not (Test-Path $p)) { return $null }
    # يُقرأ بايتاتٍ ثمّ يُفسَّر Latin-1: المانيفستُ بروتوبَف والـdex ثنائيّ،
    # والمطلوبُ حضورُ سلاسلَ لا تحليلٌ بنيويّ.
    return [System.Text.Encoding]::GetEncoding(28591).GetString([System.IO.File]::ReadAllBytes($p))
}
$mfText  = Read-Bytes-Text $bundleManifest
$dexText = Read-Bytes-Text $bundleDex

# ═════════════════════════════════════════════════════════════════════════
# ① 🔴 الختم: هل هذه الحزمةُ هي التي تدّعيها الشجرة؟
# ═════════════════════════════════════════════════════════════════════════
# **الفحصُ الذي كان ناقصاً 2026-09-07.** حزمةٌ بُنيت قبل رفعِ الرقم بعشر دقائق
# حملت كودَ `vc35` وختمَ `34/3.1`، وفارقُها عن الصحيحة **بايتان** ⇒ لا الحجمُ
# ولا التوقيعُ ولا الزمنُ يفرزها. و`versionName` **سلسلةٌ في مانيفست الحزمة**
# ⇒ الحزمةُ تشهد على ختمها بنفسها. **قِس من سطحِ الدعوى لا من سطحٍ مجاور.**
if ($null -eq $mfText) { Add-Check "ختمُ الإصدار" "unmeasured" "تعذّر قراءةُ مانيفست الحزمة" }
elseif ($null -eq $vName) { Add-Check "ختمُ الإصدار" "unmeasured" "تعذّرت قراءةُ versionName من build.gradle.kts" }
elseif ($mfText.Contains($vName)) {
    Add-Check "ختمُ الإصدار" "pass" "مانيفستُ الحزمة يحمل '$vName' — يطابق الشجرة"
} else {
    Add-Check "ختمُ الإصدار" "fail" "الشجرةُ تقول '$vName' والحزمةُ لا تحمله ⇒ **بُنيت قبل رفعِ الرقم**. أعِد البناء: .\gradlew :app:bundleRelease"
}

# ② التوقيع
$jsv = & jarsigner -verify $AAB 2>&1 | Out-String
if ($jsv -match "jar verified")   { Add-Check "التوقيع" "pass" "jar verified" }
elseif ($jsv -match "unsigned")   { Add-Check "التوقيع" "fail" "الحزمةُ **غيرُ موقَّعة**" }
else                              { Add-Check "التوقيع" "unmeasured" "لم يُفهَم جوابُ jarsigner" }

# ③ البصمة مقابل **آخرِ حزمةٍ مؤرشَفة** — مرجعٌ يُقاس لا رقمٌ مجمَّد هنا
$archiveDir = Join-Path (Split-Path (Split-Path $PROJ -Parent) -Parent) "Workspace\Releases\SchoolAppyemen"
function Get-Fingerprint($aab) {
    $o = & keytool -printcert -jarfile $aab 2>$null | Out-String
    if ($o -match 'SHA256:\s*([0-9A-F:]+)') { return $Matches[1].Trim() }
    return $null
}
$mine = Get-Fingerprint $AAB
$baseline = $null; $baselineName = "—"
if (Test-Path $archiveDir) {
    $prev = Get-ChildItem $archiveDir -Filter *.aab -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending |
            Where-Object { (Get-FileHash $_.FullName -Algorithm SHA256).Hash -ne (Get-FileHash $AAB -Algorithm SHA256).Hash } |
            Select-Object -First 1
    if ($prev) { $baseline = Get-Fingerprint $prev.FullName; $baselineName = $prev.Name }
}
if (-not $mine)          { Add-Check "بصمةُ التوقيع" "unmeasured" "تعذّرت قراءةُ الشهادة" }
elseif (-not $baseline)  { Add-Check "بصمةُ التوقيع" "unmeasured" "لا حزمةَ مؤرشَفةٌ سابقةٌ تصلح مرجعاً" }
elseif ($mine -eq $baseline) { Add-Check "بصمةُ التوقيع" "pass" "تطابق $baselineName" }
else { Add-Check "بصمةُ التوقيع" "fail" "**تخالف** $baselineName ⇒ مفتاحٌ مختلف. Play يرفض التحديث بلا رجعة" }

# ④ مشروعُ FCM: الحزمةُ تحمل مشروعَ google-services.json نفسَه
$gsvc = Join-Path $PROJ "app\google-services.json"
if (-not (Test-Path $gsvc)) { Add-Check "مشروع FCM" "unmeasured" "لا google-services.json" }
else {
    $gs = Get-Content $gsvc -Raw | ConvertFrom-Json
    $fcmProject = $gs.project_info.project_id
    $pkgs = @($gs.client | ForEach-Object { $_.client_info.android_client_info.package_name })
    $inBundle = $false
    Get-ChildItem $tmp -Recurse -File | ForEach-Object {
        if (-not $inBundle) { if ((Read-Bytes-Text $_.FullName).Contains($fcmProject)) { $inBundle = $true } }
    }
    if (-not $inBundle) { Add-Check "مشروع FCM" "fail" "'$fcmProject' **غائبٌ عن الحزمة** ⇒ رموزُ FCM تُصكّ لمشروعٍ آخر والبثُّ يُرفض صامتاً" }
    elseif ($appId -and ($pkgs -notcontains $appId)) { Add-Check "مشروع FCM" "fail" "google-services.json لا يسجّل '$appId'" }
    else { Add-Check "مشروع FCM" "pass" "'$fcmProject' حاضرٌ في الحزمة · والحزمةُ مسجَّلةٌ باسم '$appId'" }
}

# ⑤ جسرُ الإشعارات: غيابُ getFcmToken أعدم إشعاراتِ vc31 صامتاً
if ($null -eq $dexText) { Add-Check "جسرُ getFcmToken" "unmeasured" "تعذّرت قراءةُ classes.dex" }
elseif ($dexText.Contains("getFcmToken")) { Add-Check "جسرُ getFcmToken" "pass" "حاضرٌ في dex" }
else { Add-Check "جسرُ getFcmToken" "fail" "**غائبٌ من dex** — أزاله R8 على الأرجح ⇒ لا رمزَ ولا إشعارات" }

# ⑥ App Links: كلُّ مسارٍ في مانيفست المصدر حاضرٌ في الحزمة
$srcMf = Join-Path $PROJ "app\src\main\AndroidManifest.xml"
if (-not (Test-Path $srcMf) -or $null -eq $mfText) { Add-Check "مسارات App Links" "unmeasured" "تعذّرت المقارنة" }
else {
    $srcPaths = @([regex]::Matches((Get-Content $srcMf -Raw), 'android:path(?:Prefix|Pattern)?\s*=\s*"([^"]+)"') |
                  ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
    if ($srcPaths.Count -eq 0) { Add-Check "مسارات App Links" "unmeasured" "لا مساراتٍ في مانيفست المصدر" }
    else {
        $missing = @($srcPaths | Where-Object { -not $mfText.Contains($_) })
        if ($missing.Count) { Add-Check "مسارات App Links" "fail" "$($missing.Count) من $($srcPaths.Count) **غائبةٌ عن الحزمة**: $($missing -join ' · ')" }
        else { Add-Check "مسارات App Links" "pass" "$($srcPaths.Count)/$($srcPaths.Count) حاضرة — والمطالبةُ مقيَّدةٌ بها" }
    }
}

# ⑦ رقمُ الإصدار عند Play: محروقٌ أم حرّ؟
if ($Offline) { Add-Check "رقمُ الإصدار عند Play" "unmeasured" "-Offline ⇒ لم يُسأل Play" }
elseif (-not (Test-Path (Join-Path $PROJ "deploy-to-play.ps1"))) { Add-Check "رقمُ الإصدار عند Play" "unmeasured" "لا deploy-to-play.ps1" }
else {
    $dry = & pwsh -File (Join-Path $PROJ "deploy-to-play.ps1") -Track internal -DryRun 2>&1 | Out-String
    $dry = [regex]::Replace($dry, "`e\[[0-9;]*[A-Za-z]", "")
    if ($dry -match 'Bundles on Play:\s*([\d,\s]+)') {
        $codes = @($Matches[1] -split ',' | ForEach-Object { [int]$_.Trim() })
        if ($null -eq $vCode) { Add-Check "رقمُ الإصدار عند Play" "unmeasured" "تعذّرت قراءةُ versionCode" }
        elseif ($codes -contains $vCode) { Add-Check "رقمُ الإصدار عند Play" "fail" "**$vCode محروقٌ سلفاً** — Play يحجز أيَّ رقمٍ رُفع ولو بقي مسوّدة. ارفع الرقم" }
        else { Add-Check "رقمُ الإصدار عند Play" "pass" "$vCode حرٌّ (المرفوعة: $($codes -join ', '))" }
    } else { Add-Check "رقمُ الإصدار عند Play" "unmeasured" "تعذّرت قراءةُ قائمة الحزم من Play" }
}

} finally { if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue } }

# ── الحكم ─────────────────────────────────────────────────────────────────
$fail = @($checks | Where-Object { $_.state -eq 'fail' })
$unm  = @($checks | Where-Object { $_.state -eq 'unmeasured' })
$verdict = if ($fail.Count) { "NO-GO" } elseif ($unm.Count) { "INCOMPLETE" } else { "GO" }

if ($Json) {
    [ordered]@{ verdict = $verdict; appId = $appId; versionCode = $vCode; versionName = $vName; checks = $checks } | ConvertTo-Json -Depth 5
    exit ($(if ($fail.Count) { 2 } elseif ($unm.Count) { 3 } else { 0 }))
}

Write-Host ""
Write-Host "  release-preflight — $appId  vc$vCode / $vName" -ForegroundColor Cyan
Write-Host ""
foreach ($c in $checks) {
    switch ($c.state) {
        'pass'       { Write-Host ("  [OK] {0,-22} {1}" -f $c.name, $c.detail) -ForegroundColor Green }
        'fail'       { Write-Host ("  [X ] {0,-22} {1}" -f $c.name, $c.detail) -ForegroundColor Red }
        default      { Write-Host ("  [? ] {0,-22} {1}" -f $c.name, $c.detail) -ForegroundColor Yellow }
    }
}
Write-Host ""
switch ($verdict) {
    "GO"         { Write-Host "  ✅ GO — كلُّ الفحوص مقيسةٌ وخضراء." -ForegroundColor Green
                   Write-Host "     🔴 ويبقى ضابطٌ لا تقيسه أداة: شغّل الحزمةَ على جهازٍ حقيقيّ." -ForegroundColor Yellow }
    "NO-GO"      { Write-Host "  🛑 NO-GO — $($fail.Count) فحصاً أحمر. لا تَرفع." -ForegroundColor Red }
    "INCOMPLETE" { Write-Host "  ⚠️  INCOMPLETE — $($unm.Count) مصدراً **لم يُقَس**. وهذا ليس نجاحاً." -ForegroundColor Yellow
                   Write-Host "     «صفر» و«لم أستطع القياس» ليسا الشيء نفسه." -ForegroundColor DarkYellow }
}
Write-Host ""
exit ($(if ($fail.Count) { 2 } elseif ($unm.Count) { 3 } else { 0 }))
