package com.proconrers.schoolappyemen

import android.app.Activity
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

/**
 * SchoolJsBridge — جسر JavaScript ↔ أندرويد (window.AndroidApp).
 *
 *   - retry(): يعيد تحميل الرابط الهدف الحقيقي (إصلاح زرّ «إعادة المحاولة»
 *     بدل location.reload الذي كان يعيد صفحة الخطأ نفسها).
 *   - saveBase64(): يحفظ ملفاً وُلِّد داخل المتصفح (blob: مثل تصدير Excel من
 *     SheetJS) إلى مجلد «التنزيلات» العام.
 *
 * @param targetUrl دالة تُرجِع الرابط الذي يجب إعادة تحميله عند retry().
 */
class SchoolJsBridge(
    private val activity: Activity,
    private val webView: WebView,
    private val targetUrl: () -> String
) {

    @JavascriptInterface
    fun retry() {
        activity.runOnUiThread { webView.loadUrl(targetUrl()) }
    }

    @JavascriptInterface
    fun saveBase64(dataUrl: String, fileName: String, mimeType: String) {
        try {
            // dataUrl صيغته: data:<mime>;base64,<DATA> → نأخذ ما بعد الفاصلة
            val base64 = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val name = fileName.ifBlank { "school_download_${System.currentTimeMillis()}" }
            val mime = mimeType.ifBlank { "application/octet-stream" }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = activity.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("MediaStore insert failed")
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, name)).use { it.write(bytes) }
            }
            toast("تم حفظ الملف في «التنزيلات»: $name")
        } catch (e: Exception) {
            Log.e("SchoolJsBridge", "saveBase64 failed", e)
            toast("تعذّر حفظ الملف")
        }
    }

    private fun toast(msg: String) {
        activity.runOnUiThread { Toast.makeText(activity, msg, Toast.LENGTH_LONG).show() }
    }
}
