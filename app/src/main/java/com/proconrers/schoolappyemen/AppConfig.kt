package com.proconrers.schoolappyemen

/**
 * AppConfig — single source of truth for all Apps Script deployment URLs.
 *
 * WHY THIS EXISTS:
 * Previously, deployment URLs were hardcoded as partial key fragments in
 * MainActivity.shouldOverrideUrlLoading() and as full strings scattered across
 * three Activity files. If any Apps Script deployment is redeployed (new URL),
 * all three activities must be updated separately — a maintenance trap.
 *
 * HOW TO UPDATE:
 * When an Apps Script deployment is redeployed, update ONLY the corresponding
 * constant below and rebuild. No other files need to change.
 *
 * DEPLOYMENT IDs:
 * These are extracted from the /exec URLs. The full URL pattern is:
 *   https://script.google.com/macros/s/<DEPLOYMENT_ID>/exec
 *
 * To find a new deployment ID after redeployment:
 *   Apps Script editor → Deploy → Manage deployments → copy the Web App URL.
 */
object AppConfig {

    // ─── CMS Platform (loaded in MainActivity) ────────────────────────────────
    // School news, videos, images, schedule management dashboard.
    private const val CMS_DEPLOYMENT_ID =
        "AKfycbzDfGEK6IpChVNl9k8xbt_iv5p6bLOktt-TvEzDp8yBpH3Ga3yNMen_0S2ZyuuvGtKFCA"

    // ─── Teacher Platform (launched in TeacherActivity) ───────────────────────
    // Teacher login, grade entry, student notes, violations, announcements.
    private const val TEACHER_DEPLOYMENT_ID =
        "AKfycbwbiM1NdYlHf4XPpeftVcrJPmcrPJWm7KS2sSL4qtzZDMDtYo4sGdx6T-p8fAIArvND"

    // ─── Student Platform (launched in StudentActivity) ────────────────────────
    // Student/parent portal: grades, fees, messages, news, schedule.
    private const val STUDENT_DEPLOYMENT_ID =
        "AKfycbz6wFJBq6RUg7buXM5LIGfEa4eVXZguPeIyrkg-T-kbOUhWlJMypO3Ame6lmcHzdcwq"

    // ─── Base URL template ────────────────────────────────────────────────────
    private const val APPS_SCRIPT_BASE = "https://script.google.com/macros/s/%s/exec"

    // ─── Full deployment URLs (use these throughout the app) ─────────────────
    val CMS_URL: String get() = APPS_SCRIPT_BASE.format(CMS_DEPLOYMENT_ID)
    val TEACHER_URL: String get() = APPS_SCRIPT_BASE.format(TEACHER_DEPLOYMENT_ID)
    val STUDENT_URL: String get() = APPS_SCRIPT_BASE.format(STUDENT_DEPLOYMENT_ID)

    // ─── URL routing helpers (used in MainActivity.shouldOverrideUrlLoading) ──

    /**
     * Returns true if [url] belongs to the Teacher platform deployment.
     * Matches on the deployment ID fragment, not the full URL, to handle
     * intermediate redirect URLs that Apps Script generates (/macros/r/, etc.).
     */
    fun isTeacherUrl(url: String): Boolean =
        url.contains(TEACHER_DEPLOYMENT_ID, ignoreCase = true)

    /**
     * Returns true if [url] belongs to the Student platform deployment.
     */
    fun isStudentUrl(url: String): Boolean =
        url.contains(STUDENT_DEPLOYMENT_ID, ignoreCase = true)

    /**
     * Returns true if [url] belongs to any of the three known deployments.
     * Useful for deciding whether a URL should stay inside the app's WebView.
     */
    fun isKnownDeployment(url: String): Boolean =
        isTeacherUrl(url) || isStudentUrl(url) || url.contains(CMS_DEPLOYMENT_ID)

    // ─── Trusted SSL domains ──────────────────────────────────────────────────
    // All Google domains that are allowed to proceed past SSL errors in WebView.
    // Used by MainActivity, TeacherActivity, and StudentActivity.
    val trustedSslDomains: List<String> = listOf(
        "google.com",
        "script.google.com",
        "script.googleusercontent.com",
        "googleusercontent.com",
        "googleapis.com",
        "gstatic.com",
        "docs.google.com",
        "drive.google.com",
        "accounts.google.com"
    )

    /**
     * Returns true if [url] matches any domain in [trustedSslDomains].
     * Used in onReceivedSslError handlers across all three WebView activities.
     */
    fun isTrustedSslDomain(url: String): Boolean =
        trustedSslDomains.any { domain -> url.contains(domain, ignoreCase = true) }
}