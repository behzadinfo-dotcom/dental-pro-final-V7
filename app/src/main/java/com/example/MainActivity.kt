package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.reminders.ReminderCheckWorker
import com.example.ui.DentalViewModel
import com.example.ui.screens.MainDentalScreen
import com.example.ui.screens.LockScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {
  private val viewModel: DentalViewModel by viewModels()
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Appointment/cheque reminders run on a periodic background worker;
    // scheduling is idempotent (KEEP policy) so this is safe on every launch.
    ReminderCheckWorker.schedule(applicationContext)
    com.example.widget.AppointmentsWidgetProvider.requestUpdate(applicationContext)

    setContent {
      val settings by viewModel.settings.collectAsState()

      val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
      ) { /* no-op: reminders simply won't show if declined */ }

      LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
      }

      MyApplicationTheme(themeId = settings.themeName, darkTheme = settings.isDarkMode, fontScaleIndex = settings.globalFontScaleIndex) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          val isUnlocked by viewModel.isAppUnlocked.collectAsState()
          if (isUnlocked) {
            MainDentalScreen(viewModel = viewModel)
          } else {
            LockScreen(
              viewModel = viewModel,
              onUnlocked = { viewModel.isAppUnlocked.value = true }
            )
          }

        }
      }
    }
  }
}
