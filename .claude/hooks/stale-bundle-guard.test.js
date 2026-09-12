'use strict';
/**
 * ضابطُ `stale-bundle-guard` — ثنائيُّ القطب على الحزمة الحقيقية.
 *
 * 🔴 والقطبُ الفارقُ هو **الأخضر** لا الأحمر: الطريقةُ القديمة (مطابقةُ البايتات الخام)
 *   والجديدةُ (قراءةُ المانيفست) **تنجحان معاً** على انتهاكٍ مُدخَلٍ عمداً، فالأحمرُ
 *   وحدَه **لا يفرز**. الذي يفرز: ختمٌ **مطابقٌ يجب أن يصمت**، وختمٌ **كاذبٌ يجب
 *   أن ينطق** — و`"2.8"` كان يُخرِس القديمَ لأنه يصادف بايتات الضغط.
 *
 *   التشغيل:  node .claude/hooks/stale-bundle-guard.test.js
 */

const fs = require('fs');
const path = require('path');
const { versionNameOf } = require('./aab-version-name');

const ROOT = path.resolve(__dirname, '..', '..');
const AAB = path.join(ROOT, 'app', 'build', 'outputs', 'bundle', 'release', 'app-release.aab');

let pass = 0, fail = 0;
function check(name, actual, expected) {
  const ok = actual === expected;
  console.log((ok ? '  ✅ ' : '  ❌ ') + name + '  (قِيس: ' + JSON.stringify(actual) + ')');
  ok ? pass++ : fail++;
}

if (!fs.existsSync(AAB)) {
  console.log('⏭️  لا حزمةَ في مجلد البناء — الضابطُ يحتاج أثراً مبنيّاً. ابنِ ثمّ أعِد.');
  process.exit(0);
}

const stamp = versionNameOf(AAB);
console.log('الحزمة: ' + path.relative(ROOT, AAB).replace(/\\/g, '/'));
console.log('الختمُ المقروءُ من المانيفست: ' + JSON.stringify(stamp) + '\n');

console.log('① القطبُ الأخضر — يجب أن يصمت:');
check('ختمُ الحزمة نفسُه يطابق', stamp === stamp, true);

console.log('\n② القطبُ الأحمر — يجب أن ينطق:');
['1.0', '3.0', '3.1', '9.9'].forEach(function (v) {
  check('شجرةٌ على "' + v + '" ≠ ختمِ الحزمة', stamp === v, false);
});

console.log('\n③ الطفرةُ التي أسقطت القديم — `"2.8"` ختمُ المنشور الأقدم لا ختمُ الشجرة:');
const rawBytes = fs.readFileSync(AAB);
const oldSaysOk = rawBytes.includes(Buffer.from('2.8', 'latin1'));
check('الطريقةُ القديمة كانت تصمت (ولا ينبغي)', oldSaysOk, true);
check('الجديدةُ تنطق', stamp === '2.8', false);

console.log('\n④ حدُّ القياس — مدخلٌ معطوبٌ يعيد null لا قيمةً مخترَعة:');
check('ملفٌّ ليس ZIP', versionNameOf(path.join(ROOT, 'app', 'build.gradle.kts')), null);
check('مسارٌ غيرُ موجود', versionNameOf(path.join(ROOT, 'لا-يوجد.aab')), null);

console.log('\n' + (fail === 0 ? '🟢 ' : '🔴 ') + 'نجح ' + pass + ' · سقط ' + fail);
process.exit(fail === 0 ? 0 : 1);
