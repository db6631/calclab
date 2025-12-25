package kr.calclab.app

import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.kakao.sdk.user.UserApiClient

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // 배너는 코드로 생성(중요: setAdSize는 딱 1번만)
    private var bannerAdView: AdView? = null

    // 인셋 listener는 1번만 걸면 됨
    private var insetsApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupWebView()
        setupAdMobAdaptiveBanner()

        // 로그인 페이지
        webView.loadUrl("https://calclab.kr/login/?app=1")
    }

    private fun setupWebView() {
        webView = findViewById(R.id.calclabWebView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // 딥링크로 들어오면 네이티브 카카오 로그인 실행
                if (url.startsWith("calclab://kakao-login")) {
                    startKakaoNativeLogin()
                    return true
                }
                return false
            }
        }
    }

    private fun setupAdMobAdaptiveBanner() {
        MobileAds.initialize(this) {}

        val bannerContainer = findViewById<FrameLayout>(R.id.bannerContainer)

        // ✅ 시스템바(제스처/3버튼) 높이만큼 아래 padding 자동 적용
        // (배너가 시스템 윈도우 영역에 "박히는" 문제 해결)
        if (!insetsApplied) {
            ViewCompat.setOnApplyWindowInsetsListener(bannerContainer) { v, insets ->
                val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                val extra = dpToPx(6) // 살짝 띄우기(취향: 4~10dp)
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bottomInset + extra)
                insets
            }
            insetsApplied = true
        }

        // ✅ 컨테이너는 광고 로드되기 전까지 숨김(흰 여백 방지)
        bannerContainer.visibility = FrameLayout.GONE

        // 혹시 재호출되면 중복 제거
        bannerContainer.removeAllViews()
        bannerAdView?.destroy()
        bannerAdView = null

        // ✅ Adaptive 배너 사이즈 계산 (가로폭 기준)
        // 가장 안전하게 "화면 폭" 기준으로 계산
        val metrics: DisplayMetrics = resources.displayMetrics
        val adWidthDp = (metrics.widthPixels / metrics.density).toInt()
        val adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidthDp)

        // ✅ AdView를 코드로 생성 (중요: setAdSize 1회)
        val adView = AdView(this).apply {
            adUnitId = "ca-app-pub-7013375748998728/4437911137"
            setAdSize(adaptiveSize)

            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    // 액티비티가 이미 종료 중이면 표시하지 않음
                    if (isFinishing || (Build.VERSION.SDK_INT >= 17 && isDestroyed)) return

                    bannerContainer.visibility = FrameLayout.VISIBLE
                    Log.i("ADMOB", "Banner loaded: ${adaptiveSize.width}x${adaptiveSize.height} (widthDp=$adWidthDp)")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    bannerContainer.visibility = FrameLayout.GONE
                    Log.e("ADMOB", "Banner failed: ${error.code} / ${error.message}")
                }
            }
        }

        bannerAdView = adView
        bannerContainer.addView(adView)

        // 로드
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun startKakaoNativeLogin() {
        if (UserApiClient.instance.isKakaoTalkLoginAvailable(this)) {
            UserApiClient.instance.loginWithKakaoTalk(this) { token, _ ->
                if (token != null) {
                    val accessToken = token.accessToken
                    // TODO: accessToken 서버로 보내서 워드프레스 로그인 처리
                } else {
                    loginWithKakaoAccountFallback()
                }
            }
        } else {
            loginWithKakaoAccountFallback()
        }
    }

    private fun loginWithKakaoAccountFallback() {
        UserApiClient.instance.loginWithKakaoAccount(this) { token, _ ->
            if (token != null) {
                val accessToken = token.accessToken
                // TODO: accessToken 서버로 보내서 워드프레스 로그인 처리
            }
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        // 배너 정리
        try {
            bannerAdView?.destroy()
            bannerAdView = null
        } catch (_: Exception) {}

        // WebView 정리(크래시 방지 최소 정리)
        try {
            if (::webView.isInitialized) {
                webView.stopLoading()
            }
        } catch (_: Exception) {}

        super.onDestroy()
    }
}
