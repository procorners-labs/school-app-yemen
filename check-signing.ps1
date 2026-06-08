# ═══════════════════════════════════════════════════════════════════════════
# check-signing.ps1 — تحقق من صحة التوقيع
# الاستخدام: .\check-signing.ps1 [path-to-aab]
# ═══════════════════════════════════════════════════════════════════════════

param(
    [string]$AabPath = ""
)

$jbr      = "C:\Program Files\Android\Android Studio1\jbr\bin"
$keytool  = Join-Path $jbr "keytool.exe"
$jarsigner= Join-Path $jbr "jarsigner.exe"

# --- التحقق من الـ keystore ---
$keystoreFile = "C:\Users\osama\schoolapp.jks"
Write-Host ""
Write-Host "🔑 فحص Keystore" -ForegroundColor Cyan
Write-Host "───────────────" -ForegroundColor Gray
if (Test-Path $keystoreFile) {
    & $keytool -list -keystore $keystoreFile -storepass 123456 -v 2>&1 |
        Select-String "Alias name|Owner|Serial|Valid from|SHA256"
} else {
    Write-Host "❌ الملف غير موجود: $keystoreFile" -ForegroundColor Red
}

# --- التحقق من الـ AAB ---
if (-not $AabPath) {
    # ابحث عن أحدث AAB على سطح المكتب
    $files = Get-ChildItem "$([Environment]::GetFolderPath('Desktop'))\SchoolApp-v*.aab" |
             Sort-Object LastWriteTime -Descending
    $AabPath = $files[0].FullName
}

if ($AabPath -and (Test-Path $AabPath)) {
    Write-Host ""
    Write-Host "📦 فحص التوقيع على: $(Split-Path $AabPath -Leaf)" -ForegroundColor Cyan
    Write-Host "───────────────────────────────────────────" -ForegroundColor Gray
    & $jarsigner -verify $AabPath -verbose 2>&1 | Select-String "jar verified|FAILED|CN=|SHA"
} else {
    Write-Host "⚠️  لا يوجد AAB للفحص. مرّر المسار كـ argument." -ForegroundColor Yellow
}

Write-Host ""
