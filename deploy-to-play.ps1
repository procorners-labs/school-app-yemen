<#
.SYNOPSIS
    SchoolApp Yemen - Google Play Auto-Deploy
    Discovers app state, uploads AAB, assigns track.
.USAGE
    .\deploy-to-play.ps1                              # بناءٌ مرفوعٌ إلى internal
    .\deploy-to-play.ps1 -Track internal
    .\deploy-to-play.ps1 -Promote -Track production   # ترقيةُ حزمةٍ مرفوعةٍ سلفاً
#>

param(
    [ValidateSet("internal","alpha","beta","production")]
    [string]$Track = "internal",    # Start safe with internal
    [switch]$DryRun,                # Preview without uploading

    # ── وضعُ الترقية — أُضيف 2026-09-06 ────────────────────────────────────────
    # 🔴 **العلّةُ التي سدّه:** كان السكربتُ **يرفع الحزمةَ دائماً** (‏STEP 5 بلا شرط)
    #   ولا يملك سبيلاً لتعيين حزمةٍ **مرفوعةٍ سلفاً** إلى مسارٍ آخر. وPlay يرفض رفعَ
    #   نفس `versionCode` مرّتين ⇒ `-Track production` بعد رفعٍ ناجح إلى `internal`
    #   **يصطدم حتماً** بـ«سبق أن تم استخدام رمز الإصدار». وقع فعلاً 2026-09-06.
    # 🔴 **والفرقُ جوهريٌّ لا اختصار:** الترقيةُ **لا تستهلك رقماً ولا تحتاج ملفّاً**،
    #   وإعادةُ البناء لأجلها تحرق رقماً بلا مقابل وتُنتج أثراً **غير الذي اختُبر**.
    [switch]$Promote,
    # الرقمُ المراد ترقيتُه. صفرٌ ⇒ يُشتقّ من **أعلى رقمٍ مرفوعٍ على Play**، لا من
    # `build.gradle.kts`: الشجرةُ ليست شاهداً على ما عند Play (قاعدةُ الأثر المبنيّ).
    [int]$VersionCode = 0,

    # ── رايةُ عزلٍ — أُضيفت 2026-09-06 ────────────────────────────────────────
    # 🔴 **غرضُها تشخيصيٌّ محضٌ لا تجميليّ:** بند
    #   `play-upload-silently-dropped-root-unmeasured` بقي بجذرٍ غيرِ معزول لأن
    #   التجربةَ الناجحة غيّرت ثلاثةَ متغيّرات معاً. و`releaseNotes` هي المتغيّرُ
    #   الوحيد الباقي بعد أن ثبت `track` ونوعُ `versionCodes` في الشكل العامل.
    # 🟢 **والعزلُ صار مجانيّاً في وضع `-Promote`** — لا رفعَ فيه فلا رقمَ يُحرق،
    #   بعد أن كان يُقدَّر بثلاثة أرقامٍ محروقة. هذا وحده مبرّرُ الراية.
    # 🔴 **وقد استُعملت فعلاً 2026-09-06 فنقضت فرضَها: `releaseNotes` بريئة.**
    #   تُترك لأنها أثبتت نفعَها كأداةِ عزلٍ رخيصة، لا لأن الفرضَ صحيح. التفصيل عند
    #   `$trackBody` أدناه.
    # ⚠️ وأثرُها أن الإصدار يصل **بلا ملاحظات** — تُضاف من Play Console يدوياً،
    #   وهي حقلُ عرضٍ لا سلوك.
    [switch]$NoReleaseNotes,

    # ── اسمُ الإصدار في وضع الترقية — مخرَجُ الطوارئ وحده ─────────────────────
    # 🔴 **لا يُستعمل إلّا حين يعجز الاشتقاقُ من Play** (حزمةٌ مرفوعةٌ لم تُسنَد لأيّ
    #   مسارٍ بعد، فلا إصدارَ يحمل اسمَها). والافتراضُ أن يبقى فارغاً: الاسمُ يُقرأ
    #   من Play لا يُكتب بيد، لأن اليدَ هي التي أدخلت الخطأ أصلاً (انظر أدناه).
    [string]$ReleaseName = "",

    # ── 🎯 المرشّحُ السادس لسقوطِ الرفع الصامت — أداةُ عزلٍ لا إصلاحٌ مُثبَت ────────
    # يضيف `?changesNotSentForReview=true` إلى نداء `Edits.commit`.
    # **الفرضيّة:** التطبيقُ عليه **نشرٌ مُدار**، والنشرُ المُدار **لا يسري على
    # `internal`** ⇒ يفسّر لماذا ينجح `internal` وحدَه وتسقط الثلاثةُ الأخرى صامتةً.
    # 🔴 **وحدُّها يُقال: فرضيّةٌ لم تُثبَت** — وسقط قبلها خمسةُ مرشّحين، آخرُها الأذونُ
    #   التي بَرِئت **بالأثر** (مُنِح «المشرف الكامل» ثمّ سقطت الترقيةُ سواءً · 2026-09-08).
    # 🟢 والاختبارُ بها **مجانيٌّ في وضع `-Promote`**: لا رفعَ فيه فلا رقمَ يُحرق.
    [switch]$ChangesNotSentForReview
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding          = [System.Text.Encoding]::UTF8
$ErrorActionPreference   = "Stop"

# ── Config ───────────────────────────────────────────────────────────────────
$PACKAGE_NAME  = "com.proconrers.schoolappyemen"
$PROJ          = "C:\Users\osama\AndroidStudioProjects\SchoolAppyemen"
$KEY_FILE      = "$PROJ\service-account-key.json"
$AAB_PATH      = "$PROJ\app\build\outputs\bundle\release\app-release.aab"
$PLAY_SCOPE    = "https://www.googleapis.com/auth/androidpublisher"
$TOKEN_URL     = "https://oauth2.googleapis.com/token"
$API_BASE      = "https://androidpublisher.googleapis.com/androidpublisher/v3"
$UPLOAD_BASE   = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3"

Set-Location $PROJ

function Banner($m) { Write-Host $m -ForegroundColor Cyan }
function OK($m)     { Write-Host "  [OK]  $m" -ForegroundColor Green }
function ERR($m)    { Write-Host "  [!!]  $m" -ForegroundColor Red }
function WARN($m)   { Write-Host "  [>>]  $m" -ForegroundColor Yellow }
function INFO($m)   { Write-Host "  [--]  $m" -ForegroundColor Gray }
function STEP($m)   { Write-Host "`n>>> $m" -ForegroundColor White }

Banner ""
Banner "======================================================"
Banner "   SchoolApp Yemen - Google Play Auto-Deploy"
Banner "======================================================"
Banner "   Package : $PACKAGE_NAME"
Banner "   Track   : $Track$(if($DryRun){' [DRY RUN]'})"
Banner "   Mode    : $(if($Promote){'PROMOTE (no upload, no new versionCode)'}else{'UPLOAD'})"
Banner ""

# ════════════════════════════════════════════════════════════════════════════
# STEP 0: Check Service Account Key
# ════════════════════════════════════════════════════════════════════════════
STEP "Checking service account key..."

if (-not (Test-Path $KEY_FILE)) {
    ERR "service-account-key.json NOT FOUND"
    Banner ""
    Banner "======================================================"
    Banner "  ONE-TIME SETUP: Get your Service Account Key"
    Banner "======================================================"
    Banner ""
    Banner "  Follow these exact steps (takes ~5 minutes):"
    Banner ""
    Banner "  STEP A: Open Google Play Console"
    Banner "    https://play.google.com/console"
    Banner ""
    Banner "  STEP B: Setup -> API access"
    Banner "    Left menu -> Setup -> API access"
    Banner ""
    Banner "  STEP C: Link Google Cloud Project"
    Banner "    Click: 'Link to an existing Google Cloud project'"
    Banner "    OR click: 'Create a new Google Cloud project'"
    Banner ""
    Banner "  STEP D: Create Service Account"
    Banner "    Click: 'Create new service account'"
    Banner "    -> Opens Google Cloud Console"
    Banner "    -> Click 'CREATE SERVICE ACCOUNT'"
    Banner "    -> Name: 'schoolapp-play-deploy'"
    Banner "    -> Click 'CREATE AND CONTINUE'"
    Banner "    -> Role: 'Service Account User' -> DONE"
    Banner ""
    Banner "  STEP E: Grant Play Console Access"
    Banner "    Back in Play Console -> API access"
    Banner "    Find the service account -> click 'Grant access'"
    Banner "    Permissions: Check 'Release manager'"
    Banner "    Click 'Invite user'"
    Banner ""
    Banner "  STEP F: Download JSON Key"
    Banner "    Google Cloud Console -> IAM -> Service Accounts"
    Banner "    Find 'schoolapp-play-deploy' -> Keys -> ADD KEY"
    Banner "    -> Create new key -> JSON -> CREATE"
    Banner "    File downloads automatically"
    Banner ""
    Banner "  STEP G: Place the key file here:"
    Banner "    $KEY_FILE"
    Banner ""
    Banner "  Then run this script again: .\deploy-to-play.ps1"
    Banner ""
    Start-Process "https://play.google.com/console"
    exit 0
}

$keyJson = Get-Content $KEY_FILE -Raw | ConvertFrom-Json
OK "Service account: $($keyJson.client_email)"

# ════════════════════════════════════════════════════════════════════════════
# STEP 1: Check AAB exists and is signed
# ════════════════════════════════════════════════════════════════════════════
STEP "Checking AAB..."

# 🔴 في وضع الترقية **لا يُلمَس الملفُّ إطلاقاً** — لا وجودُه ولا توقيعُه ولا بناؤه.
#   الحزمةُ المعنيّة **عند Play سلفاً**، والملفُّ المحلّي قد يكون بناءً أحدثَ أو مدهوساً
#   بـ`clean` ⇒ فحصُه هنا يقيس أثراً **غير الذي يُرقَّى**، وقد يُطلق بناءً لا لزوم له.
if ($Promote) {
    INFO "Promote mode: skipping AAB checks entirely (nothing is uploaded)"
    $aabItem = $null
    $aabMB   = 0
}
elseif (-not (Test-Path $AAB_PATH)) {
    WARN "AAB not found. Building now..."
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"
    & .\gradlew.bat bundleRelease 2>&1 | Select-Object -Last 5
    if (-not (Test-Path $AAB_PATH)) {
        ERR "AAB build failed. Run: .\gradlew.bat bundleRelease"
        exit 1
    }
}

if (-not $Promote) {
    $aabItem = Get-Item $AAB_PATH
    $aabMB   = [math]::Round($aabItem.Length / 1MB, 1)
    OK "AAB: $($aabItem.Name) ($aabMB MB)"
    OK "Path: $AAB_PATH"

    # Verify signed
    $jsv = & "$env:JAVA_HOME\bin\jarsigner.exe" -verify $AAB_PATH 2>&1 | Out-String
    if ($jsv -match "unsigned") {
        ERR "AAB is UNSIGNED. Run: .\build-and-test.ps1"
        exit 1
    }
    OK "AAB is signed"

    # 🔴 **الرفضُ هنا لا عند بناء الجسم — والفرقُ أن الرقم يُحرق أو لا يُحرق.**
    #   وُضع الفحصُ أوّلَ مرّة عند `$releaseNotes` (‏STEP 6) وهو **بعد الرفع** ⇒ كان
    #   يرفض بعد استهلاك `versionCode` عند Play للأبد. حارسٌ يحمي بعد وقوع ما يحميه منه
    #   ليس حارساً. ⇒ يُقاس من `build.gradle.kts` قبل لمسِ الشبكة.
    if (-not $NoReleaseNotes) {
        $bkEarly = Get-Content "$PROJ\app\build.gradle.kts" -Raw
        if ($bkEarly -match 'versionCode\s*=\s*(\d+)') {
            $pendingCode = $Matches[1]
            $pendingNotes = Join-Path $PROJ "_docs\release-notes\vc$pendingCode.json"
            if (-not (Test-Path $pendingNotes)) {
                ERR "لا ملفَّ ملاحظاتٍ للإصدار vc$pendingCode :"
                ERR "  _docs\release-notes\vc$pendingCode.json"
                ERR "اكتبه قبل الرفع — ولا نصَّ افتراضيٌّ هنا عمداً: النصُّ العامُّ يُخفي أنك نسيتَ،"
                ERR "وقد أُرسلت فعلاً وعودُ vc31 مع إصداراتٍ لاحقةٍ لا تحملها."
                ERR 'الشكل: [ { "language": "ar", "text": "..." }, { "language": "en-US", "text": "..." } ]'
                ERR "أو -NoReleaseNotes لرفعٍ بلا ملاحظاتٍ عن قصد."
                exit 1
            }
            OK "ملاحظاتُ الإصدار حاضرةٌ: vc$pendingCode.json"
        }
    }
}

# ════════════════════════════════════════════════════════════════════════════
# STEP 2: Authenticate with Google Play API (JWT -> OAuth2 token)
# ════════════════════════════════════════════════════════════════════════════
STEP "Authenticating with Google Play API..."

function New-GoogleJwt {
    param($email, $privateKeyPem, $scope)

    # Header
    $header  = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('{"alg":"RS256","typ":"JWT"}')) -replace '\+','-' -replace '/','_' -replace '=',''
    $iat = [int][double]::Parse((Get-Date -UFormat %s))
    $exp = $iat + 3600
    $payload = @{ iss=$email; scope=$scope; aud=$TOKEN_URL; exp=$exp; iat=$iat } | ConvertTo-Json -Compress
    $payloadB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($payload)) -replace '\+','-' -replace '/','_' -replace '=',''
    $signingInput = "$header.$payloadB64"

    # Load RSA private key
    $pem = $privateKeyPem -replace "-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s",""
    $keyBytes = [Convert]::FromBase64String($pem)
    $rsa = [System.Security.Cryptography.RSA]::Create()
    $rsa.ImportPkcs8PrivateKey($keyBytes, [ref]$null)

    # Sign
    $dataBytes = [Text.Encoding]::UTF8.GetBytes($signingInput)
    $sig = $rsa.SignData($dataBytes, [Security.Cryptography.HashAlgorithmName]::SHA256, [Security.Cryptography.RSASignaturePadding]::Pkcs1)
    $sigB64 = [Convert]::ToBase64String($sig) -replace '\+','-' -replace '/','_' -replace '=',''

    return "$signingInput.$sigB64"
}

$jwt = New-GoogleJwt -email $keyJson.client_email -privateKeyPem $keyJson.private_key -scope $PLAY_SCOPE

$tokenResp = Invoke-RestMethod -Uri $TOKEN_URL -Method Post -ContentType "application/x-www-form-urlencoded" -Body @{
    grant_type = "urn:ietf:params:oauth:grant-type:jwt-bearer"
    assertion  = $jwt
}
$accessToken = $tokenResp.access_token
$authHeader  = @{ Authorization = "Bearer $accessToken" }
OK "Authenticated successfully"

# ════════════════════════════════════════════════════════════════════════════
# STEP 3: Create edit session
# ════════════════════════════════════════════════════════════════════════════
# 🔴 أُنشئت هنا لا بعد الاستكشاف — أُصلح 2026-09-05 بعد قياسٍ مباشر:
#   كان الاستكشاف يضرب `applications/<pkg>/tracks` و`/bundles` **بلا مقطع
#   `edits/<editId>`**، وواجهةُ Play توجبه ⇒ **404 دائماً وأبداً**، لا «أحياناً».
#   والأخبثُ أن الرسالتين تدعوان لاستنتاجٍ خاطئ بثقة: «app may be new on Play»
#   و«No bundles found yet (first upload)» — والتطبيقُ **منشورٌ بـ663 تثبيتاً**.
#   ⇒ فحصُ ما قبل الرفع كان يكذب صامتاً، وهو الفحصُ الوحيد الذي يكشف
#   أن `versionCode` محروقٌ سلفاً قبل عمليةٍ **لا رجعةَ فيها**.
# 🟢 والتصحيحُ مجّانيّ: الجلسةُ كانت تُنشأ بعد أسطر على أي حال، فنُقلت لا أُضيفت.
#   وفي `-DryRun` تُحذف صراحةً قبل الخروج فلا تتراكم مسوّداتٌ معلَّقة.
STEP "Creating Play edit session..."

$editResp = Invoke-RestMethod -Uri "$API_BASE/applications/$PACKAGE_NAME/edits" `
    -Method Post -Headers $authHeader -ContentType "application/json" -Body "{}"
$editId = $editResp.id
OK "Edit session created: $editId (expires: $($editResp.expiryTimeSeconds)s)"

# ════════════════════════════════════════════════════════════════════════════
# STEP 4: Discover current app state
# ════════════════════════════════════════════════════════════════════════════
STEP "Discovering current app state on Google Play..."

# Get current tracks
# ⚠️ يُهيَّأ صراحةً قبل الـtry: وضعُ الترقية يقرؤه لاحقاً لاشتقاق اسم الإصدار،
#   ومتغيّرٌ غيرُ معرَّفٍ يُقرأ `$null` صامتاً فيبدو «لا إصدارات» بدل «تعذّر القياس».
$tracksResp = $null
try {
    $tracksResp = Invoke-RestMethod -Uri "$API_BASE/applications/$PACKAGE_NAME/edits/$editId/tracks" -Headers $authHeader
    Banner ""
    Banner "  Current Release State:"
    # 🔴 **كلُّ** الإصدارات لا أوّلُها — أُصلح 2026-09-05 في نفس المرور:
    #   المسارُ الواحد يحمل عدّةَ إصدارات (مسوّدةٌ + مكتمِل)، و`Select-Object -First 1`
    #   كان يعرض المسوّدةَ الفارغة ويُخفي المنشورَ الحيّ ⇒ `production` يبدو **فارغاً**
    #   بينما عليه 663 تثبيتاً. وهذا **يعكس التشخيص** عند أيّ سؤالٍ عن الحالة الحيّة.
    foreach ($t in $tracksResp.tracks) {
        if (-not $t.releases) { Banner ("  [{0,-12}] (لا إصدارات)" -f $t.track); continue }
        foreach ($rel in $t.releases) {
            $codes = if ($rel.versionCodes) { $rel.versionCodes -join ", " } else { "—" }
            $nm    = if ($rel.name) { $rel.name } else { "(بلا اسم)" }
            Banner ("  [{0,-12}] {1} (codes: {2}) - {3}" -f $t.track, $nm, $codes, $rel.status)
        }
    }
    Banner ""
} catch {
    WARN "Could not fetch tracks (app may be new on Play): $($_.Exception.Message.Split("`n")[0])"
}

# Get APKs/bundles info
$playCodes = @()
try {
    $bundlesResp = Invoke-RestMethod -Uri "$API_BASE/applications/$PACKAGE_NAME/edits/$editId/bundles" -Headers $authHeader
    if ($bundlesResp.bundles) {
        $playCodes = @($bundlesResp.bundles | ForEach-Object { [int]$_.versionCode }) | Sort-Object
        $latest = ($playCodes | Select-Object -Last 1)
        OK "Bundles on Play: $($playCodes -join ', ')"
        # 🔴 القاعدة: التالي = آخرُ ما **رُفع** + 1، لا آخرُ ما **نُشر** + 1.
        #   Play يحجز أيَّ رقمٍ رُفع ولو بقي مسوّدةً للأبد.
        OK "Highest uploaded versionCode: $latest  =>  next free: $($latest + 1)"
    } else {
        INFO "No bundles listed (app may genuinely be new on Play)"
    }
} catch {
    WARN "Could not fetch bundles: $($_.Exception.Message.Split("`n")[0])"
}

# ════════════════════════════════════════════════════════════════════════════
# وضعُ الترقية: تثبيتُ الرقم والتحقّقُ من وجوده **قبل** أيّ فعل
# ════════════════════════════════════════════════════════════════════════════
# أيُّ خروجٍ مبكّرٍ يترك مسوّدةً معلَّقة — والمسوّداتُ الشاردة هي بعينها ما شوّش
# تشخيصَ حالة Play أمس. تُحذف قبل الخروج دائماً.
function Discard-Edit {
    try { Invoke-RestMethod -Uri "$API_BASE/applications/$PACKAGE_NAME/edits/$editId" -Method Delete -Headers $authHeader | Out-Null }
    catch { WARN "Could not discard edit $editId" }
}

if ($Promote) {
    if ($playCodes.Count -eq 0) {
        ERR "Promote requires the list of bundles on Play, and it could not be read."
        ERR "Refusing to guess a versionCode. Fix the discovery step first."
        Discard-Edit; exit 1
    }
    if ($VersionCode -le 0) {
        $VersionCode = ($playCodes | Select-Object -Last 1)
        INFO "No -VersionCode given; using highest uploaded on Play: $VersionCode"
    }
    # 🔴 الرفضُ المبكّر بدل رسالةٍ غامضة من Play بعد أن يكون التحريرُ قد بدأ.
    if ($playCodes -notcontains $VersionCode) {
        ERR "versionCode $VersionCode is NOT among the bundles uploaded to Play."
        ERR "Uploaded: $($playCodes -join ', ')"
        ERR "Promotion assigns an EXISTING bundle to a track - it cannot create one."
        Discard-Edit; exit 1
    }
    # ⚠️ تحذيرٌ لا حجب: الترقيةُ إلى نفس المسار الذي يحمله الرقمُ أصلاً لا فائدة منها.
    OK "Promoting existing versionCode $VersionCode  ->  [$Track]  (no upload)"

    # ────────────────────────────────────────────────────────────────────────
    # 🔴 اسمُ الإصدار وملاحظاتُه في وضع الترقية يُشتقّان من **Play** لا من الشجرة
    # ────────────────────────────────────────────────────────────────────────
    # **العيبُ الذي أُصلح هنا، وقع مقيساً 2026-09-07:** كان `$versionName` يُقرأ من
    # `build.gradle.kts` ويُستعمل في `name` **في الوضعين معاً**. فترقيةُ `vc34/3.1`
    # أرسلت `"name": "v3.2"` وملاحظاتِ `vc35` — وصفٌ لإصدارٍ **لم يُرفع أصلاً**
    # («الوضع الليلي» و«إشعارات فورية» موعودةٌ لحزمةٍ ليست هي).
    # 🟢 ولم يقع الضرر **لأن الترقية سقطت لسببٍ آخر** — صدفةٌ لا ضابط.
    #
    # 🔴 **والقاعدةُ المشتقّة أعمُّ من الحادثة:** `-Promote` **لا يلمس ملفّاً**، فلا
    #   يجوز أن يشتقّ وصفَه من شجرةٍ تقدّمت عن الحزمة. وهي فئةُ «الأثرُ المبنيُّ يشهد
    #   على نفسه، والشجرةُ لا تشهد عليه» — المسجَّلة في `CLAUDE.md` §الإصدارات.
    #
    # **المصدرُ الصحيح:** الإصدارُ القائم على Play الذي **يحمل هذا الرقم** — وهو ما
    # طبعته خطوةُ الاكتشاف أعلاه حرفياً (`[production] v3.1 (codes: 34)`).
    # 🔴 **ولا هبوطَ صامتٌ إلى الشجرة عند العجز** — الهبوطُ هو العطلُ بعينه، لأنه
    #   يُنتج وصفاً خاطئاً **بلا رسالةٍ واحدة**. يُرفض ويُسمَّى البديل.
    $promoteName  = ""
    $promoteNotes = $null
    if ($ReleaseName) {
        $promoteName = $ReleaseName
        WARN "Release name taken from -ReleaseName (manual override): $promoteName"
        WARN "Play was NOT consulted for this name. Verify it matches versionCode $VersionCode."
    }
    elseif ($null -eq $tracksResp) {
        ERR "Promote needs the release name that Play already holds for versionCode $VersionCode,"
        ERR "but the tracks listing could not be read (see the warning above)."
        ERR "Refusing to fall back to build.gradle.kts - the tree describes a DIFFERENT build."
        ERR "Fix discovery, or pass -ReleaseName explicitly if you know the correct name."
        Discard-Edit; exit 1
    }
    else {
        foreach ($t in $tracksResp.tracks) {
            foreach ($rel in $t.releases) {
                if ($rel.versionCodes -and (@($rel.versionCodes) -contains "$VersionCode")) {
                    if ($rel.name)         { $promoteName  = $rel.name }
                    if ($rel.releaseNotes) { $promoteNotes = $rel.releaseNotes }
                    break
                }
            }
            if ($promoteName) { break }
        }
        if (-not $promoteName) {
            ERR "versionCode $VersionCode is uploaded to Play but is not assigned to ANY track,"
            ERR "so Play holds no release name for it - and the tree's name belongs to a different build."
            ERR "Pass -ReleaseName '<the name this bundle was built as>' to proceed deliberately."
            Discard-Edit; exit 1
        }
        OK "Release name resolved FROM PLAY: $promoteName  (versionCode $VersionCode)"
        if ($promoteNotes) { OK "Release notes carried over from Play (not from the tree)" }
        else               { INFO "Play holds no release notes for this release; none will be sent" }
    }
}

if ($DryRun) {
    Banner ""
    if ($Promote) {
        Banner "  [DRY RUN] Would PROMOTE (no file is uploaded):"
        Banner "  - versionCode: $VersionCode  (already on Play)"
        Banner "  - Track: $Track"
        # 🔴 يُطبع هنا عمداً: البروفةُ تخرج **قبل** بناء الجسم، فلولا هذا السطر لما
        #   كان للإصلاح ضابطٌ يُقاس بلا فعلٍ حقيقيّ على Play.
        Banner "  - Release name: $promoteName   (source: $(if($ReleaseName){'-ReleaseName (manual)'}else{'Play'}) - NOT build.gradle.kts)"
        Banner "  - Release notes: $(if($NoReleaseNotes){'suppressed (-NoReleaseNotes)'}elseif($promoteNotes){'carried over from Play'}else{'none held by Play'})"
    } else {
        Banner "  [DRY RUN] Would upload:"
        Banner "  - File: $($aabItem.Name) ($aabMB MB)"
        Banner "  - Track: $Track"
        Banner "  - versionCode: (from AAB)"
    }
    Banner ""
    # لا تُترك مسوّدةٌ معلَّقة بعد معاينةٍ لم ترفع شيئاً.
    try {
        Invoke-RestMethod -Uri "$API_BASE/applications/$PACKAGE_NAME/edits/$editId" -Method Delete -Headers $authHeader | Out-Null
        OK "Edit session discarded (dry run leaves no draft)"
    } catch {
        WARN "Could not discard edit $editId : $($_.Exception.Message.Split("`n")[0])"
    }
    OK "Dry run complete. Run without -DryRun to actually upload."
    exit 0
}

# ════════════════════════════════════════════════════════════════════════════
# STEP 5: Upload AAB
# ════════════════════════════════════════════════════════════════════════════
if ($Promote) {

STEP "Skipping upload (promote mode)"
$uploadedCode = $VersionCode
OK "Using versionCode already on Play: $uploadedCode"

} else {

STEP "Uploading AAB ($aabMB MB) to Google Play..."
INFO "This may take 1-3 minutes..."

$uploadUrl  = "$UPLOAD_BASE/applications/$PACKAGE_NAME/edits/$editId/bundles"
$aabBytes   = [System.IO.File]::ReadAllBytes($AAB_PATH)
$uploadHeaders = @{
    Authorization   = "Bearer $accessToken"
    "Content-Type"  = "application/octet-stream"
    "X-Goog-Upload-Protocol" = "raw"
}

$uploadResp = Invoke-RestMethod -Uri $uploadUrl `
    -Method Post -Headers $uploadHeaders -Body $aabBytes -TimeoutSec 300

$uploadedCode = $uploadResp.versionCode
OK "AAB uploaded successfully"
OK "versionCode: $uploadedCode"

}   # نهاية else — الرفع

# ════════════════════════════════════════════════════════════════════════════
# STEP 6: Assign to track
# ════════════════════════════════════════════════════════════════════════════
STEP "Assigning to track: $Track..."

# Read versionName from build.gradle.kts
# 🟢 **صحيحٌ في وضع الرفع وحده** — هناك تُبنى الحزمةُ من هذه الشجرة الآن، فالشجرةُ
#   شاهدٌ عليها. 🔴 **وباطلٌ في وضع الترقية** — تلك حزمةٌ بُنيت في الماضي، واسمُها
#   يُقرأ من Play في الكتلة أعلاه. (الحدُّ كان مكتوباً هنا تنبيهاً ثمّ صار مُنفَّذاً
#   2026-09-07: «حدٌّ يُنبَّه عليه» يُنسى، و«حدٌّ يُفرَض» لا يُنسى.)
$bk = Get-Content "$PROJ\app\build.gradle.kts" -Raw
$versionName = "2.2"
if ($bk -match 'versionName\s*=\s*"([\d\.]+)"') { $versionName = $Matches[1] }

# ── ملاحظاتُ الإصدار: ملفٌّ لكلّ رقم، ولا نصَّ افتراضيٌّ إطلاقاً ──────────────
# 🔴 **العلّةُ التي أُصلحت 2026-09-07 — وهي توأمُ عيبِ `-Promote`:** كان هنا نصٌّ
#   **مجمَّدٌ** يَعِد بـ«نطاقٍ أسرع» و«إشعاراتٍ فورية» و«الوضع الليلي» — وتلك تغييراتُ
#   `vc31`. ويُرسَل كما هو مع **أيّ** إصدارٍ لاحق، فيقرأ مستخدمُك وعوداً ليست له.
#   ⚠️ والفارقُ عن `versionName` جوهريّ: ذاك تقرؤه الشجرةُ **من نفسها فيصدق**،
#      وهذا **سلسلةٌ محفورةٌ لا تعرف أيَّ إصدارٍ تصف** ⇒ الشجرةُ ليست شاهداً عليها.
# 🔴 **ولا هبوطَ إلى نصٍّ عامّ عند الغياب** — «تحسيناتٌ عامّة» تُخفي أنك نسيت الكتابة،
#   وهي بالضبط الحالةُ التي أنتجت العطل. الغيابُ **يُوقف الرفع** ويُسمّى الملفُّ المطلوب.
$notesFile    = Join-Path $PROJ "_docs\release-notes\vc$uploadedCode.json"
$releaseNotes = $null
if (-not $Promote -and -not $NoReleaseNotes) {
    if (-not (Test-Path $notesFile)) {
        ERR "لا ملفَّ ملاحظاتٍ لهذا الإصدار: _docs\release-notes\vc$uploadedCode.json"
        ERR "اكتبه أوّلاً — ولا نصَّ افتراضيٌّ هنا عمداً: النصُّ العامُّ يُخفي أنك نسيتَ."
        ERR 'الشكل:  [ { "language": "ar", "text": "..." }, { "language": "en-US", "text": "..." } ]'
        ERR "أو مرّر -NoReleaseNotes لرفعٍ بلا ملاحظاتٍ عن قصد."
        Discard-Edit; exit 1
    }
    $releaseNotes = @(Get-Content $notesFile -Raw -Encoding UTF8 | ConvertFrom-Json |
                      ForEach-Object { @{ language = $_.language; text = $_.text } })
    OK "ملاحظاتُ الإصدار من vc$uploadedCode.json ($($releaseNotes.Count) لغة)"
}

# 🔴 أُصلح 2026-09-05 بعد سقوطٍ صامتٍ **مُستنسَخ 3/3** — والشكلُ أدناه هو الذي
#   ثبت عملُه بالأثر (‏vc34 صار حيّاً على `internal` بعد استعماله).
#   الفرقان عن الشكل الساقط:
#     ① 🔴 **حقل `track` كان غائباً من الجسم.** وثائقُ `Edits.tracks.update` تنصّ
#        على أن الجسمَ **مورد Track** يحمل `track` و`releases` معاً — والمقطعُ في
#        الـURL لا يُغني عنه.
#     ② `versionCodes` **سلسلةٌ لا عدد**: الحقلُ `int64`، وترميزُه في JSON سلسلةٌ
#        بحسب اصطلاح Google. ⚠️ **وهذا يعكس فرضاً سابقاً** في بند
#        `play-upload-silently-dropped-root-unmeasured` (كان يتّهم السلسلةَ) —
#        فالمسارُ الناجح مرّرها **سلسلةً** والساقطُ مرّرها **عدداً**.
#
# 🔴 **والجذرُ لم يُعزَل، ويُقال صراحةً بدل الإيهام:** التجربةُ الناجحة غيّرت
#   **ثلاثةَ متغيّرات معاً** (‏`track` · نوعُ `versionCodes` · وغيابُ `releaseNotes`)
#   ⇒ تُثبت أن أحدَها السبب **ولا تقول أيَّها**. والعزلُ يحتاج ثلاثَ رفعاتٍ
#   تُغيّر واحداً في كلٍّ — أي ثلاثةَ أرقامٍ محروقة. البندُ يبقى مفتوحاً.
#
# 🔴 **وعزلٌ جرى 2026-09-06 فنقضَ فرضَه — ويُكتب لأن نتيجته السالبة هي الفائدة:**
#   محاولتان متتاليتان لترقية `vc34` إلى `production` بهذا الشكل نفسِه:
#     ① **مع** `releaseNotes` ⇒ سقط صامتاً.
#     ② **بلا** `releaseNotes` (‏`-NoReleaseNotes`) ⇒ **سقط أيضاً**.
#   وكلتاهما طبعت «‏Assigned» ثمّ «‏committed»، ثمّ `production` ما زال `31` بقراءةٍ
#   مستقلّةٍ بعد ٦٠ث و٧٥ث. ⇒ **`releaseNotes` بريئة** — والفرضُ الذي بُنيت عليه هذه
#   الراية **منقوضٌ لا مؤجَّل**.
#
# 🎯 **وعزلٌ مقيسٌ 2026-09-07 قلبَ التشخيصَ المكتوبَ هنا — يُصحَّح ولا يُحذف:**
#   جُرّبت الترقيةُ على `alpha` (بروفةٌ ثمّ تنفيذٌ فعليّ) فسقطت صامتةً كما على
#   `production`. ⇒ **الحدُّ الفاصل `internal` مقابل ما سواه، لا `production` وحده.**
#
#     | المسار      | الترقيةُ بالـAPI | العدد |
#     |-------------|------------------|-------|
#     | internal    | 🟢 نجحت          | ١/١   |
#     | alpha       | 🔴 سقطت صامتةً   | ١/١   |
#     | production  | 🔴 سقطت صامتةً   | ٢/٢   |
#
#   🔴 **ومرشَّحان كانا مكتوبَين هنا سقطا كلاهما:**
#     · **المسوّدةُ الفارغة** — لا مسوّدةَ على `alpha`، وسقط سواءً.
#     · **النشرُ المُدار** — طابق الجدولَ ٤/٤ فاتُّهم ساعةً، ثمّ **برئ بقياسٍ من سطحه
#       الخاصّ**: «التغييرات الجاهزة للنشر» **فارغة**، وسجلُّ الإرسال يقف عند `#20`
#       بلا طلبٍ لاحق. ⇒ لو التُزمت الترقيةُ وحُجبت لظهرت في أحد السطحين.
#   ⇒ **`Edit committed successfully` كذبٌ محض**: التحريرُ لا يصل Play أصلاً، لا
#     «يصل ويُحجَب عن النشر». ولا أثرَ للطلب في أيّ سطحٍ من سطوح Console.
#
# 🎯 **والمرشّحُ الباقي — يطابق الجدولَ ٤/٤ ويفسّر الصمتَ بدل أن يفترضه: أذونُ حساب
#   الخدمة.** Play يفصل أذونَ الإصدار **ثلاثةً مستقلّة**: `internal testing` ·
#   `testing tracks` · `production`. وحسابٌ يملك **الأوّلَ وحده** يُنتج المقيسَ حرفياً.
#   ⚠️ **ولا يُقاس من `androidpublisher`** — بل من Play Console: المستخدمون والأذونات
#      ← حسابُ الخدمة ← أذوناتُ التطبيق ← قسم «الإصدارات». البندُ مفتوحٌ حتى ذلك،
#      **ومرشّحٌ قويٌّ ليس قياساً.**
#   🔴 **ولم يُجرَّب `beta` كضابطٍ فاصل** عمداً: مسارٌ له مختبرون، وتغييرُه فعلٌ
#      خارجيٌّ لم يأذن به المالك — والتشخيصُ لا يبرّر أثراً غيرَ مأذون.
$release = @{
    # 🔴 المصدرُ يتبع الوضع: الترقيةُ من Play (الحزمةُ بُنيت في الماضي)، والرفعُ من
    #   الشجرة (الحزمةُ تُبنى منها الآن). الخلطُ يصف إصداراً بوعودِ إصدارٍ آخر.
    name         = if ($Promote) { $promoteName } else { "v$versionName" }
    versionCodes = @("$uploadedCode")
    status       = "completed"
}
if (-not $NoReleaseNotes) {
    $release.releaseNotes = if ($Promote) { $promoteNotes } else { $releaseNotes }
    if ($null -eq $release.releaseNotes) { $release.Remove("releaseNotes") }
}
$trackBody = @{
    track    = $Track
    releases = @($release)
} | ConvertTo-Json -Depth 5

INFO "Track body: $($trackBody -replace '\s+', ' ')"

$trackUrl  = "$API_BASE/applications/$PACKAGE_NAME/edits/$editId/tracks/$Track"
$trackResp = Invoke-RestMethod -Uri $trackUrl -Method Put -Headers $authHeader -ContentType "application/json" -Body $trackBody
OK "Assigned to track: $Track"
# 🔴 **يُطبع جسمُ الردّ ولا يُهمَل** (أُضيف 2026-09-08): كان يُلقى في متغيّرٍ لا يُقرأ،
#   فبقي «‏Assigned» شهادةً جوفاء أربعَ مرّاتٍ سقطت كلُّها. **وPlay يعيد موردَ Track
#   كما خزّنه** ⇒ إن خلا من الرقم المطلوب فالرفضُ عند الكتابة لا عند الالتزام.
INFO "tracks.update response: $((($trackResp | ConvertTo-Json -Depth 6 -Compress)))"

# ════════════════════════════════════════════════════════════════════════════
# STEP 7: Commit the edit (makes it live/visible)
# ════════════════════════════════════════════════════════════════════════════
STEP "Committing edit (finalizing upload)..."

$commitUri = "$API_BASE/applications/$PACKAGE_NAME/edits/$editId`:commit"
if ($ChangesNotSentForReview) {
    $commitUri += "?changesNotSentForReview=true"
    INFO "Commit with changesNotSentForReview=true  (isolation probe - hypothesis, not a proven fix)"
}
$commitResp = Invoke-RestMethod -Uri $commitUri -Method Post -Headers $authHeader
# 🔴 «‏committed successfully» **كذبٌ مُثبَتٌ بالأثر** حين لا يكون المسارُ `internal`:
#   طُبعت في 4/4 محاولاتٍ سقطت جميعُها. ⇒ **لا تُقرأ شهادةَ نجاح**، والحكمُ من
#   خطوة التحقّق أدناه ومن قراءةٍ مستقلّةٍ بعدها.
OK "Edit commit call returned OK (NOT proof - see verification below)"
INFO "commit response: $((($commitResp | ConvertTo-Json -Depth 6 -Compress)))"

# ════════════════════════════════════════════════════════════════════════════
# STEP 8: Verify upload
# ════════════════════════════════════════════════════════════════════════════
STEP "Verifying upload..."
# 🔴 وُسِّعت من 3 ثوانٍ إلى 20 في 2026-09-05 — **الوجهُ المعاكس للحارس الأخضر:**
#   تحقّقٌ يجري قبل أوانه يُعلن الناجحَ فاشلاً، ويدفع لتكرار فعلٍ **تمّ** — وهو هنا
#   رفعٌ لا رجعةَ فيه يحرق رقماً. ⚠️ ولا يُقرأ هذا تفسيراً لسقوط 3/3: أُعيد القياسُ
#   يدوياً **بعد ٤٥ ثانية** فكان المسارُ ما زال على الرقم القديم ⇒ **السقوط حقيقيّ
#   لا توقيت**. التوسيعُ يسدّ فئةً أخرى، ولا يُنسَب إليه إصلاحُ هذه.
Start-Sleep -Seconds 20

# 🔴 أُعيدت كتابتُها 2026-09-02 بعد فشلٍ صامتٍ مُستنسَخ 2/2: طبع السكربتُ
#   «Edit committed successfully» ثمّ «DEPLOY COMPLETE / Status: Published» وخرج بـ0،
#   و**vc33 لم يكن على Play إطلاقاً** (قِيس بأداةٍ مستقلّة ثلاثَ مرّات). وعلّتان معاً:
#   ① الوجهةُ كانت `/applications/{pkg}/tracks/{track}` — **ليست نقطةَ نهايةٍ صالحة**؛
#      قراءةُ المسارات تلزمها جلسةُ تحرير ⇒ 404 دائماً، أي أن التحقّق **لم يجرِ قطّ**.
#   ② و`catch` كان يبتلع ذلك بـWARN، والملخّصُ يطبع "Published" **بلا شرط** ⇒
#      الحالةُ ليست النتيجة، و«رُفعت» ليست «وصلت».
$verified = $false
try {
    $newJwt   = New-GoogleJwt -email $keyJson.client_email -privateKeyPem $keyJson.private_key -scope $PLAY_SCOPE
    $newToken = (Invoke-RestMethod -Uri $TOKEN_URL -Method Post -ContentType "application/x-www-form-urlencoded" -Body @{
        grant_type = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        assertion  = $newJwt
    }).access_token
    $newAuth  = @{ Authorization = "Bearer $newToken" }

    # جلسةُ قراءةٍ منفصلة — تُحذف في finally فلا تبقى معلّقةً تُربك رفعاً لاحقاً
    $vEdit = $null
    try {
        $vEdit = (Invoke-RestMethod -Method Post -Uri "$API_BASE/applications/$PACKAGE_NAME/edits" `
                    -Headers $newAuth -ContentType "application/json" -Body "{}").id
        $verifyTrack = Invoke-RestMethod -Headers $newAuth `
                        -Uri "$API_BASE/applications/$PACKAGE_NAME/edits/$vEdit/tracks/$Track"
        $allCodes = @($verifyTrack.releases | ForEach-Object { $_.versionCodes }) | ForEach-Object { "$_" }
        if ($allCodes -contains "$uploadedCode") {
            OK "Verified on Play: versionCode $uploadedCode is live on [$Track]"
            $verified = $true
        } else {
            ERR "NOT on Play: [$Track] carries [$($allCodes -join ',')] - expected $uploadedCode"
        }
    } finally {
        if ($vEdit) { try { Invoke-RestMethod -Method Delete -Headers $newAuth `
            -Uri "$API_BASE/applications/$PACKAGE_NAME/edits/$vEdit" | Out-Null } catch {} }
    }
} catch {
    ERR "Verification FAILED to run: $($_.Exception.Message.Split("`n")[0])"
}

# 🔴 لا نجاحَ بلا تحقّق. الخروجُ غيرُ الصفريّ هو ما يمنع تكرار الفشل الصامت.
if (-not $verified) {
    ERR "Upload NOT confirmed on Play. Do not report success."
    ERR "Diagnose before retrying; the version code may or may not be consumed."
    exit 1
}

# Open Play Console
Start-Process "https://play.google.com/console/u/0/developers"

# ════════════════════════════════════════════════════════════════════════════
# FINAL SUMMARY
# ════════════════════════════════════════════════════════════════════════════
Banner ""
Banner "======================================================"
Banner "   DEPLOY COMPLETE"
Banner "======================================================"
Banner "   App     : $PACKAGE_NAME"
Banner "   Version : $versionName (code $uploadedCode)"
Banner "   Track   : $Track"
Banner "   Action  : $(if($Promote){'PROMOTED (existing bundle, no upload)'}else{'UPLOADED'})"
Banner "   Status  : Verified live on this track"
Banner ""

if ($Track -eq "internal") {
    Banner "  NEXT: Test on a real device, then promote to production:"
    Banner ""
    # 🔴 صُحّح 2026-09-06: كان يقول `-Track production` — وهو **يرفع الحزمةَ ثانيةً**
    #   فيصطدم بـ«سبق أن تم استخدام رمز الإصدار». الترقيةُ تلزمها الراية `-Promote`.
    Banner "  pwsh -File .\deploy-to-play.ps1 -Promote -Track production -DryRun"
    Banner "  pwsh -File .\deploy-to-play.ps1 -Promote -Track production"
    Banner ""
    Banner "  OR: Play Console -> Testing -> Internal testing -> Promote release"
}
elseif ($Track -eq "production") {
    Banner "  🔴 NEXT (otherwise half the release never lands):"
    Banner "  Set ANDROID_LATEST_VERSION_CODE_$PACKAGE_NAME = $uploadedCode"
    Banner "  in the 'teacher' Apps Script project properties."
    Banner "  Without it nobody sees the update banner."
}
Banner "======================================================"
Banner ""
