package kr.calclab.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.ComponentActivity

class SplashActivity : ComponentActivity() {

    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val videoView = VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { gravity = Gravity.CENTER }
        }

        root.addView(videoView)
        setContentView(root)

        val uri = Uri.parse("android.resource://$packageName/${R.raw.splash}")
        videoView.setVideoURI(uri)

        fun goNext() {
            if (navigated) return
            navigated = true
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        videoView.setOnPreparedListener { mp ->
            // ✅ FIT(레터박스)로 "잘림 없이" 표시
            val videoW = mp.videoWidth
            val videoH = mp.videoHeight

            val screenW = resources.displayMetrics.widthPixels
            val screenH = resources.displayMetrics.heightPixels

            if (videoW > 0 && videoH > 0) {
                val videoRatio = videoW.toFloat() / videoH
                val screenRatio = screenW.toFloat() / screenH

                val lp = videoView.layoutParams as FrameLayout.LayoutParams
                if (videoRatio > screenRatio) {
                    // 폭 맞춤(위/아래 여백 가능)
                    lp.width = screenW
                    lp.height = (screenW / videoRatio).toInt()
                } else {
                    // 높이 맞춤(좌/우 여백 가능)
                    lp.height = screenH
                    lp.width = (screenH * videoRatio).toInt()
                }
                lp.gravity = Gravity.CENTER
                videoView.layoutParams = lp
            }

            mp.isLooping = false
            videoView.start()
        }

        // ✅ 영상 끝나면 "즉시" 다음 화면으로 이동 (딜레이 0)
        videoView.setOnCompletionListener {
            goNext()
        }

        // ✅ 오류 나도 앱 죽지 말고 다음으로
        videoView.setOnErrorListener { _, _, _ ->
            goNext()
            true
        }
    }
}
