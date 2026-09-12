'use strict';
/**
 * aab-version-name — يستخرج `versionName` من حزمة AAB **من مانيفستها**، لا من بايتاتها الخام.
 *
 * 🔴 العطلُ الذي وُلد منه، قِيس 2026-09-12:
 *   كان `stale-bundle-guard` يطابق الختمَ نصّاً على الحزمة كاملةً
 *   (`buf.includes(Buffer.from(treeName))`) بدعوى أن «الاسمَ يظهر نصّاً في الحاوية».
 *   **والدعوى باطلة: مدخلاتُ AAB كلُّها `deflate`** ⇒ المطابقةُ تقع على ناتج الضغط،
 *   فتصادف سلاسلَ لا علاقةَ لها بالختم.
 *
 *   المقيسُ على حزمتنا (٣٬٣٣٤٬٧٣٠ بايت · ختمُها `3.2`) بنفس استدعاء الكود:
 *     "3.2" ⇒ true   ✅ صحيحٌ بالمصادفة
 *     "2.8" ⇒ true   🔴 **كاذب** — وهو ختمُ المنشور الأقدم، فشجرةٌ أُعيدت إليه
 *                        بحزمةٍ بائتة كانت تخضرّ صامتةً
 *     "1.0"/"3.0"/"3.1" ⇒ false
 *
 * 🟢 والعلاجُ قراءةُ المصدر: `base/manifest/AndroidManifest.xml` مفكوكاً — **٢٠ ك.ب**
 *   بدل ٣٫٣ م.ب — وفيه `versionName` حقلٌ في protobuf تُقرأ قيمتُه بعينها:
 *     ‏`\x12\x0b versionName \x1a\x03 3.2`  (اسمُ السمة · ثمّ طولُ القيمة · ثمّ القيمة)
 *   ⇒ **مقارنةُ تساوٍ لا احتواء**، فتصحّ في الاتّجاهين.
 *
 * ولا يستعمل إلّا `zlib` المدمج: صفرُ أداةٍ خارجية · صفرُ شبكةٍ · صفرُ كتابة.
 */

const fs = require('fs');
const zlib = require('zlib');

const MANIFEST = 'base/manifest/AndroidManifest.xml';

/** يقرأ varint من protobuf ⇒ { value, next } أو null */
function readVarint(buf, at) {
  let value = 0, shift = 0, i = at;
  while (i < buf.length && shift <= 28) {
    const b = buf[i++];
    value |= (b & 0x7f) << shift;
    if ((b & 0x80) === 0) return { value: value >>> 0, next: i };
    shift += 7;
  }
  return null;
}

/** يستخرج مدخلَ المانيفست من حاوية ZIP مفكوكاً، أو null */
function inflateManifest(buf) {
  // EOCD: يُمسح من النهاية — التعليقُ أقصاه ٦٥٥٣٥ بايت
  let eocd = -1;
  const floor = Math.max(0, buf.length - (22 + 65535));
  for (let i = buf.length - 22; i >= floor; i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) return null;

  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  let entry = null;

  for (let k = 0; k < count && p + 46 <= buf.length; k++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) return null;
    const nameLen = buf.readUInt16LE(p + 28);
    const extraLen = buf.readUInt16LE(p + 30);
    const cmtLen = buf.readUInt16LE(p + 32);
    if (buf.toString('utf8', p + 46, p + 46 + nameLen) === MANIFEST) {
      entry = {
        method: buf.readUInt16LE(p + 10),
        csize: buf.readUInt32LE(p + 20),
        lho: buf.readUInt32LE(p + 42),
      };
      break;
    }
    p += 46 + nameLen + extraLen + cmtLen;
  }
  if (!entry) return null;

  // الترويسةُ المحلّية تحمل أطوالاً خاصّةً بها — لا تُؤخذ من السجلّ المركزي
  const L = entry.lho;
  if (L + 30 > buf.length || buf.readUInt32LE(L) !== 0x04034b50) return null;
  const start = L + 30 + buf.readUInt16LE(L + 26) + buf.readUInt16LE(L + 28);
  const data = buf.slice(start, start + entry.csize);

  if (entry.method === 0) return data;
  if (entry.method !== 8) return null;
  try { return zlib.inflateRawSync(data); } catch (e) { return null; }
}

/**
 * يعيد `versionName` المختوم في الحزمة، أو null إن تعذّر القياس.
 * 🔴 وnull تعني **«لم يُقَس»** لا «لا ختم» — ومن يستدعيه يُظهر ذلك ولا يبتلعه صمتاً.
 */
function versionNameOf(aabPath) {
  let buf;
  try { buf = fs.readFileSync(aabPath); } catch (e) { return null; }

  const mf = inflateManifest(buf);
  if (!mf) return null;

  const key = Buffer.from('versionName', 'latin1');
  let at = 0;
  while (at >= 0) {
    const i = mf.indexOf(key, at);
    if (i < 0) return null;
    // سمةٌ في protobuf: 0x12 (حقل الاسم · LEN) ثمّ طولُه 11 ثمّ الاسم
    if (i >= 2 && mf[i - 2] === 0x12 && mf[i - 1] === key.length) {
      let j = i + key.length;
      if (mf[j] === 0x1a) {                      // حقلُ القيمة (LEN)
        const len = readVarint(mf, j + 1);
        if (len && len.value > 0 && len.next + len.value <= mf.length) {
          return mf.toString('utf8', len.next, len.next + len.value);
        }
      }
    }
    at = i + 1;
  }
  return null;
}

module.exports = { versionNameOf, inflateManifest };
