package kr.calclab.app

import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tv = TextView(this).apply {
            text = "Hello Android!"
            textSize = 24f
            gravity = Gravity.CENTER
        }
        setContentView(tv)
    }
}
