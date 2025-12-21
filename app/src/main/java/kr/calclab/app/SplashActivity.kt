package kr.calclab.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.activity.ComponentActivity

class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoView = VideoView(this)
        setContentView(videoView)

        val uri = Uri.parse("android.resource://$packageName/${R.raw.splash}")
        videoView.setVideoURI(uri)

        fun goNext() {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        videoView.setOnPreparedListener { mp ->
            mp.isLooping = false
            videoView.start()
        }

        videoView.setOnCompletionListener { goNext() }

        videoView.setOnErrorListener { _, _, _ ->
            goNext()
            true
        }
    }
}
