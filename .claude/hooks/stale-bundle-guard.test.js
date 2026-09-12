'use strict';
/**
 * ضابطُ `stale-bundle-guard` — والسؤالُ الحاكم: **هل يسقط هذا البندُ على الطريقة
 * الخاطئة؟** ما لا يسقط على أيِّ مرحلةٍ سابقة **يوثّق سلوكاً ولا يفرز فئة**.
 *
 * 🔴 «يضيق ولا يغلق» — وقعت على هذا الحارس **ثلاثَ مرّاتٍ متتالية**، وكلُّ مرحلةٍ
 *   اجتازت ضوابطَ سابقتها كلَّها:
 *     ① مسحُ بايتات الحزمة الخام   — مطابقةٌ على ناتج `deflate` ⇒ مصادفة
 *     ② `includes` على المانيفست    — ٣٫٣ م.ب ⇒ ٢٠ ك.ب: **أضيقُ ومن الفئة نفسِها**
 *     ③ اشتراطُ الطولِ المُعلَن      — يقبل قيمةَ **حقلٍ آخر** تساوي الختم
 *     ④ التساوي بقيمة الحقل         — **خارجَ الفئة**، وهو القائم
 *
 * 🔴 **والضابطُ الأحمرُ (انتهاكٌ مُدخَلٌ عمداً) لا يفرز أبداً: تنجح فيه المراحلُ كلُّها.**
 * 🟢 **الفرزُ في ضابطٍ أخضرَ مبنيٍّ على الفئة الجديدة تحديداً — ولكلِّ تضييقٍ ضابطُه:**
 *     القسمُ ⓐ يُسقط ② و③   ·   والبندُ ③ في ⓓ يُسقط ①
 *
 * التشغيل:  node .claude/hooks/stale-bundle-guard.test.js
 */

const fs = require('fs');
const os = require('os');
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

// ═══════════════════════════════════════════════════════════════════════════
// ⓐ الفرزُ الحقيقيّ — على مانيفستٍ مصنوع · **يعمل دائماً ولا يقف على وجود حزمة**
// ═══════════════════════════════════════════════════════════════════════════
// 🔴 وموضعُه **قبل** حارسِ وجود الحزمة عمداً: هو مستقلٌّ عنها، ووقوعُه خلفَه كان
//   يعني أن جوهرَ الضابط **لا يعمل ولو مرّةً** في أيّ استنساخٍ طازج (‏`.aab`
//   مُستبعدةٌ من git) — **بلا إشارةٍ أنه لم يُنفَّذ**. وهي فئةُ الفشل الصامت نفسُها.
//
// المصفوفةُ الفارزة: حقلُ المانيفست `"2.0"` · وسلسلةُ `"1.0"` في موضعٍ آخر منه ·
// والشجرةُ على `"1.0"`:
//     ② `includes`            ⇒ true  🔴 صمتٌ كاذب
//     ③ الطولُ المُعلَن        ⇒ true  🔴 صمتٌ كاذب
//     ④ التساوي بقيمة الحقل   ⇒ false ✅ يُنذر
// ⚠️ وسابقةُ `"3.2"` داخل `"3.2.1"` **لا تكشف شيئاً** — تنجح في الأربع معاً.
console.log('ⓐ الفرزُ بين «تساوٍ بقيمة الحقل» و«احتواءٍ» و«طولٍ مُعلَن»:');

function u16(n) { const b = Buffer.alloc(2); b.writeUInt16LE(n); return b; }
function u32(n) { const b = Buffer.alloc(4); b.writeUInt32LE(n); return b; }

/** حاويةُ ZIP صغيرةٌ بمدخلٍ مخزَّنٍ (method 0) يحمل المانيفستَ المصنوع */
function synthAab(mf, tag) {
  const name = Buffer.from('base/manifest/AndroidManifest.xml', 'latin1');
  const lh = Buffer.concat([u32(0x04034b50), u16(20), u16(0), u16(0), u16(0), u16(0),
    u32(0), u32(mf.length), u32(mf.length), u16(name.length), u16(0), name]);
  const local = Buffer.concat([lh, mf]);
  const cd = Buffer.concat([u32(0x02014b50), u16(20), u16(20), u16(0), u16(0), u16(0), u16(0),
    u32(0), u32(mf.length), u32(mf.length), u16(name.length), u16(0), u16(0), u16(0), u16(0),
    u32(0), u32(0), name]);
  const eocd = Buffer.concat([u32(0x06054b50), u16(0), u16(0), u16(1), u16(1),
    u32(cd.length), u32(local.length), u16(0)]);
  // 🔴 ملفٌّ لكلّ حالة: مسارٌ مشترَكٌ يدهس سابقَه فيقيس بندٌ حالةَ غيرِه
  const p = path.join(os.tmpdir(), 'stale-bundle-guard-probe-' + tag + '.zip');
  fs.writeFileSync(p, Buffer.concat([local, cd, eocd]));
  return p;
}

const trap = Buffer.concat([
  Buffer.from('otherAttr=1.0 ', 'latin1'),                   // السلسلةُ الفخّ
  Buffer.from([0x12, 0x0b]), Buffer.from('versionName', 'latin1'),
  Buffer.from([0x1a, 0x03]), Buffer.from('2.0', 'latin1'),   // قيمةُ الحقل الحقيقية
  Buffer.from(' trailing 1.0', 'latin1'),
]);
const trapPath = synthAab(trap, "trap");

check('يستخرج قيمةَ الحقل "2.0" لا السلسلةَ المجاورة', versionNameOf(trapPath), '2.0');
check('شجرةٌ على "1.0" ⇒ لا يساوي ⇒ يُنذر', versionNameOf(trapPath) === '1.0', false);
// 🟢 ضابطٌ يحرس الضابط: بلا هذا يخضرّ ⓐ لأنه لا يفحص شيئاً — صمتُ مِجَسّ لا سلامة
check('والسلسلةُ الفخُّ حاضرةٌ فعلاً (وإلّا لم يفرز الضابط)',
  trap.includes(Buffer.from('1.0', 'latin1')), true);

// 🟢 والقطبُ الأخضرُ يُبنى هنا لا على الحزمة الحقيقية — **لأن طرفيه مستقلّان**:
//   قيمةٌ نصنعها ونعرفها سلفاً، تُقارَن بما استُخرج. وعلى الحزمة الحقيقية يستحيل
//   ذلك: `stamp === stamp` تساوٍ ذاتيٌّ يخضرّ أبداً — **بندٌ لا يفحص شيئاً.**
const okPath = synthAab(Buffer.concat([
  Buffer.from([0x12, 0x0b]), Buffer.from('versionName', 'latin1'),
  Buffer.from([0x1a, 0x03]), Buffer.from('1.0', 'latin1'),
]), "green");
check('القطبُ الأخضر — قيمةٌ معلومةٌ سلفاً تُستخرج كما هي ⇒ يصمت',
  versionNameOf(okPath), '1.0');

// ═══════════════════════════════════════════════════════════════════════════
// ⓑ حدُّ القياس — مدخلٌ معطوبٌ يعيد null لا قيمةً مخترَعة (مستقلٌّ عن الحزمة)
// ═══════════════════════════════════════════════════════════════════════════
console.log('\nⓑ حدُّ القياس — «لم يُقَس» لا تُقرأ «سليم»:');
check('ملفٌّ ليس ZIP', versionNameOf(path.join(ROOT, 'app', 'build.gradle.kts')), null);
check('مسارٌ غيرُ موجود', versionNameOf(path.join(ROOT, 'لا-يوجد.aab')), null);

// ═══════════════════════════════════════════════════════════════════════════
// ⓒ ⓓ على الحزمة الحقيقية — وهذه وحدَها تقف على وجودها
// ═══════════════════════════════════════════════════════════════════════════
if (!fs.existsSync(AAB)) {
  console.log('\n⏭️  لا حزمةَ في مجلد البناء ⇒ الأقسامُ ⓒ ⓓ لم تُنفَّذ (تحتاج أثراً مبنيّاً).');
  console.log('   🟢 والفرزُ ⓐ وحدُّ القياس ⓑ نُفِّذا — وهما ما يُثبت المنهج.');
  console.log('\n' + (fail === 0 ? '🟢 ' : '🔴 ') + 'نجح ' + pass + ' · سقط ' + fail);
  process.exit(fail === 0 ? 0 : 1);
}

const stamp = versionNameOf(AAB);
console.log('\nⓒ الحزمة: ' + path.relative(ROOT, AAB).replace(/\\/g, '/'));
console.log('   الختمُ المقروءُ من المانيفست: ' + JSON.stringify(stamp));

// 🔴 وكان هنا `stamp === stamp` — **تساوٍ ذاتيٌّ يخضرّ أبداً**، حتى لو عاد
//   `null`. أُزيل 2026-09-12: القطبُ الأخضرُ يحتاج طرفين مستقلَّين، وهما
//   لا يتوفّران على الحزمة الحقيقية ⇒ موضعُه ⓐ حيث نصنع القيمةَ ونعرفها.
//   وما يصحّ قياسُه هنا: **أن الختمَ قِيس فعلاً** لا أنه يساوي نفسَه.
console.log('\n   أن الختمَ قِيس فعلاً (لا null ولا سلسلةٌ فارغة):');
check('نوعُه سلسلةٌ غيرُ فارغة', typeof stamp === 'string' && stamp.length > 0, true);

console.log('\n   القطبُ الأحمر — يجب أن ينطق:');
['1.0', '3.0', '3.1', '9.9'].forEach(function (v) {
  check('شجرةٌ على "' + v + '" ≠ ختمِ الحزمة', stamp === v, false);
});

// ═══════════════════════════════════════════════════════════════════════════
// ⓓ الطفرةُ التي تُسقط المرحلةَ ① — مسحَ البايتات الخام
// ═══════════════════════════════════════════════════════════════════════════
// `"2.8"` ختمُ المنشور الأقدم لا ختمُ الشجرة، ويُصادَف في ناتج ضغط الحزمة.
console.log('\nⓓ الفرزُ ضدّ مسح البايتات الخام:');
const rawBytes = fs.readFileSync(AAB);
check('الطريقةُ ① كانت تصمت (ولا ينبغي)',
  rawBytes.includes(Buffer.from('2.8', 'latin1')), true);
check('والقائمةُ تنطق', stamp === '2.8', false);

console.log('\n' + (fail === 0 ? '🟢 ' : '🔴 ') + 'نجح ' + pass + ' · سقط ' + fail);
process.exit(fail === 0 ? 0 : 1);
