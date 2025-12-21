package kr.calclab.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 임시: 로그인 화면 대신 바로 Main으로 넘김 (나중에 UI 붙이면 됨)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
