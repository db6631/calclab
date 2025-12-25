package kr.calclab.app

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import com.kakao.sdk.common.KakaoSdk
import java.security.MessageDigest

class CalclabApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // ✅ 카카오 SDK 초기화
        KakaoSdk.init(this, "6795b7aa4ddf12f294cac8b0918b26d2")

        // ✅ KeyHash 출력
        Log.i("KAKAO_KEY_HASH", getKeyHash())
    }

    private fun getKeyHash(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signatures.isNullOrEmpty()) return ""

            val md = MessageDigest.getInstance("SHA")
            signatures.forEach { md.update(it.toByteArray()) }

            Base64.encodeToString(md.digest(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("KAKAO_KEY_HASH", "KeyHash Error", e)
            ""
        }
    }
}
