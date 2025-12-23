package kr.calclab.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var bannerAdView: AdView? = null   // ❗ nullable 로 변경해서 크래시 방지

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) WebView 연결
        webView = findViewById(R.id.calclabWebView)

        // 2) WebView 설정
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // User-Agent 에서 ; wv 제거 (앱 모드 유지)
        val ua = settings.userAgentString
        settings.userAgentString = ua.replace("; wv", "")

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()

        // 3) 앱 모드 URL 로드 (?app=1 고정)
        webView.loadUrl("https://calclab.kr/login/?app=1")

        // 4) 광고 초기화/로딩은 예외 나도 앱이 안 죽게 runCatching 으로 감쌈
        runCatching {
            // AdMob SDK 초기화
            MobileAds.initialize(this)

            // 배너 뷰 찾기 (없으면 null)
            bannerAdView = findViewById(R.id.bannerAdView)

            // 테스트 배너 요청
            val adRequest = AdRequest.Builder().build()
            bannerAdView?.loadAd(adRequest)   // null 이면 그냥 무시 → 앱은 계속 동작
        }
    }
}
