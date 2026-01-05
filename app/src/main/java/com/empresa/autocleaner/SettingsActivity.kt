package com.empresa.autocleaner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var isAuthenticated by remember { mutableStateOf(false) }

                if (isAuthenticated) {
                    SettingsScreen()
                } else {
                    PasswordScreen { isAuthenticated = true }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // La comprobación de permisos se ha movido a la pantalla de ajustes para
        // no interrumpir el flujo de autenticación.
    }
}

@Composable
fun PasswordScreen(onPasswordCorrect: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    // Contraseña simple. Para una aplicación real, usar un mecanismo de autenticación más seguro.
    val correctPassword = "Luis031466"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Acceso Restringido",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Advertencia: No elimine la aplicación. El acceso está restringido únicamente al área de sistemas.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            if (password == correctPassword) {
                onPasswordCorrect()
            } else {
                Toast.makeText(context, "Contraseña incorrecta", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("Ingresar")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var sliderPosition by remember { mutableFloatStateOf(SettingsManager.getDaysToKeep(context).toFloat()) }
    var frequencyPosition by remember { mutableFloatStateOf(SettingsManager.getExecutionFrequency(context).toFloat()) }

    // Comprueba el permiso cada vez que el usuario vuelve a la pantalla de ajustes.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Toast.makeText(context, "Se necesita permiso para acceder a todos los archivos.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            context.startActivity(intent)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Ajustes de Limpieza Inteligente") }) }) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Text(text = if (sliderPosition.roundToInt() == 0) "¡PRECAUCIÓN! Se eliminarán TODOS los archivos" else "Eliminar archivos con ${sliderPosition.roundToInt()} o más días de antigüedad")
            Spacer(modifier = Modifier.height(8.dp))
            Slider(value = sliderPosition, onValueChange = { sliderPosition = it }, onValueChangeFinished = {
                val days = sliderPosition.roundToInt()
                SettingsManager.saveDaysToKeep(context, days)
                WorkerScheduler.schedule(context.applicationContext)
                Toast.makeText(context, "Ajuste guardado: $days días. La tarea ha sido reprogramada.", Toast.LENGTH_SHORT).show()
            }, valueRange = 0f..30f, steps = 29)
            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Ejecutar limpieza cada ${frequencyPosition.roundToInt()} horas")
            Spacer(modifier = Modifier.height(8.dp))
            Slider(value = frequencyPosition, onValueChange = { frequencyPosition = it }, onValueChangeFinished = {
                val hours = frequencyPosition.roundToInt()
                SettingsManager.saveExecutionFrequency(context, hours)
                WorkerScheduler.schedule(context.applicationContext)
                Toast.makeText(context, "Frecuencia guardada: $hours horas.", Toast.LENGTH_SHORT).show()
            }, valueRange = 1f..24f, steps = 22)
            Spacer(modifier = Modifier.height(16.dp))
            Text("La tarea de limpieza se ejecutará automáticamente cada ${frequencyPosition.roundToInt()} horas con la configuración guardada.")
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = {
                WorkerScheduler.runNow(context)
                Toast.makeText(context, "Ejecutando limpieza inmediata...", Toast.LENGTH_SHORT).show()
            }) {
                Text("Ejecutar limpieza ahora")
            }
        }
    }
}