package com.proconrers.schoolappyemen

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * UpdateBanner — بطاقة «تحديث متاح» الأصلية (لا HTML): كانت مبنيّة داخل `MainActivity` وحدها،
 * فأُخرِجت هنا كي تظهر أيضاً في منصّتَي المعلّم والطالب — وهما حيث يجلس المستخدم فعلاً.
 *
 * سلوك مقصود:
 *  - **غير حاجبة** افتراضياً (بطاقة سفلية قابلة للإغلاق) — الإزعاج القسري لا يخدم أحداً.
 *  - **حاجبة** ([showBlocking]) فقط إن كان الإصدار أقدم من الحدّ الأدنى المدعوم فعلاً، أي حين
 *    يكون الاستمرار بلا تحديث معطّلاً وظيفياً لا مجرّد غير مُفضَّل.
 *  - تختفي تلقائياً بعد التحديث: الشرط الوحيد لظهورها هو `latestVersionCode > versionCode`
 *    الحقيقي للحزمة المثبَّتة ([UpdateChecker]) — لا علم يُخزَّن يحتاج تنظيفاً لاحقاً.
 */
object UpdateBanner {

    private const val TAG = "UpdateBanner"
    private const val VIEW_TAG = "update_banner"

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    /**
     * يعرض البطاقة داخل [host] (FrameLayout يجب أن يكون فوق الـWebView في ترتيب الرسم).
     * تكرار الاستدعاء آمن — تُعرَض مرة واحدة فقط.
     */
    fun show(activity: Activity, host: FrameLayout, playUrl: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (host.findViewWithTag<View>(VIEW_TAG) != null) return

        val card = LinearLayout(activity).apply {
            tag = VIEW_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 10), dp(activity, 12))
            // مُنشئ (Orientation, colors) لا `setColors(int[])` — الأخيرة API 29 وminSdk هنا 24.
            background = GradientDrawable(
                GradientDrawable.Orientation.RIGHT_LEFT,
                intArrayOf(
                    ContextCompat.getColor(activity, R.color.brand_primary),
                    ContextCompat.getColor(activity, R.color.ic_launcher_background)
                )
            ).apply { cornerRadius = dp(activity, 16).toFloat() }
            elevation = dp(activity, 6).toFloat()
        }

        val message = TextView(activity).apply {
            text = "✨ نسخة أحدث من التطبيق متاحة — حدِّثها لتحصل على آخر التحسينات والإصلاحات"
            setTextColor(Color.WHITE)
            textSize = 13f
            setLineSpacing(0f, 1.25f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val updateBtn = TextView(activity).apply {
            text = "تحديث"
            setTextColor(ContextCompat.getColor(activity, R.color.brand_primary))
            setTypeface(typeface, Typeface.BOLD)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(activity, 18), dp(activity, 8), dp(activity, 18), dp(activity, 8))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(activity, 20).toFloat()
            }
            setOnClickListener { openStore(activity, playUrl) }
        }

        val closeBtn = TextView(activity).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(activity, 10), dp(activity, 4), dp(activity, 6), dp(activity, 4))
            contentDescription = "إغلاق تنبيه التحديث"
            setOnClickListener { host.removeView(card) }
        }

        card.addView(message)
        card.addView(updateBtn)
        card.addView(closeBtn)

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            setMargins(dp(activity, 12), 0, dp(activity, 12), dp(activity, 12))
        }
        host.addView(card, params)
    }

    /** تحديث إلزامي: حوار غير قابل للإلغاء (يُستخدَم فقط تحت الحدّ الأدنى المدعوم). */
    fun showBlocking(activity: Activity, playUrl: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle("تحديث مطلوب")
            .setMessage(
                "هذه النسخة من التطبيق لم تعد مدعومة. حدِّث التطبيق للمتابعة — " +
                    "بياناتك وحسابك كما هي، ولن تفقد شيئاً."
            )
            .setPositiveButton("تحديث الآن") { _, _ -> openStore(activity, playUrl) }
            .setCancelable(false)
            .show()
    }

    private fun openStore(activity: Activity, playUrl: String) {
        val pkg = activity.packageName
        val candidates = listOf(
            "market://details?id=$pkg",
            playUrl.ifBlank { "https://play.google.com/store/apps/details?id=$pkg" }
        )
        for (url in candidates) {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                return
            } catch (e: Exception) {
                Log.w(TAG, "cannot open $url: ${e.message}")
            }
        }
    }
}
