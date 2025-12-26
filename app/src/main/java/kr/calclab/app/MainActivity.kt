package kr.calclab.app

import android.graphics.Color
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.kakao.sdk.user.UserApiClient
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    private var bannerContainer: FrameLayout? = null
    private var bannerAdView: AdView? = null
    private var bannerSizedOnce = false
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    // ✅ 로그인 페이지 (앱용)
    private val loginUrl = "https://calclab.kr/login/?app=1"

    // ✅ 로그인 성공 후 서버가 리다이렉트 시켜야 하는 페이지(앱용)
    // (서버 스니펫의 $redirect도 이걸로 맞춰야 함)
    private val afterLoginUrl = "https://calclab.kr/계산기-메인/?app=1"

    // ✅ WP 쿠키 발급 엔드포인트
    private val appLoginEndpoint = "https://calclab.kr/app-login"

    // ✅ 실제 하단 배너 광고 단위ID
    private val bannerUnitId = "ca-app-pub-7013375748998728/4437911137"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.calclabWebView)
        bannerContainer = findViewById(R.id.bannerContainer)

        // 배너 컨테이너 배경 때문에 "흰 띠" 보이는 경우가 많아서 투명 처리
        bannerContainer?.setBackgroundColor(Color.TRANSPARENT)

        // ✅ 시스템바(네비게이션바) 영역만큼 아래 padding 반영 → “하단 시스템 윈도우에 박힘” 방지
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            bannerContainer?.setPadding(0, 0, 0, sys.bottom)
            insets
        }

        setupWebView()
        setupAdMobAdaptiveBanner()

        // 로그인 페이지 로드
        webView.loadUrl(loginUrl)
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // ✅ 로그인 버튼 딥링크 → 네이티브 카카오 로그인 실행
                if (url.startsWith("calclab://kakao-login")) {
                    startKakaoNativeLogin()
                    return true
                }
                return false
            }
        }
    }

    private fun startKakaoNativeLogin() {
        val talkAvailable = UserApiClient.instance.isKakaoTalkLoginAvailable(this)
        Log.d("KAKAO_LOGIN", "talkAvailable=$talkAvailable")

        if (talkAvailable) {
            UserApiClient.instance.loginWithKakaoTalk(this) { token, error ->
                Log.d("KAKAO_LOGIN", "loginWithKakaoTalk token=${token != null}, error=$error")
                if (token != null) {
                    // ✅ 여기서 “동의항목(스코프)” 체크 후 서버로 POST
                    ensureScopesThenPostToServer(token.accessToken)
                } else {
                    // 카톡 실패 → 계정 로그인으로 fallback
                    loginWithKakaoAccountFallback()
                }
            }
        } else {
            loginWithKakaoAccountFallback()
        }
    }

    private fun loginWithKakaoAccountFallback() {
        UserApiClient.instance.loginWithKakaoAccount(this) { token, error ->
            Log.d("KAKAO_LOGIN", "loginWithKakaoAccount token=${token != null}, error=$error")
            if (token != null) {
                ensureScopesThenPostToServer(token.accessToken)
            } else {
                Toast.makeText(this, "카카오 로그인 취소/실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * ✅ 핵심:
     * - me() 호출해서 email/profile 동의 필요 여부 확인
     * - 필요하면 loginWithNewScopes로 “동의화면” 띄움
     * - 최종 accessToken을 /app-login로 POST
     */
    private fun ensureScopesThenPostToServer(accessToken: String) {
        UserApiClient.instance.me { user, error ->
            if (error != null) {
                Log.e("KAKAO_LOGIN", "me() error=$error")
                // 그래도 일단 진행(서버가 최소 가입 처리하도록)
                postAccessTokenToServer(accessToken)
                return@me
            }

            val scopes = mutableListOf<String>()

            // ✅ 카카오 SDK가 동의 필요 여부를 이렇게 내려줌(없으면 false/null)
            val acc = user?.kakaoAccount
            if (acc?.emailNeedsAgreement == true) scopes += "account_email"
            if (acc?.profileNeedsAgreement == true) {
                scopes += "profile_nickname"
                scopes += "profile_image"
            }

            if (scopes.isEmpty()) {
                // 이미 동의되어 있음 → 바로 서버로
                postAccessTokenToServer(accessToken)
                return@me
            }

            Log.d("KAKAO_LOGIN", "need scopes=$scopes")

            // ✅ 여기서 “동의 화면”이 떠야 정상
            UserApiClient.instance.loginWithNewScopes(this, scopes) { newToken, scopeError ->
                Log.d("KAKAO_LOGIN", "loginWithNewScopes token=${newToken != null}, error=$scopeError")

                if (newToken != null) {
                    postAccessTokenToServer(newToken.accessToken)
                } else {
                    Toast.makeText(this, "동의가 필요합니다(취소됨)", Toast.LENGTH_SHORT).show()
                    // 취소해도 최소 진행 원하면 아래 주석 해제:
                    // postAccessTokenToServer(accessToken)
                }
            }
        }
    }

    /**
     * ✅ WP 서버로 access_token POST → 서버가 WP 쿠키 발급 → afterLoginUrl로 redirect 해야 함
     */
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

    /**
     * ✅ 하단 배너: Adaptive Banner
     * - “adSize는 1번만 set 가능” 크래시 방지 (bannerSizedOnce 플래그)
     */
    private fun setupAdMobAdaptiveBanner() {
        MobileAds.initialize(this)

        val container = bannerContainer ?: return
        container.visibility = View.VISIBLE

        // 이미 만들어져 있으면 정리
        bannerAdView?.destroy()
        bannerAdView = null
        bannerSizedOnce = false

        val adView = AdView(this)
        adView.adUnitId = bannerUnitId
        container.removeAllViews()
        container.addView(adView)
        bannerAdView = adView

        // 레이아웃이 실제 너비를 가진 뒤에 adSize 계산
        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (bannerSizedOnce) return@OnGlobalLayoutListener

            val widthPx = container.width
            if (widthPx <= 0) return@OnGlobalLayoutListener

            bannerSizedOnce = true
            try {
                // listener 제거(중복 호출 방지)
                container.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
            } catch (_: Exception) {}

            val dm: DisplayMetrics = resources.displayMetrics
            val adWidth = (widthPx / dm.density).toInt()
            val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)

            // ✅ adSize는 “한 번만” 세팅해야 함 (이거 때문에 크래시 났던 거)
            adView.setAdSize(adSize)

            adView.adListener = object : AdListener() {
                override fun onAdFailedToLoad(adError: com.google.android.gms.ads.LoadAdError) {
                    Log.e("ADMOB", "Banner failed: $adError")
                }
            }

            adView.loadAd(AdRequest.Builder().build())
        }

        container.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    override fun onDestroy() {
        try {
            bannerAdView?.destroy()
            bannerAdView = null
        } catch (_: Exception) {}

        try {
            if (::webView.isInitialized) {
                webView.stopLoading()
            }
        } catch (_: Exception) {}

        super.onDestroy()
    }
}
