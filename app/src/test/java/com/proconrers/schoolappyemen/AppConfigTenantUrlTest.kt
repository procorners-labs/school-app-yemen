package com.proconrers.schoolappyemen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ضابطٌ ثنائيُّ القطب لـ`AppConfig.withEbdaaSchool` — إكمالُ رابطِ منصّةٍ بلا مدرسة.
 *
 * ① **يُكمَل:** مداخلُ `/teacher` و`/student` العارية على مضيفات المنصّة.
 * ② **لا يُمسّ:** ما يحمل مدرستَه (استعلاماً أو مساراً) · المسارات الأخرى · المضيفات الخارجية ·
 *    والمدخلاتُ المعطوبة — وإلّا صار الإصلاحُ يكسر روابطَ كانت تعمل.
 */
class AppConfigTenantUrlTest {

    private val id = AppConfig.EBDAA_SCHOOL_ID

    @Test
    fun adds_school_to_bare_portal_entries() {
        val cases = mapOf(
            "https://yemenschoolz.com/teacher/index.html" to "https://yemenschoolz.com/teacher/index.html?school=$id",
            "https://yemenschoolz.com/student/index.html" to "https://yemenschoolz.com/student/index.html?school=$id",
            "https://yemenschoolz.com/teacher/" to "https://yemenschoolz.com/teacher/?school=$id",
            "https://yemenschoolz.com/student" to "https://yemenschoolz.com/student?school=$id",
            "https://www.yemenschoolz.com/teacher/index.html" to "https://www.yemenschoolz.com/teacher/index.html?school=$id",
            "https://school.procorners.com/teacher/index.html" to "https://school.procorners.com/teacher/index.html?school=$id",
            "https://yemenschoolz.com/teacher/index.html?lang=ar" to "https://yemenschoolz.com/teacher/index.html?lang=ar&school=$id",
            "https://yemenschoolz.com/student/index.html#grades" to "https://yemenschoolz.com/student/index.html?school=$id#grades"
        )
        cases.forEach { (input, expected) -> assertEquals(input, expected, AppConfig.withEbdaaSchool(input)) }
    }

    @Test
    fun leaves_everything_else_untouched() {
        val untouched = listOf(
            "https://yemenschoolz.com/teacher/index.html?school=$id",
            "https://yemenschoolz.com/teacher/index.html?schoolId=other",
            "https://yemenschoolz.com/teacher/abdaawatmuaz",
            "https://yemenschoolz.com/teacher/visits/abdaawatmuaz",
            "https://yemenschoolz.com/home/index.html",
            "https://yemenschoolz.com/home/newsarticle.html?news=798",
            "https://yemenschoolz.com/gas/teacher",
            "https://evil.com/teacher/index.html",
            "https://yemenschoolz.com.evil.com/teacher/index.html",
            "https://evil.com/?x=yemenschoolz.com/teacher/index.html",
            "tel:+967775189922",
            "not a url with spaces",
            ""
        )
        untouched.forEach { assertEquals(it, it, AppConfig.withEbdaaSchool(it)) }
    }
}
