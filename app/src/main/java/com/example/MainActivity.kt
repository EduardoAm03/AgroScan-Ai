package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AgroRepository
import com.example.data.AppDatabase
import com.example.ui.AgroScanApp
import com.example.ui.ScanViewModel
import com.example.ui.ScanViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        // Instantiate Database, Repository, and ViewModel within the Jetpack Compose Context safely
        val context = LocalContext.current.applicationContext
        val database = AppDatabase.getDatabase(context)
        val repository = AgroRepository(database.diagnosisDao(), database.portfolioDao())
        
        val viewModel: ScanViewModel = viewModel(
            factory = ScanViewModelFactory(
                application = context as Application,
                repository = repository
            )
        )
        
        Surface {
          AgroScanApp(viewModel = viewModel)
        }
      }
    }
  }
}
