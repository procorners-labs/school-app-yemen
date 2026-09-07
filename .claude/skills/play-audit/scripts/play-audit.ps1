<#
  play-audit — تدقيقُ مساراتِ Play الأربعة قبل أيّ قرارِ إصدار.

  🔴 قراءةٌ محضة: يستدعي `deploy-to-play.ps1 -DryRun` (الذي يحذف مسوّدتَه بنفسه)
     ويحلّل مخرَجَه. لا يبني، لا يرفع، لا يرقّي، ولا يلمس ملفَّ مشروع.

  رموزُ الخروج: 0 نظيف · 1 تحذير · 2 نتائجُ حمراء · 3 تعذّر القياس.
#>
[CmdletBinding()]
param(
    # جذرُ المشروع (افتراضي: يُكتشف بالصعود بحثاً عن `deploy-to-play.ps1`)
    [string] $Path = (Get-Location).Path,

    # 🔴 مدخلٌ بديلٌ للضابط المعاكس: نصُّ مخرَجٍ محفوظ بدل استدعاء Play حيّاً.
    #    وجودُه يجعل التشغيلَ **بلا شبكةٍ بتاتاً** — وهو ما يسمح بإثبات أن الحارس
    #    يحمرّ على حالةٍ معطوبة ويخضرّ على السليمة، بلا لمسِ حسابٍ حقيقيّ.
    [string] $InputFile = "",

    [switch] $Json
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding           = [System.Text.Encoding]::UTF8
$ErrorActionPreference    = "Stop"

# ── اكتشافُ الجذر: لا مسارَ مثبَّت ──────────────────────────────────────────
function Find-Root([string]$start) {
    $d = Resolve-Path -LiteralPath $start -ErrorAction SilentlyContinue
    if (-not $d) { return $null }
    $cur = Get-Item -LiteralPath $d
    for ($i = 0; $i -lt 40 -and $cur; $i++) {
        if (Test-Path (Join-Path $cur.FullName "deploy-to-play.ps1")) { return $cur.FullName }
        $cur = $cur.Parent
    }
    return $null
}

$PROJ = Find-Root $Path
if (-not $PROJ) {
    Write-Host "[X] لا `deploy-to-play.ps1` في أيّ أصلٍ من: $Path" -ForegroundColor Red
    exit 3
}

# ── ما تقوله الشجرة (يُقرأ وقتَ التشغيل — لا رقمَ مجمَّدٌ في هذا الملفّ) ─────
$gradle       = Join-Path $PROJ "app\build.gradle.kts"
$treeCode     = $null; $treeName = $null; $treeTarget = $null
if (Test-Path $gradle) {
    $g = Get-Content $gradle -Raw
    if ($g -match 'versionCode\s*=\s*(\d+)')        { $treeCode   = [int]$Matches[1] }
    if ($g -match 'versionName\s*=\s*"([\d\.]+)"')  { $treeName   = $Matches[1] }
    if ($g -match 'targetSdk\s*=\s*(\d+)')          { $treeTarget = [int]$Matches[1] }
}

# ── المخرَج: حيٌّ أو من ملفّ ────────────────────────────────────────────────
$src = "Play (live, dry-run)"
if ($InputFile) {
    if (-not (Test-Path $InputFile)) {
        Write-Host "[X] -InputFile غير موجود: $InputFile" -ForegroundColor Red
        exit 3
    }
    $raw = Get-Content -LiteralPath $InputFile -Raw -Encoding UTF8
    $src = "ملفّ: $InputFile"
} else {
    $raw = & pwsh -File (Join-Path $PROJ "deploy-to-play.ps1") -Track internal -DryRun 2>&1 | Out-String
}

# ── التحليل ────────────────────────────────────────────────────────────────
# 🔴 تُنزع رموزُ ANSI أوّلاً: المخرَجُ ملوَّن، و`(\S+)\s*$` تبتلع رمزَ إعادة الضبط
#   فيخرج الحقلُ **فارغاً بلا خطأ** — فشلٌ صامتٌ يجعل الحارسَ يبدو عاملاً وهو أعمى.
$raw = [regex]::Replace($raw, "`e\[[0-9;]*[A-Za-z]", "")
$releases = @()
foreach ($line in ($raw -split "`r?`n")) {
    if ($line -match '^\s*\[([a-z]+)\s*\]\s*(.+?)\s*\(codes:\s*([^)]*)\)\s*-\s*(\S+)\s*$') {
        # 🔴 تُلتقط المجموعاتُ في متغيّراتٍ **قبل** أيّ استعمالٍ لاحق: أيُّ `-match`
        #   داخل الأنبوب أدناه يدهس `$Matches`، وقيمُ الجدول تُقيَّم بالترتيب ⇒ كان
        #   `status` يخرج **فارغاً بلا خطأ**. فشلٌ صامتٌ أظهره ضابطٌ لا قراءةٌ بصرية.
        $mTrack = $Matches[1]; $mName = $Matches[2].Trim()
        $mCodes = $Matches[3]; $mStatus = $Matches[4]
        $releases += [pscustomobject]@{
            track  = $mTrack
            name   = $mName
            codes  = @($mCodes -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '^\d+$' } | ForEach-Object { [int]$_ })
            status = $mStatus
        }
    }
}
$playCodes = @()
if ($raw -match 'Bundles on Play:\s*([\d,\s]+)') {
    $playCodes = @($Matches[1] -split ',' | ForEach-Object { [int]$_.Trim() }) | Sort-Object
}
$nextFree = $null
if ($raw -match 'next free:\s*(\d+)') { $nextFree = [int]$Matches[1] }

if ($releases.Count -eq 0) {
    Write-Host "[X] لم يُقرأ أيُّ إصدارٍ من المخرَج — لا يُفترض «لا مسارات»." -ForegroundColor Red
    Write-Host "    قد يكون الاكتشافُ فشل (شبكة/أذون). القياسُ ناقصٌ لا نظيف." -ForegroundColor Red
    exit 3
}

# أعلى رقمٍ نشطٍ على production = المرجعُ الذي تُقاس عليه بقيّةُ المسارات
$prodCodes = @($releases | Where-Object { $_.track -eq 'production' } | ForEach-Object { $_.codes } )
$prodMax   = if ($prodCodes.Count) { ($prodCodes | Measure-Object -Maximum).Maximum } else { $null }

$findings = @()
$warnings = @()

# ① 🔴 المطابقةُ تُقاس على اتّحاد المسارات — لا على الإنتاج وحده
foreach ($r in $releases) {
    if ($r.track -eq 'production') { continue }
    foreach ($c in $r.codes) {
        if ($null -ne $prodMax -and $c -lt $prodMax) {
            $findings += "المسارُ [$($r.track)] يحمل الحزمة $c وهي **أقدمُ** من إنتاجٍ على $prodMax — «$($r.name)»"
        }
    }
}

# ② ⚠️ `completed` حالةُ طلبٍ لا حالةُ توزيعٍ ولا نشاطِ مسار
$statuses = @($releases | ForEach-Object { $_.status } | Sort-Object -Unique)

# ③ 🔴 الرقمُ التالي = آخرُ ما رُفع + 1 — هل رقمُ الشجرة محروق؟
$burned = ($null -ne $treeCode -and $playCodes -contains $treeCode)
if ($burned) {
    $findings += "رقمُ الشجرة $treeCode **محروقٌ عند Play** — رُفع سلفاً ولو لم يُنشر. التالي الحرّ: $nextFree"
}
if ($null -eq $treeCode)   { $warnings += "تعذّرت قراءةُ versionCode من build.gradle.kts" }
if ($null -eq $treeTarget) { $warnings += "تعذّرت قراءةُ targetSdk من build.gradle.kts" }
if ($null -eq $prodMax)    { $warnings += "لا إصدارَ نشطاً على production — فلا مرجعَ تُقاس عليه المسارات" }

# ── العرض ──────────────────────────────────────────────────────────────────
$report = [ordered]@{
    source     = $src
    tree       = [ordered]@{ versionCode = $treeCode; versionName = $treeName; targetSdk = $treeTarget; codeBurned = $burned }
    play       = [ordered]@{ bundles = $playCodes; nextFree = $nextFree; productionMax = $prodMax }
    releases   = $releases
    findings   = $findings
    warnings   = $warnings
}

if ($Json) { $report | ConvertTo-Json -Depth 6; exit ($(if ($findings.Count) { 2 } elseif ($warnings.Count) { 1 } else { 0 })) }

Write-Host ""
Write-Host "  play-audit — $($PROJ)" -ForegroundColor Cyan
Write-Host "  source: $src" -ForegroundColor DarkGray
Write-Host ""
Write-Host "  TRACKS (all four - compliance is measured on their UNION)" -ForegroundColor Cyan
foreach ($r in $releases) {
    $mark = if ($r.track -ne 'production' -and $null -ne $prodMax -and ($r.codes | Where-Object { $_ -lt $prodMax })) { "!!" } else { "  " }
    $col  = if ($mark -eq "!!") { "Red" } else { "Gray" }
    Write-Host ("  {0} [{1,-10}] codes: {2,-10} status: {3,-10} {4}" -f $mark, $r.track, ($r.codes -join ','), $r.status, $r.name) -ForegroundColor $col
}
Write-Host ""
Write-Host "  ⚠️  status «$($statuses -join '/')» حالةُ **طلبٍ** لا توزيعٍ ولا نشاطِ مسار." -ForegroundColor Yellow
Write-Host "     دليلُه تباينُ سطحين: لوحةُ «beta» تقول «لا إصدارات نشطة» والـAPI يقول completed." -ForegroundColor DarkYellow
Write-Host "     ⇒ «الحيّ» يُقرأ من «نظرة عامة على النشر» وسجلِّ الإرسال وحدهما." -ForegroundColor DarkYellow
Write-Host ""
Write-Host ("  VERSIONS  tree: vc{0}/{1} targetSdk {2}   |   play next free: {3}" -f $treeCode, $treeName, $treeTarget, $nextFree) -ForegroundColor Cyan
Write-Host ""

if ($findings.Count) {
    Write-Host "  FINDINGS ($($findings.Count))" -ForegroundColor Red
    foreach ($f in $findings) { Write-Host "  🔴 $f" -ForegroundColor Red }
    Write-Host ""
    Write-Host "     مسارُ اختبارٍ يحمل حزمةً أقدمَ من الإنتاج يُبقي إنذارَ **الحساب كلِّه**" -ForegroundColor Red
    Write-Host "     ولو كان الإنتاجُ مطابقاً. والعلاجُ الذي يوصي به Play: **إيقافُ المسار**" -ForegroundColor Red
    Write-Host "     مؤقتاً — لا ترقيةٌ إليه. (وقع مقيساً 2026-09-07: حزمتا أبريل 4 و5.)" -ForegroundColor Red
} else {
    Write-Host "  FINDINGS: لا شيء 🎉" -ForegroundColor Green
}
if ($warnings.Count) {
    Write-Host ""
    Write-Host "  WARNINGS ($($warnings.Count)) — مصادرُ لم تُقَس، وهي ليست «صفراً»" -ForegroundColor Yellow
    foreach ($w in $warnings) { Write-Host "  ⚠️  $w" -ForegroundColor Yellow }
}
Write-Host ""

exit ($(if ($findings.Count) { 2 } elseif ($warnings.Count) { 1 } else { 0 }))
