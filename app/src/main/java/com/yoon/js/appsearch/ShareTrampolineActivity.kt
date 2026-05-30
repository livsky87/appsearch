package com.yoon.js.appsearch

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.yoon.js.appsearch.data.share.ShareFlowLogger
import com.yoon.js.appsearch.data.share.ShareIntentParser
import com.yoon.js.appsearch.data.share.ShareProcessResult
import com.yoon.js.appsearch.data.share.ShareProcessor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ShareTrampolineActivity : ComponentActivity() {

    @Inject
    lateinit var shareIntentParser: ShareIntentParser

    @Inject
    lateinit var shareProcessor: ShareProcessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action != Intent.ACTION_SEND) {
            ShareFlowLogger.w("ShareTrampoline", "unexpected action=${intent?.action}")
            finish()
            return
        }

        val payload = shareIntentParser.parse(intent)
        if (payload == null) {
            ShareFlowLogger.w("ShareTrampoline", "empty share payload")
            showToast(getString(R.string.share_toast_failed, "공유 내용이 없습니다"))
            finish()
            return
        }

        ShareFlowLogger.d("ShareTrampoline", "processing share type=${payload.sourceType}")
        lifecycleScope.launch {
            val message = when (val result = shareProcessor.process(payload)) {
                is ShareProcessResult.Success -> getString(R.string.share_toast_success)
                is ShareProcessResult.Failure -> getString(R.string.share_toast_failed, result.message)
            }
            showToast(message)
            finish()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
