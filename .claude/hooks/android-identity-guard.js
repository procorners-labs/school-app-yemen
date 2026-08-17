#!/usr/bin/env node
/* eslint-disable */
'use strict';
// ─────────────────────────────────────────────────────────────────────────────
// android-identity-guard.js — يمنع مسّ هوية تطبيق أندرويد أو أسرار توقيعه.
//
// **الحادثة التي يقفلها (2026-08-11، موثّقة في**
// `SchoolApp-gas/_docs/2026-08-11-دفعة-دمج-ونشر-وتصحيح-تسمية-الاندرويد.md`):
// كوميتُ إعادة تسمية كتب في `CLAUDE.md` قاعدةَ «‏`applicationId` لا يُمَسّ أبداً» وخالفها
// **في المرور نفسه** بـ٤٥ مرجعاً عبر سبعة ملفات — مسار الحزمة في سكربتات الاختبار،
// و`$PACKAGE_NAME` في `deploy-to-play.ps1` (اسم الحزمة المرفوع إلى Play)، واسم ملف المفتاح
// وalias‏ه في سكربتات التوقيع الثلاثة. **ولم يحمرّ شيء** — لا فحص آلي هناك يقارن النصّ بالقرص.
//
// **الأثر لو مرّ:** Firebase مُسجَّل على `applicationId` (‏`app/google-services.json`)،
// وPlay يربط التحديثات به. تغييره ⇒ التطبيق المنشور يفقد تحديثاته بلا رجعة، وFCM يصمت.
//
// **المعيار — مشتقّ لا مثبَّت:** المعرّف المحمي يُقرأ **وقت التشغيل** من
// `<module>/build.gradle.kts` الخاص بالمشروع الذي يُلمَس. فلا قائمة أسماء هنا تتقادم،
// ويعمل الحارس على أي مشروع أندرويد يُضاف لاحقاً بلا تعديل سطر.
//
// **آلية الكشف:** عدّ ورود المعرّف. إن نقص عدده بين `old_string` و`new_string` (أو بين
// القرص و`content`) فالتعديل يُزيل/يبدّل مرجعاً للهوية ⇒ حظر. هذا بالضبط شكل حادثة الـ٤٥.
//
// **ثلاث فئات:**
//   1. أسرار مطلقة (`*.jks` · `keystore.properties` · `google-services.json` · `service-account-key.json`) ⇒ حظر أي كتابة.
//   2. هوية (‏`applicationId`/`namespace`) ⇒ حظر إن نقص عدد ورودها.
//   3. توقيع (‏`keyAlias`/`storeFile`) ⇒ حظر إن تغيّرت قيمتها.
//
// الحدث: `PreToolUse` (‏`Edit|Write|NotebookEdit`) — الرمز 2 يمنع الأداة فعلاً.
// أي شكّ أو تعذّر قياس ⇒ `exit 0` (لا يحجب ما لم يُثبِت). حارسٌ يحجب بالظنّ يُعطَّل خلال يوم.
//
// **لماذا نسختان لا نسخة واحدة مشتركة:** `C:\Users\osama` مستودع git **بلا ريموت** ⇒ ما يعيش
// فيه بلا نسخة احتياطية سحابية إطلاقاً. ولا يصحّ أن يشير «يمن سكولز» إلى ملف داخل مستودع
// «الإبداع» — فهما شقيقان منفصلا الدورة لا نسختان، والاقتران بينهما يخلق تبعية لا وجود لها.
// ⇒ نسخة متتبَّعة في `.claude/hooks/` لكل مشروع. **أي تعديل هنا يُطبَّق في المشروعين معاً.**
// ─────────────────────────────────────────────────────────────────────────────
var fs = require('fs');
var path = require('path');

function readStdin() {
  try { return fs.readFileSync(0, 'utf8'); } catch (e) { return ''; }
}

var input = {};
try { input = JSON.parse(readStdin() || '{}'); } catch (e) { process.exit(0); }

var ti = input.tool_input || {};
var target = ti.file_path || ti.notebook_path || '';
if (!target) process.exit(0);

var abs;
try { abs = path.resolve(target); } catch (e) { process.exit(0); }
var norm = abs.replace(/\\/g, '/');
var base = path.basename(norm);

/** يصعد بحثاً عن `gradlew` — إثبات أن هذا مشروع أندرويد. */
function findAndroidRoot(startDir) {
  var dir = startDir;
  for (var i = 0; i < 40; i++) {
    if (fs.existsSync(path.join(dir, 'gradlew')) || fs.existsSync(path.join(dir, 'gradlew.bat'))) return dir;
    var up = path.dirname(dir);
    if (up === dir) return null;
    dir = up;
  }
  return null;
}

var root = findAndroidRoot(path.dirname(abs));
if (!root) process.exit(0);                       // ليس مشروع أندرويد ⇒ لا شأن لهذا الحارس

function deny(title, lines) {
  process.stderr.write('\n🔴 ' + title + '\n');
  lines.forEach(function (l) { process.stderr.write('   ' + l + '\n'); });
  process.stderr.write('\n   المرجع: `C:\\Users\\osama\\CLAUDE.md` §«خريطة العلاقات» — رابعاً.\n');
  process.stderr.write('   إن كان هذا مقصوداً فعلاً، عطّل الهوك صراحةً في إعداد المشروع وسجّل السبب.\n\n');
  process.exit(2);
}

// ═══════════════════════════════════════════════════════════════════════════
//  الفئة 1 — أسرار مطلقة: لا كتابة عليها من جلسة آلية بحال
// ═══════════════════════════════════════════════════════════════════════════
var ABSOLUTE = ['keystore.properties', 'google-services.json', 'service-account-key.json'];
if (ABSOLUTE.indexOf(base) !== -1 || /\.(jks|keystore)$/i.test(base)) {
  deny('ملف هوية/توقيع لا يُكتَب من جلسة آلية: ' + base, [
    'المسار: ' + abs,
    base === 'google-services.json'
      ? 'يُولَّد من وحدة تحكّم Firebase ومربوط بـapplicationId — تحريره يدوياً يكسر FCM صامتاً.'
      : 'أسرار التوقيع تعيش في `Workspace\\Secure\\` وتُحرَّر يدوياً وحدها.',
    'توقيع تطبيق بمفتاح شقيقه يجعل Play يرفض التحديث بلا رجعة.'
  ]);
}

// ═══════════════════════════════════════════════════════════════════════════
//  اشتقاق الهوية المحميّة من القرص — لا قيمة مكتوبة في هذا الملف
// ═══════════════════════════════════════════════════════════════════════════
function findGradleFile(r) {
  var dirs;
  try {
    dirs = fs.readdirSync(r).filter(function (d) {
      try { return fs.statSync(path.join(r, d)).isDirectory(); } catch (e) { return false; }
    });
  } catch (e) { return null; }
  var ordered = ['app', 'mobile', 'application'].filter(function (d) { return dirs.indexOf(d) !== -1; })
    .concat(dirs.filter(function (d) { return ['app', 'mobile', 'application'].indexOf(d) === -1; }));
  for (var i = 0; i < ordered.length; i++) {
    for (var j = 0; j < 2; j++) {
      var f = path.join(r, ordered[i], j === 0 ? 'build.gradle.kts' : 'build.gradle');
      if (fs.existsSync(f)) return f;
    }
  }
  return null;
}

var gradleFile = findGradleFile(root);
if (!gradleFile) process.exit(0);                 // لا وحدة تطبيق ⇒ لا قياس ⇒ لا حجب

var gradleText = '';
try { gradleText = fs.readFileSync(gradleFile, 'utf8'); } catch (e) { process.exit(0); }

function grab(text, key) {
  var m = new RegExp('(?:^|\\n)\\s*' + key + '\\s*(?:=\\s*|\\(\\s*)?["\\\']([^"\\\']+)["\\\']').exec(text);
  return m ? m[1] : null;
}

var appId = grab(gradleText, 'applicationId');
var ns    = grab(gradleText, 'namespace');
var identities = [];
if (appId) identities.push(appId);
if (ns && ns !== appId) identities.push(ns);
if (identities.length === 0) process.exit(0);     // لم يُقَس ⇒ لا حجب

// ═══════════════════════════════════════════════════════════════════════════
//  الفئة 2 — عدّ ورود المعرّف: نقصانه = مرجع هوية أُزيل أو بُدِّل
// ═══════════════════════════════════════════════════════════════════════════
function countOf(hay, needle) {
  if (!hay) return 0;
  var n = 0, i = 0;
  while ((i = hay.indexOf(needle, i)) !== -1) { n++; i += needle.length; }
  return n;
}

var before = null, after = null, how = '';
if (input.tool_name === 'Edit' && typeof ti.old_string === 'string') {
  before = ti.old_string;
  after  = typeof ti.new_string === 'string' ? ti.new_string : '';
  how = 'Edit';
  // `replace_all` يضاعف الأثر لكن النسبة ثابتة ⇒ نفس المقارنة صالحة.
} else if (input.tool_name === 'Write' && typeof ti.content === 'string') {
  try { before = fs.readFileSync(abs, 'utf8'); } catch (e) { before = null; }  // ملف جديد ⇒ لا مقارنة
  after = ti.content;
  how = 'Write';
}

if (before !== null && after !== null) {
  for (var k = 0; k < identities.length; k++) {
    var id = identities[k];
    var b = countOf(before, id), a = countOf(after, id);
    if (b > 0 && a < b) {
      deny('تعديل يُزيل مرجعاً لهوية التطبيق — محظور', [
        'المعرّف المحمي : ' + id,
        'المصدر الحيّ   : ' + gradleFile,
        'الملف المستهدَف: ' + abs,
        'الورود قبل ' + b + ' → بعد ' + a + '   (‏' + how + ')',
        '',
        'Firebase مسجَّل على هذا المعرّف، وPlay يربط التحديثات به.',
        'حادثة 2026-08-11: ٤٥ مرجعاً بُدِّلوا في مرور واحد بلا أي إنذار.'
      ]);
    }
  }
}

// ═══════════════════════════════════════════════════════════════════════════
//  الفئة 3 — قيم التوقيع داخل ملفات gradle/PowerShell
// ═══════════════════════════════════════════════════════════════════════════
if (after !== null && before !== null && /\.(kts|gradle|ps1)$/i.test(base)) {
  ['keyAlias', 'storeFile', 'keyPassword', 'storePassword'].forEach(function (key) {
    var vb = grab(before, key), va = grab(after, key);
    if (vb && va && vb !== va) {
      deny('تغيير قيمة توقيع — محظور', [
        'المفتاح : ' + key,
        'من     : ' + vb,
        'إلى    : ' + va,
        'الملف  : ' + abs,
        '',
        'مفتاحا «الإبداع» و«يمن سكولز» منفصلان تماماً؛ توقيع أحدهما بالآخر',
        'يجعل Play يرفض التحديث بلا رجعة.'
      ]);
    }
  });
}

process.exit(0);
