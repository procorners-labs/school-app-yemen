package com.proconrers.schoolappyemen

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * DeepLinkActivity — يستقبل روابط `yemenschoolz.com` من خارج التطبيق ويوجّهها للشاشة الصحيحة.
 *
 * لماذا نشاط مستقلّ لا `intent-filter` على `MainActivity`:
 *   - `MainActivity` غير مُصدَّرة (`exported="false"`) وتبقى كذلك — تصديرها يفتح كلّ
 *     شاشات المنصّة لأي تطبيق على الجهاز.
 *   - الرابط الوارد قد يكون لأي من الشاشات الثلاث (‏`/portal` ⇒ الطالب · مسار المعلّم ⇒
 *     المعلّم · الـslug و`?news=` ⇒ الواجهة العامّة)، فيلزم موضع يقرّر قبل الفتح.
 *   - `noHistory` + `Theme.NoDisplay`: لا يظهر ولا يبقى في «الأخيرة» ولا في زرّ الرجوع.
 *
 * 🔴 والمطالبةُ مُقيَّدةٌ بمسار المستأجر منذ vc34 — راجع `AndroidManifest.xml` للمبرّر
 * الكامل. ما يفتح داخل التطبيق هو هذا وحده:
 *   `…/abdaawatmuaz` · `…/abdaawatmuaz?news=<id>` (الاستعلامُ ليس جزءاً من المسار)
 *   `…/teacher/<page>/abdaawatmuaz` · `…/student/<tab>/abdaawatmuaz`
 *
 * ⚠️ وما **لا** يفتح فيه — عمداً، لأنه ليس ملكَ هذه المدرسة أو يحمل هويّتَه في الاستعلام:
 *   `…/` · `…/pricing` · `…/portal[?school=<uuid>]` · `…/teacher/index.html`
 *   `…/home/news.html?news=<id>&school=<uuid>` · `…/<أيّ-مدرسةٍ-أخرى>`
 * وكانت كلُّها مُطالَباً بها في vc32/vc33 (‏`intent-filter` بلا قيدِ مسار) ولم تُشحن قطّ.
 */
class DeepLinkActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppConfig.init(applicationContext)

        val url = intent?.dataString.orEmpty()
        Log.d(TAG, "Deep link: $url")

        val target = if (url.isBlank()) {
            AppConfig.LinkTarget.HOME
        } else {
            AppConfig.routeTargetFor(url)
        }

        // ‏`EXTERNAL` مستحيل عملياً هنا (‏`intent-filter` محصور بمضيف المنصّة)، لكنه يبقى
        // مُعالَجاً: نفتح الواجهة العامّة بدل ترك المستخدم أمام شاشة فارغة — ولا نُعيد
        // إطلاق `ACTION_VIEW` لأنه يعود إلينا حلقةً لا تنتهي.
        val screen = when (target) {
            AppConfig.LinkTarget.TEACHER -> TeacherActivity::class.java
            AppConfig.LinkTarget.STUDENT -> StudentActivity::class.java
            else -> MainActivity::class.java
        }

        startActivity(
            Intent(this, screen).apply {
                if (url.isNotBlank() && target != AppConfig.LinkTarget.EXTERNAL) {
                    putExtra(AppConfig.EXTRA_TARGET_URL, url)
                }
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
        finish()
    }

    private companion object {
        const val TAG = "DeepLink"
    }
}
