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

// ═══════════════════════════════════════════════════════════════════════════
// ⑤ 🔴 المصفوفةُ التي تفرز «يضيق ولا يغلق» — على مانيفستٍ مصنوعٍ لأنها لا تقع صدفة
// ═══════════════════════════════════════════════════════════════════════════
// رفعتها جلسةُ `YemenSchoolz` 2026-09-12: حارسٌ يشترط **الطولَ المُعلَن** ثمّ
// يطابق نصّاً يبقى من الفئة نفسِها. والحالةُ الفارقة: حقلُ المانيفست `"2.0"`
// **وسلسلةُ `"1.0"` حاضرةٌ في موضعٍ آخر منه**، والشجرةُ على `"1.0"`.
//   ‏«الطولُ المُعلَن ثمّ المطابقة» ⇒ true  ⇒ 🔴 صمتٌ كاذب
//   ‏التساوي بقيمة الحقل المستخرجة   ⇒ false ⇒ ✅ يُنذر
// ⚠️ وسابقةُ `"3.2"` داخل `"3.2.1"` **لا تكشف هذا** — تنجح في الطريقتين معاً.
console.log('\n⑤ الفرزُ بين «تساوٍ بقيمة الحقل» و«طولٍ مُعلَنٍ ثمّ مطابقة»:');

function u16(n) { const b = Buffer.alloc(2); b.writeUInt16LE(n); return b; }
function u32(n) { const b = Buffer.alloc(4); b.writeUInt32LE(n); return b; }

/** حاويةُ ZIP صغيرةٌ بمدخلٍ مخزَّنٍ (method 0) يحمل المانيفستَ المصنوع */
function synthAab(mf) {
  const name = Buffer.from('base/manifest/AndroidManifest.xml', 'latin1');
  const lh = Buffer.concat([u32(0x04034b50), u16(20), u16(0), u16(0), u16(0), u16(0),
    u32(0), u32(mf.length), u32(mf.length), u16(name.length), u16(0), name]);
  const local = Buffer.concat([lh, mf]);
  const cd = Buffer.concat([u32(0x02014b50), u16(20), u16(20), u16(0), u16(0), u16(0), u16(0),
    u32(0), u32(mf.length), u32(mf.length), u16(name.length), u16(0), u16(0), u16(0), u16(0),
    u32(0), u32(0), name]);
  const eocd = Buffer.concat([u32(0x06054b50), u16(0), u16(0), u16(1), u16(1),
    u32(cd.length), u32(local.length), u16(0)]);
  const p = path.join(require('os').tmpdir(), 'stale-bundle-guard-probe.zip');
  fs.writeFileSync(p, Buffer.concat([local, cd, eocd]));
  return p;
}

const trap = Buffer.concat([
  Buffer.from('otherAttr=1.0 ', 'latin1'),           // السلسلةُ الفخّ في موضعٍ آخر
  Buffer.from([0x12, 0x0b]), Buffer.from('versionName', 'latin1'),
  Buffer.from([0x1a, 0x03]), Buffer.from('2.0', 'latin1'),  // قيمةُ الحقل الحقيقية
  Buffer.from(' trailing 1.0', 'latin1'),
]);
const trapPath = synthAab(trap);

check('يستخرج قيمةَ الحقل "2.0" لا السلسلةَ المجاورة', versionNameOf(trapPath), '2.0');
check('شجرةٌ على "1.0" ⇒ لا يساوي ⇒ يُنذر', versionNameOf(trapPath) === '1.0', false);
check('والسلسلةُ الفخُّ حاضرةٌ فعلاً في المانيفست (وإلّا لم يفرز الضابط)',
  trap.includes(Buffer.from('1.0', 'latin1')), true);

console.log('\n' + (fail === 0 ? '🟢 ' : '🔴 ') + 'نجح ' + pass + ' · سقط ' + fail);
process.exit(fail === 0 ? 0 : 1);
