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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview

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
}

@Composable
fun PasswordScreen(onPasswordCorrect: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    val correctPassword = "Luis031466"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.bloqueo),
            contentDescription = null,
            modifier = Modifier.height(150.dp),
            tint = Color.Unspecified
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(end = 50.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.nokey),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.padding(end = 10.dp)
            )
            Text(
                text = "Acceso Restringido",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,

            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "El acceso está restringido únicamente al área de sistemas",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(15.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = {
                Text("Contraseña",
                    modifier = Modifier
                        .width(280.dp),
                    textAlign = TextAlign.Center)
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .height(50.dp)
                .width(310.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = {
            if (password == correctPassword) {
                onPasswordCorrect()
            } else {
                Toast.makeText(context, "Contraseña incorrecta", Toast.LENGTH_SHORT).show()
            }
        }) {
            Icon(
                painter = painterResource(R.drawable.key),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .height(50.dp)
                    .width(50.dp)
            )
        }
    }
}

@Composable
@ExperimentalMaterial3Api
fun DatePickerModal(
    onDateSelected: (Long?) -> Unit,
    onDismiss: () -> Unit
){
    val datePickerState = rememberDatePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onDateSelected(datePickerState.selectedDateMillis)
                onDismiss()
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    ){
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    // Nota: SettingsManager puede devolver valores por defecto si el contexto de Preview no tiene SharedPreferences reales.
    var sliderPosition by remember { mutableFloatStateOf(SettingsManager.getDaysToKeep(context).toFloat()) }
    var frequencyPosition by remember { mutableFloatStateOf(SettingsManager.getExecutionFrequency(context).toFloat()) }

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

    Scaffold(topBar = { TopAppBar(title = { Text("Limpieza Inteligente", modifier = Modifier.width(360.dp), textAlign = TextAlign.Center) }) }) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Text(text = if (sliderPosition.roundToInt() == 0) "¡PRECAUCIÓN! Se eliminarán TODOS los archivos" else "Eliminar archivos con ${sliderPosition.roundToInt()} o más días de antigüedad")
            Spacer(modifier = Modifier.height(8.dp))
            Slider(value = sliderPosition, onValueChange = { sliderPosition = it }, onValueChangeFinished = {
                val days = sliderPosition.roundToInt()
                SettingsManager.saveDaysToKeep(context, days)
                WorkerScheduler.schedule(context.applicationContext)
                Toast.makeText(context, "Ajuste guardado: $days días. La tarea ha sido reprogramada.", Toast.LENGTH_SHORT).show()
            }, valueRange = 0f..180f, steps = 179)
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

// FUNCIONES DE VISTA PREVIA (PREVIEW)
@Preview(showBackground = true, name = "Pantalla de Contraseña")
@Composable
fun PasswordPreview() {
    MaterialTheme {
        PasswordScreen(onPasswordCorrect = {})
    }
}

@Preview(showBackground = true, name = "Pantalla de Ajustes")
@Composable
fun SettingsPreview() {
    MaterialTheme {
        SettingsScreen()
    }
}
