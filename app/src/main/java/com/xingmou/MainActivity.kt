package com.xingmou

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.xingmou.data.db.QizhiDatabase
import com.xingmou.ui.XingmouApp

class MainActivity : ComponentActivity() {
    private val viewModel: XingmouViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Open the local Room database and trigger verified seed data before the UI starts.
        QizhiDatabase.getInstance(applicationContext)
        setContent {
            XingmouApp(viewModel)
        }
    }
}
