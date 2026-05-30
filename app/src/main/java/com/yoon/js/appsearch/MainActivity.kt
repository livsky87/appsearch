package com.yoon.js.appsearch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.yoon.js.appsearch.data.share.ShareCoordinator
import com.yoon.js.appsearch.data.share.ShareIntentParser
import com.yoon.js.appsearch.ui.navigation.AppNavGraph
import com.yoon.js.appsearch.ui.theme.AppsearchTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var shareIntentParser: ShareIntentParser

    @Inject
    lateinit var shareCoordinator: ShareCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
        enableEdgeToEdge()
        setContent {
            AppsearchTheme {
                val navController = rememberNavController()
                AppNavGraph(
                    navController = navController,
                    shareCoordinator = shareCoordinator,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        val payload = intent?.let { shareIntentParser.parse(it) } ?: return
        shareCoordinator.enqueue(payload)
    }
}
