package com.androssh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androssh.app.ui.AndroSshApp
import com.androssh.app.viewmodel.AndroSshViewModel
import com.androssh.app.viewmodel.AndroSshViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AndroSshApplication).container
        setContent {
            val viewModel: AndroSshViewModel = viewModel(
                factory = AndroSshViewModelFactory(
                    repository = container.connectionRepository,
                    sshConnectionManager = container.sshConnectionManager,
                ),
            )
            AndroSshApp(viewModel = viewModel)
        }
    }
}
