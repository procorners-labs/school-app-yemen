package com.proconrers.schoolappyemen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ضابطٌ معاكسٌ لـ`AppConfig.isTrustedSslDomain` — وهي الدالّةُ التي يقرّر بها
 * `onReceivedSslError` (‏`BaseWebViewActivity` و`MainActivity` كلاهما)
 * **تجاوزَ خطأِ شهادةٍ فعليّ** بـ`handler.proceed()`.
 *
 * 🔴 **ثنائيُّ القطب، ولا يكفي أحدُ قطبيه:**
 *  ① أن اللاحقةَ العارية والانتحالَ **يُحجَبان**.
 *  ② أن المضيفَ المشروعَ **يُقبَل** — وإلّا انقلبت الدالّةُ **سياجاً يكسر التطبيق**،
 *     وهو أسوأُ من الثغرة لأنه يُعلَّم تجاوزُه.
 *
 * يعمل على JVM بلا محاكٍ لأن `isTrustedSslDomain` تستعمل `java.net.URI` لا
 * `android.net.Uri`، ولا تلمس `SharedPreferences` (‏`AppConfig.init` دالّةٌ صريحة
 * لا كتلةُ تهيئة).
 *
 * ⚠️ **وحدُّ هذا الملفّ يُقال بدل الإيهام:** لا يغطّي **التوجيه**
 * (`routeTargetFor`) لأنه يستعمل `android.net.Uri`. وعدمُ انكسار التوجيه مسنودٌ
 * **بالبناء لا بالاختبار**: `GOOGLE_HOSTS` لم تُمسّ، و`GOOGLE_SSL_HOSTS` قائمةٌ
 * ثانيةٌ مستقلّة. ويبقى فحصُ الجهاز في بنده المعلَّق.
 */
class AppConfigSslTrustTest {

    // ① 🔴 لاحقةُ محتوى المستخدمين ليست مضيفاً — وهي علّةُ هذا الملفّ.
    //
    // ‏`*.googleusercontent.com` تُوزَّع على أطرافٍ عشوائيّة، فإدراجُها عاريةً كان
    // يمنح **تجاوزَ خطأِ شهادة** لأيٍّ منها. والمضيفُ المطلوبُ فعلاً واحد: إعادةُ
    // توجيه Apps Script.
    // ⚠️ وسقوطُ هذا الاختبارِ يوماً يعني أن أحداً أعاد اللاحقةَ العارية — والسؤالُ
    //    حينها «مَن وسّع القائمةَ ولماذا؟» لا «كيف أُخضّره؟».
    @Test
    fun rejects_bare_user_content_suffix() {
        val notOurs = listOf(
            "https://evil.googleusercontent.com/",
            "https://lh3.googleusercontent.com/a/photo",
            "https://googleusercontent.com/"
        )
        for (url in notOurs) {
            assertFalse("يجب أن يُحجَب: $url", AppConfig.isTrustedSslDomain(url))
        }
    }

    // الانتحالُ الذي كانت `contains` القديمةُ تمرّره
    @Test
    fun rejects_spoofed_hosts() {
        val spoofed = listOf(
            "https://evil.com/?x=procorners.com",
            "https://procorners.com.evil.tld/login",
            "https://yemenschoolz.com.evil.tld/",
            "https://evil.com/procorners.com/index.html",
            "https://notprocorners.com/",
            "https://evil.com/#google.com",
            "https://google.com.evil.tld/",
            // 🔴 `workers.dev` **لاحقةٌ عامّة** كـ`googleusercontent`: المسارُ
            //    الاحتياطيُّ مُدرَجٌ بمضيفه الكامل، لا باللاحقة.
            "https://attacker.workers.dev/",
            "https://evil.procorners-shop.workers.dev/"
        )
        for (url in spoofed) {
            assertFalse("يجب أن يُحجَب: $url", AppConfig.isTrustedSslDomain(url))
        }
    }

    // مدخلاتٌ معطوبةٌ أو بلا مضيف ⇒ **فشلٌ مغلق** (‏cancel لا proceed)
    @Test
    fun rejects_unparseable_or_hostless() {
        val bad = listOf("", "not a url", "about:blank", "javascript:alert(1)", "/teacher/index.html")
        for (url in bad) {
            assertFalse("يجب أن يُحجَب: $url", AppConfig.isTrustedSslDomain(url))
        }
    }

    // ② السماح — المضيفاتُ التي يعتمد عليها التطبيقُ **المنشور** فعلاً.
    //    هذا القطبُ هو ما يمنع أن ينقلب الإصلاحُ سياجاً.
    @Test
    fun accepts_real_platform_and_google_hosts() {
        val trusted = listOf(
            // الروابطُ الخمسةُ المجمَّدةُ في الحزمة المنشورة
            "https://school.procorners.com/home/index.html",
            "https://school.procorners.com/teacher/index.html",
            "https://school.procorners.com/student/index.html",
            "https://school.procorners.com/cms/index.html",
            "https://school.procorners.com/gas/teacher",
            // النطاقُ الذي تطالب به روابطُ التطبيق
            "https://yemenschoolz.com/teacher/abdaawatmuaz",
            "https://www.yemenschoolz.com/",
            // مسارٌ احتياطيٌّ حيٌّ — لا يُحذف، وبمضيفه الكامل
            "https://school-teacher-proxy.procorners-shop.workers.dev/student/index.html",
            "https://procorners.com/",
            // Google — وإعادةُ توجيه Apps Script بمضيفها بعينه
            "https://script.google.com/macros/s/AKfycb/exec",
            "https://accounts.google.com/signin",
            "https://drive.google.com/file/d/1/view",
            "https://script.googleusercontent.com/echo",
            "https://fonts.gstatic.com/s/font.woff2",
            "https://www.googleapis.com/auth"
        )
        for (url in trusted) {
            assertTrue("يجب أن يُقبَل: $url", AppConfig.isTrustedSslDomain(url))
        }
    }

    // المطابقةُ لا تتأثّر بحالة أحرف المضيف
    @Test
    fun host_match_is_case_insensitive() {
        assertTrue(AppConfig.isTrustedSslDomain("https://SCHOOL.Procorners.COM/home/index.html"))
    }
}
