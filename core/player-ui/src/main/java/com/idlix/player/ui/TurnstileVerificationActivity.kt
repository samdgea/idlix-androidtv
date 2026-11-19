package com.idlix.player.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.idlix.data.api.ApiClient

/**
 * Isolated Activity hosting the Turnstile verification WebView in a dedicated `:verify` process
 * with hardware acceleration disabled so GPU/EGL quirks on TV/emulator devices cannot crash
 * the main application process.
 */
class TurnstileVerificationActivity : Activity() {

    private var webView: WebView? = null
    private lateinit var webContainer: FrameLayout
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button

    private var siteKey: String = ""
    private var action: String = "play"
    private var originUrl: String = "https://z3.idlixku.com/"
    private var deviceId: String = ""
    private var titleTextValue: String = "Security Verification"
    private var descTextValue: String = "Complete the security check below to start playback."
    private var cancelTextValue: String = "Cancel"
    private var retryTextValue: String = "Try Again"
    private var failedFormatValue: String = "Verification failed (%s). Please try again."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                WebView.setDataDirectorySuffix("verify")
            } catch (_: Throwable) {
            }
        }

        siteKey = intent.getStringExtra(EXTRA_SITE_KEY).orEmpty()
        action = intent.getStringExtra(EXTRA_ACTION).orEmpty().ifBlank { "play" }
        originUrl = intent.getStringExtra(EXTRA_ORIGIN_URL).orEmpty().ifBlank { "https://z3.idlixku.com/" }
        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID).orEmpty().ifBlank { ApiClient.getDeviceId() }
        titleTextValue = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { titleTextValue }
        descTextValue = intent.getStringExtra(EXTRA_DESC).orEmpty().ifBlank { descTextValue }
        cancelTextValue = intent.getStringExtra(EXTRA_CANCEL_LABEL).orEmpty().ifBlank { cancelTextValue }
        retryTextValue = intent.getStringExtra(EXTRA_RETRY_LABEL).orEmpty().ifBlank { retryTextValue }
        failedFormatValue = intent.getStringExtra(EXTRA_FAILED_FORMAT).orEmpty().ifBlank { failedFormatValue }

        if (siteKey.isBlank()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(210, 0, 0, 0))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val cardBg = GradientDrawable().apply {
            setColor(Color.parseColor("#1A1A1A"))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.parseColor("#333333"))
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val cardWidth = minOf(dp(400), (screenWidth * 0.90f).toInt())
        val webWidth = minOf(dp(340), (screenWidth * 0.80f).toInt())

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = cardBg
            setPadding(dp(24), dp(22), dp(24), dp(22))
            layoutParams = FrameLayout.LayoutParams(
                cardWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        val titleView = TextView(this).apply {
            text = titleTextValue
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        card.addView(titleView)

        val descView = TextView(this).apply {
            text = descTextValue
            setTextColor(Color.parseColor("#B3B3B3"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        }
        card.addView(descView)

        webContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(webWidth, dp(96))
        }
        card.addView(webContainer)

        errorText = TextView(this).apply {
            setTextColor(Color.parseColor("#E50914"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, dp(10), 0, 0)
        }
        card.addView(errorText)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }

        retryButton = Button(this).apply {
            text = retryTextValue
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#E50914"))
            visibility = View.GONE
            isFocusable = true
            setOnClickListener {
                errorText.visibility = View.GONE
                visibility = View.GONE
                mountWebView()
            }
        }
        buttonRow.addView(
            retryButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(12)
            }
        )

        val cancelButton = Button(this).apply {
            text = cancelTextValue
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#444444"))
            isFocusable = true
            setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
        buttonRow.addView(cancelButton)

        card.addView(buttonRow)
        root.addView(card)
        setContentView(root)

        mountWebView()
    }

    private fun mountWebView() {
        destroyWebView()
        val created = createTurnstileWebView(
            context = this,
            siteKey = siteKey,
            action = action,
            originUrl = originUrl,
            deviceId = deviceId,
            onTokenReceived = { token ->
                android.util.Log.d("TurnstileVerify", "Activity finishing with token len=${token.length}")
                setResult(RESULT_OK, Intent().putExtra(EXTRA_TOKEN, token))
                finish()
            },
            onError = { err ->
                val formatted = try {
                    String.format(failedFormatValue, err)
                } catch (_: Exception) {
                    "Verification failed ($err)"
                }
                errorText.text = formatted
                errorText.visibility = View.VISIBLE
                retryButton.visibility = View.VISIBLE
            }
        )
        webView = created
        webContainer.addView(created)
    }

    private fun destroyWebView() {
        webContainer.removeAllViews()
        webView?.apply {
            stopLoading()
            removeJavascriptInterface("IdlixTurnstile")
            destroy()
        }
        webView = null
    }

    override fun onDestroy() {
        destroyWebView()
        super.onDestroy()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    companion object {
        const val EXTRA_SITE_KEY = "extra_site_key"
        const val EXTRA_ACTION = "extra_action"
        const val EXTRA_ORIGIN_URL = "extra_origin_url"
        const val EXTRA_DEVICE_ID = "extra_device_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DESC = "extra_desc"
        const val EXTRA_CANCEL_LABEL = "extra_cancel_label"
        const val EXTRA_RETRY_LABEL = "extra_retry_label"
        const val EXTRA_FAILED_FORMAT = "extra_failed_format"
        const val EXTRA_TOKEN = "extra_token"

        fun createIntent(
            context: Context,
            siteKey: String,
            action: String,
            originUrl: String,
            title: String,
            desc: String,
            cancelLabel: String,
            retryLabel: String,
            failedFormat: String
        ): Intent {
            return Intent(context, TurnstileVerificationActivity::class.java).apply {
                putExtra(EXTRA_SITE_KEY, siteKey)
                putExtra(EXTRA_ACTION, action)
                putExtra(EXTRA_ORIGIN_URL, originUrl)
                putExtra(EXTRA_DEVICE_ID, ApiClient.getDeviceId())
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_DESC, desc)
                putExtra(EXTRA_CANCEL_LABEL, cancelLabel)
                putExtra(EXTRA_RETRY_LABEL, retryLabel)
                putExtra(EXTRA_FAILED_FORMAT, failedFormat)
            }
        }
    }
}
