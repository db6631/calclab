package kr.calclab.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)

        // ✅ 흰 배경 로딩 화면
        val loadingView = FrameLayout(this).apply {
            setBackgroundColor(0xFFFFFFFF.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // ✅ 배경 없는 계산기 아이콘 + 느린 회전
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_calclab_loading)
            layoutParams = FrameLayout.LayoutParams(300, 300).apply {
                gravity = Gravity.CENTER
            }
            startAnimation(
                AnimationUtils.loadAnimation(
                    this@MainActivity,
                    R.anim.rotate
                )
            )
        }

        loadingView.addView(icon)

        // ✅ WebView
        val webView = WebView(this).apply {
            visibility = View.INVISIBLE
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // 300ms 안에 뜨면 로딩 안 보임
                    postDelayed({
                        loadingView.visibility = View.GONE
                        this@apply.visibility = View.VISIBLE
                    }, 400)
                }
            }
        }

        // ✅ 로그인 페이지 (고정)
        webView.loadUrl("https://calclab.kr/login/?app=1")

        root.addView(webView)
        root.addView(loadingView)

        setContentView(root)
    }
}
