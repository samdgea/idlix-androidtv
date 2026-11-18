package com.idlix.player.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.idlix.data.api.ApiClient
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

class TurnstileJsBridge(
    private val mainHandler: Handler,
    private val delivered: AtomicBoolean,
    private val onTokenCallback: (String) -> Unit,
    private val onErrorCallback: (String) -> Unit
) {
    @JavascriptInterface
    fun onToken(token: String?) {
        val clean = token?.trim().orEmpty()
        Log.d("TurnstileVerify", "JS bridge onToken len=${clean.length}")
        if (clean.isNotEmpty() && delivered.compareAndSet(false, true)) {
            mainHandler.post { onTokenCallback(clean) }
        }
    }

    @JavascriptInterface
    fun onError(code: String?) {
        val clean = code?.trim().orEmpty().ifEmpty { "verification_error" }
        Log.w("TurnstileVerify", "JS bridge onError code=$clean")
        mainHandler.post { onErrorCallback(clean) }
    }
}

internal fun buildTurnstileHtml(siteKey: String, action: String): String {
    val quotedKey = JSONObject.quote(siteKey)
    val quotedAction = JSONObject.quote(action.ifBlank { "play" })
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <style>
            html, body {
              margin: 0;
              padding: 0;
              background: #1A1A1A;
              display: flex;
              align-items: center;
              justify-content: center;
              min-height: 100%;
              overflow: hidden;
            }
            #turnstile-container {
              display: flex;
              align-items: center;
              justify-content: center;
            }
          </style>
          <script>
            window.onTurnstileReady = function() {
              try {
                if (!window.turnstile) {
                  IdlixTurnstile.onError("turnstile_unavailable");
                  return;
                }
                var delivered = false;
                var deliverToken = function(token) {
                  if (delivered || !token) return;
                  delivered = true;
                  console.log("IDLIX_TURNSTILE_CALLBACK len=" + token.length);
                  if (window.IdlixTurnstile && typeof window.IdlixTurnstile.onToken === "function") {
                    window.IdlixTurnstile.onToken(token);
                  } else {
                    console.error("IDLIX_TURNSTILE_BRIDGE_MISSING");
                  }
                };
                var widgetId = window.turnstile.render("#turnstile-container", {
                  sitekey: $quotedKey,
                  action: $quotedAction,
                  theme: "dark",
                  appearance: "always",
                  callback: function(token) {
                    deliverToken(token);
                  },
                  "error-callback": function(code) {
                    console.log("IDLIX_TURNSTILE_ERROR=" + String(code || "turnstile_error"));
                    if (window.IdlixTurnstile && typeof window.IdlixTurnstile.onError === "function") {
                      window.IdlixTurnstile.onError(String(code || "turnstile_error"));
                    }
                  },
                  "expired-callback": function() {
                    if (window.IdlixTurnstile && typeof window.IdlixTurnstile.onError === "function") {
                      window.IdlixTurnstile.onError("token_expired");
                    }
                  }
                });
                var pollTimer = setInterval(function() {
                  if (delivered) {
                    clearInterval(pollTimer);
                    return;
                  }
                  try {
                    var resp = window.turnstile && window.turnstile.getResponse ? window.turnstile.getResponse(widgetId) : "";
                    if (resp) {
                      clearInterval(pollTimer);
                      deliverToken(resp);
                    }
                  } catch (_) {}
                }, 400);
              } catch (e) {
                if (window.IdlixTurnstile && typeof window.IdlixTurnstile.onError === "function") {
                  window.IdlixTurnstile.onError(String(e && e.message ? e.message : "render_error"));
                }
              }
            };
          </script>
          <script src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit&onload=onTurnstileReady"
                  async defer
                  onerror="IdlixTurnstile.onError('script_load_failed')"></script>
        </head>
        <body>
          <div id="turnstile-container"></div>
        </body>
        </html>
    """.trimIndent()
}

@SuppressLint("SetJavaScriptEnabled")
internal fun createTurnstileWebView(
    context: Context,
    siteKey: String,
    action: String,
    originUrl: String,
    deviceId: String,
    onTokenReceived: (String) -> Unit,
    onError: (String) -> Unit
): WebView {
    val mainHandler = Handler(Looper.getMainLooper())
    val delivered = AtomicBoolean(false)
    val html = buildTurnstileHtml(siteKey, action)
    val normalizedOrigin = if (originUrl.endsWith("/")) originUrl else "$originUrl/"
    val verifyPageUrl = "${normalizedOrigin}__idlix_verify"

    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.parseColor("#1A1A1A"))
        isFocusable = true
        isFocusableInTouchMode = true

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, true)
        if (deviceId.isNotBlank()) {
            cookieManager.setCookie(normalizedOrigin, "did=$deviceId; Path=/")
            cookieManager.flush()
        }

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val msg = consoleMessage?.message().orEmpty()
                if (msg.contains("IDLIX_")) {
                    Log.d("TurnstileVerify", "Console: $msg")
                }
                return super.onConsoleMessage(consoleMessage)
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (request?.url?.toString() == verifyPageUrl) {
                    return WebResourceResponse(
                        "text/html",
                        "UTF-8",
                        200,
                        "OK",
                        mapOf("Cache-Control" to "no-store"),
                        ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    onError("webview_error_${error?.errorCode ?: -1}")
                }
            }
        }

        addJavascriptInterface(
            TurnstileJsBridge(
                mainHandler = mainHandler,
                delivered = delivered,
                onTokenCallback = onTokenReceived,
                onErrorCallback = onError
            ),
            "IdlixTurnstile"
        )

        loadUrl(verifyPageUrl)
        requestFocus()
    }
}

/**
 * Visible WebView rendering Cloudflare Turnstile on the resolved origin so the user can
 * complete playback verification directly when required by the server.
 */
@Composable
fun TurnstileVerificationView(
    siteKey: String,
    action: String,
    originUrl: String,
    onTokenReceived: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnToken by rememberUpdatedState(onTokenReceived)
    val latestOnError by rememberUpdatedState(onError)

    key(siteKey, action, originUrl) {
        val webViewHolder = remember { arrayOfNulls<WebView>(1) }

        DisposableEffect(Unit) {
            onDispose {
                webViewHolder[0]?.apply {
                    stopLoading()
                    removeJavascriptInterface("IdlixTurnstile")
                    destroy()
                }
                webViewHolder[0] = null
            }
        }

        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                createTurnstileWebView(
                    context = ctx,
                    siteKey = siteKey,
                    action = action,
                    originUrl = originUrl,
                    deviceId = ApiClient.getDeviceId(),
                    onTokenReceived = { token -> latestOnToken(token) },
                    onError = { err -> latestOnError(err) }
                ).also { webViewHolder[0] = it }
            }
        )
    }
}
