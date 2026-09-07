#!/usr/bin/env node
/**
 * stale-bundle-guard — PostToolUse
 *
 * 🔴 العطلُ الذي وُلد منه، وقع مقيساً 2026-09-07:
 *   حزمةٌ بُنيت 16:19 وكوميتُ رفعِ `versionCode` عند 16:29 ⇒ الحزمةُ تحمل
 *   **كودَ vc35 وختمَ 34/3.1**. والفارقُ عن الصحيحة **بايتان** ⇒ لا الحجمُ ولا
 *   التوقيعُ ولا تاريخُ البناء يفرزها، وكادت تُسلَّم على أنها vc35.
 *
 * ⚠️ والخطرُ ليس نظرياً: `deploy-to-play.ps1` يرفع **مجلدَ البناء** لا الأرشيف
 *   ⇒ حزمةٌ بائتةُ الختم هناك تُرفع كما هي، وترفع Play رقماً محروقاً أو خاطئاً.
 *
 * 🟢 يُنبّه ولا يحجب: تعديلُ رقمِ الإصدار **فعلٌ مشروعٌ تماماً**، والمعطوبُ هو
 *   بقاءُ الحزمة القديمة بعده. وحاجزٌ يمنع المشروعَ يُلتفّ عليه فيسقط عملياً.
 *   (‏قاعدةُ «اقرأ ما حجبتَه لا أنه حُجب» — `~/CLAUDE.md`.)
 *
 * ولا يقرأ إلّا: مدخلَ الأداة · و`mtime` لملفّين. صفرُ شبكةٍ وصفرُ كتابة.
 */
'use strict';

const fs = require('fs');
const path = require('path');

let raw = '';
process.stdin.on('data', (c) => (raw += c));
process.stdin.on('end', () => {
  let input;
  try { input = JSON.parse(raw || '{}'); } catch (e) { process.exit(0); }

  const root = process.env.CLAUDE_PROJECT_DIR || process.cwd();
  const filePath = (input.tool_input && (input.tool_input.file_path || input.tool_input.notebook_path)) || '';
  if (!filePath) process.exit(0);

  // يعني هذا الحارسَ ملفُّ إعدادِ الإصدار وحده
  if (path.basename(filePath) !== 'build.gradle.kts') process.exit(0);

  const gradle = path.join(root, 'app', 'build.gradle.kts');
  const aab = path.join(root, 'app', 'build', 'outputs', 'bundle', 'release', 'app-release.aab');
  if (!fs.existsSync(gradle) || !fs.existsSync(aab)) process.exit(0);

  let gStat, aStat;
  try { gStat = fs.statSync(gradle); aStat = fs.statSync(aab); } catch (e) { process.exit(0); }

  // الحزمةُ أحدثُ من الإعداد ⇒ لا شبهة
  if (aStat.mtimeMs >= gStat.mtimeMs) process.exit(0);

  // 🔴 والدعوى تُقاس من سطحها: `versionName` سلسلةٌ في مانيفست الحزمة، فتُقرأ منها.
  //   الاعتمادُ على `mtime` وحده يُنذر كاذباً عند أيّ تعديلٍ لا يمسّ الرقم.
  let treeName = null;
  try {
    const m = /versionName\s*=\s*"([^"]+)"/.exec(fs.readFileSync(gradle, 'utf8'));
    if (m) treeName = m[1];
  } catch (e) { /* يُعامَل غيرَ مقيس */ }
  if (!treeName) process.exit(0);

  let stamped = false;
  try {
    const buf = fs.readFileSync(aab); // يكفي البحثُ في الحاوية: الاسمُ يظهر نصّاً
    stamped = buf.includes(Buffer.from(treeName, 'latin1'));
  } catch (e) { process.exit(0); }
  if (stamped) process.exit(0);

  const rel = path.relative(root, aab).replace(/\\/g, '/');
  console.error(
    '⚠️  حزمةُ الإصدار في مجلد البناء صارت **بائتةَ الختم**.\n\n' +
    '  الشجرةُ الآن على versionName "' + treeName + '"، والحزمةُ لا تحمله\n' +
    '  ⇒ بُنيت **قبل** هذا التعديل، فهي كودٌ قديمٌ بختمٍ لا يطابق الشجرة.\n\n' +
    '  🔴 و`deploy-to-play.ps1` يرفع **هذا الملفّ بعينه** لا الأرشيف:\n' +
    '     ' + rel + '\n\n' +
    '  العلاج — واحدٌ منهما قبل أيّ رفع:\n' +
    '    .\\gradlew :app:bundleRelease        (أعِد البناء)\n' +
    '    pwsh -File .claude\\skills\\release-preflight\\scripts\\release-preflight.ps1\n'
  );
  process.exit(2); // ⚠️ تنبيهٌ يصل النموذج — لا حجبَ للتعديل (PostToolUse)
});
