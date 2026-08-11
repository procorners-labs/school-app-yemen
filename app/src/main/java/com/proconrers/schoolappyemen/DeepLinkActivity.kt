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
 * أمثلة على ما صار يفتح داخل التطبيق بدل المتصفّح ابتداءً من vc32:
 *   `…/portal` · `…/portal?school=<uuid>` · `…/abdaawatmuaz` · `…/abdaawatmuaz?news=<id>`
 *   `…/home/newsarticle.html?news=<id>&school=<uuid>` · `…/teacher/index.html` · `…/pricing`
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
