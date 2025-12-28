package kr.calclab.app

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.kakao.sdk.user.UserApiClient
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // ===== Banner =====
    private var bannerContainer: FrameLayout? = null
    private var bannerAdView: AdView? = null
    private var bannerSizedOnce = false
    private var bannerRetryCount = 0
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    // ===== Exit Native =====
    private var exitNativeAd: NativeAd? = null
    private var exitDialog: AlertDialog? = null

    // ===== App state =====
    private var isCalculatorMain = false

    // ===== URLs =====
    private val loginUrl = "https://calclab.kr/login/?app=1"
    private val afterLoginUrl = "https://calclab.kr/계산기-메인/?app=1"
    private val appLoginEndpoint = "https://calclab.kr/app-login"

    // ===== Ad Unit IDs =====
    private val bannerUnitId = "ca-app-pub-7013375748998728/4437911137"
    private val exitNativeUnitId = "ca-app-pub-7013375748998728/9976058712"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.calclabWebView)
        bannerContainer = findViewById(R.id.bannerContainer)

        bannerContainer?.setBackgroundColor(Color.TRANSPARENT)

        // 하단 제스처 영역만큼 배너 패딩
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            bannerContainer?.setPadding(0, 0, 0, sys.bottom)
            insets
        }

        setupWebView()

        // ✅ 배너는 여기서 1번 확실히 로드 시도
        setupAdMobAdaptiveBanner()

        // ✅ 종료 네이티브 미리 로드
        preloadExitNativeAd()

        // ✅ 첫 화면
        webView.loadUrl(loginUrl)

        // ✅ 뒤로가기: 메인에서만 종료 다이얼로그
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val urlNow = try { webView.url ?: "" } catch (_: Exception) { "" }

                val isMainByUrl =
                    urlNow.contains("/계산기-메인/") ||
                            urlNow.contains("/%EA%B3%84%EC%82%B0%EA%B8%B0-%EB%A9%94%EC%9D%B8/") ||
                            urlNow.contains("%EA%B3%84%EC%82%B0%EA%B8%B0-%EB%A9%94%EC%9D%B8")

                val isMain = isCalculatorMain || isMainByUrl

                Log.d("CALCLAB_BACK", "url=$urlNow, isFlag=$isCalculatorMain, isMainByUrl=$isMainByUrl, isMain=$isMain")

                if (isMain) {
                    showExitDialog()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // ✅ 배너가 어떤 이유로든 null이면 복구 시도 (배너 “쭉 안 나옴” 방지)
        if (bannerAdView == null) {
            Log.d("ADMOB_BANNER", "onResume: bannerAdView null -> retry setup")
            setupAdMobAdaptiveBanner()
        }
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("calclab://kakao-login")) {
                    startKakaoNativeLogin()
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                // 웹에서 window.CALCLAB_APP.page = "calculator-main" 세팅하면 더 정확
                webView.evaluateJavascript(
                    "window.CALCLAB_APP && window.CALCLAB_APP.page ? window.CALCLAB_APP.page : ''"
                ) { result ->
                    val page = result.replace("\"", "")
                    isCalculatorMain = (page == "calculator-main")
                    Log.d("CALCLAB_FLAG", "url=$url, pageFlag=$page, isMain=$isCalculatorMain")
                }
            }
        }
    }

    // ===== Kakao Login =====
    private fun startKakaoNativeLogin() {
        val talkAvailable = UserApiClient.instance.isKakaoTalkLoginAvailable(this)

        if (talkAvailable) {
            UserApiClient.instance.loginWithKakaoTalk(this) { token, _ ->
                if (token != null) postAccessTokenToServer(token.accessToken)
                else loginWithKakaoAccountFallback()
            }
        } else {
            loginWithKakaoAccountFallback()
        }
    }

    private fun loginWithKakaoAccountFallback() {
        UserApiClient.instance.loginWithKakaoAccount(this) { token, _ ->
            if (token != null) postAccessTokenToServer(token.accessToken)
            else Toast.makeText(this, "카카오 로그인 취소/실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun postAccessTokenToServer(accessToken: String) {
        try {
            val postData =
                "access_token=${URLEncoder.encode(accessToken, "UTF-8")}" +
                        "&redirect=${URLEncoder.encode(afterLoginUrl, "UTF-8")}"

            webView.postUrl(appLoginEndpoint, postData.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e("KAKAO_LOGIN", "postUrl error", e)
            Toast.makeText(this, "서버 로그인 연동 실패", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== Banner (Adaptive) =====
    private fun setupAdMobAdaptiveBanner() {
        MobileAds.initialize(this)

        val container = bannerContainer ?: return
        container.visibility = View.VISIBLE

        // 정리
        try { bannerAdView?.destroy() } catch (_: Exception) {}
        bannerAdView = null
        bannerSizedOnce = false
        bannerRetryCount = 0

        val adView = AdView(this)
        adView.adUnitId = bannerUnitId

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("ADMOB_BANNER", "Banner loaded ✅")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e("ADMOB_BANNER", "Banner failed ❌ : $adError")
            }
        }

        container.removeAllViews()
        container.addView(adView)
        bannerAdView = adView

        fun tryLoadWhenReady() {
            val widthPx = container.width
            if (widthPx <= 0) {
                if (bannerRetryCount < 12) {
                    bannerRetryCount++
                    container.postDelayed({ tryLoadWhenReady() }, 120)
                } else {
                    Log.e("ADMOB_BANNER", "container width still 0 after retries")
                }
                return
            }

            if (bannerSizedOnce) return

            val dm: DisplayMetrics = resources.displayMetrics
            val adWidth = (widthPx / dm.density).toInt()
            val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)

            Log.d("ADMOB_BANNER", "Request size: adWidth=$adWidth, adSize=$adSize")

            bannerSizedOnce = true
            adView.setAdSize(adSize)
            adView.loadAd(AdRequest.Builder().build())
        }

        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (!bannerSizedOnce) tryLoadWhenReady()
            if (bannerSizedOnce) {
                try { container.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener) } catch (_: Exception) {}
            }
        }

        container.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        container.post { tryLoadWhenReady() }
    }

    // ===== Exit Native preload =====
    private fun preloadExitNativeAd() {
        val loader = AdLoader.Builder(this, exitNativeUnitId)
            .forNativeAd { ad ->
                exitNativeAd?.destroy()
                exitNativeAd = ad
                Log.d("ADMOB_NATIVE", "Exit native loaded ✅")
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("ADMOB_NATIVE", "Exit native failed ❌ : $adError")
                }
            })
            .build()

        loader.loadAd(AdRequest.Builder().build())
    }

    // ===== Exit Dialog =====
    private fun showExitDialog() {
        if (exitDialog?.isShowing == true) return

        val view = layoutInflater.inflate(R.layout.dialog_exit, null)

        view.findViewById<TextView>(R.id.btnCancel).setOnClickListener {
            exitDialog?.dismiss()
        }
        view.findViewById<TextView>(R.id.btnExit).setOnClickListener {
            finish()
        }

        val adContainer = view.findViewById<FrameLayout>(R.id.nativeAdContainer)

        exitNativeAd?.let { ad ->
            val adView = layoutInflater.inflate(
                R.layout.native_ad_exit,
                adContainer,
                false
            ) as NativeAdView

            val mediaView = adView.findViewById<com.google.android.gms.ads.nativead.MediaView>(R.id.adMedia)
            val iconView = adView.findViewById<android.widget.ImageView>(R.id.adIcon)
            val headlineView = adView.findViewById<TextView>(R.id.adHeadline)
            val bodyView = adView.findViewById<TextView>(R.id.adBody)
            val ctaBtn = adView.findViewById<android.widget.Button>(R.id.adCta)

            // 매핑(정석)
            adView.mediaView = mediaView

            adView.headlineView = headlineView
            headlineView.text = ad.headline ?: ""

            if (ad.body.isNullOrBlank()) {
                bodyView.visibility = View.GONE
            } else {
                bodyView.visibility = View.VISIBLE
                adView.bodyView = bodyView
                bodyView.text = ad.body
            }

            if (ad.callToAction.isNullOrBlank()) {
                ctaBtn.visibility = View.GONE
            } else {
                ctaBtn.visibility = View.VISIBLE
                adView.callToActionView = ctaBtn
                ctaBtn.text = ad.callToAction
            }

            val icon = ad.icon
            if (icon?.drawable == null) {
                iconView.visibility = View.GONE
            } else {
                iconView.visibility = View.VISIBLE
                adView.iconView = iconView
                iconView.setImageDrawable(icon.drawable)
            }

            // 마지막에 setNativeAd
            adView.setNativeAd(ad)

            adContainer.removeAllViews()
            adContainer.addView(adView)
        }

        exitDialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        exitDialog?.show()
    }

    override fun onDestroy() {
        try { bannerAdView?.destroy() } catch (_: Exception) {}
        try { exitNativeAd?.destroy() } catch (_: Exception) {}
        try {
            if (::webView.isInitialized) webView.stopLoading()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
