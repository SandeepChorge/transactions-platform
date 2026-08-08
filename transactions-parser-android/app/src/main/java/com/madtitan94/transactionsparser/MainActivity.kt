package com.madtitan94.transactionsparser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.madtitan94.transactionsparser.core.designsystem.theme.TransactionsParserTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TransactionsParserTheme {
                AppRoot()
            }
        }
    }
}
