package kr.calclab.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var adView: AdView
    private lateinit var bannerContainer: View

    private val firstUrl = "https://calclab.kr/login/?app=1"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        bannerContainer = findViewById(R.id.bannerContainer)

        webView = findViewById(R.id.calclabWebView)
        adView = findViewById(R.id.bannerAdView)

        // ✅ (핵심) 네비게이션 바 안전영역만큼 배너를 위로 올림
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            bannerContainer.setPadding(
                bannerContainer.paddingLeft,
                bannerContainer.paddingTop,
                bannerContainer.paddingRight,
                navBars.bottom
            )
            insets
        }

        // ✅ WebView (v1.1)
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)

        val ua = s.userAgentString ?: ""
        s.userAgentString = ua.replace("; wv", "")

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {}

        if (savedInstanceState == null) webView.loadUrl(firstUrl)
        else webView.restoreState(savedInstanceState)

        // ✅ AdMob 배너
        MobileAds.initialize(this)
        adView.loadAd(AdRequest.Builder().build())

        // ✅ 최신 뒤로가기
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
                else finish()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }
}
