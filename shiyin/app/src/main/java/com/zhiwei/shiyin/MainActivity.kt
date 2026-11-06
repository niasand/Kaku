package com.zhiwei.shiyin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zhiwei.shiyin.ui.ShiyinNav
import com.zhiwei.shiyin.ui.theme.ShiyinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ShiyinApp).container
        setContent {
            ShiyinTheme {
                ShiyinNav(container)
            }
        }
    }
}
