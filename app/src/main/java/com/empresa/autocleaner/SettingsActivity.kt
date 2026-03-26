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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.SelectableDates
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            modifier = Modifier.height(100.dp),
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(end = 50.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.nokey),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.padding(end = 25.dp)
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
                    textAlign = TextAlign.Center
                )
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .height(60.dp)
                .width(310.dp),
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
    onDismiss: () -> Unit,
){
    val now = System.currentTimeMillis()
    val thirtyDaysInMs = 30L * 24 * 60 * 60 * 1000
    val oneEightyDaysInMs = 180L * 24 * 60 * 60 * 1000

    val maxTimestamp = now - thirtyDaysInMs
    val minTimestamp = now - oneEightyDaysInMs

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = maxTimestamp,
        initialDisplayedMonthMillis = maxTimestamp,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long):Boolean{
                return utcTimeMillis in minTimestamp..maxTimestamp
            }
        }
    )

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
    val activity = (context as? ComponentActivity)
    // Nota: SettingsManager puede devolver valores por defecto si el contexto de Preview no tiene SharedPreferences reales.
    var daysToKeep by remember { mutableFloatStateOf(SettingsManager.getDaysToKeep(context).toFloat()) }
    var frequencyPosition by remember { mutableFloatStateOf(SettingsManager.getExecutionFrequency(context).toFloat()) }
    var showDatePicker by remember { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .background(color = Color(0xFFFFFFFF), shape = RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF174375), shape = RoundedCornerShape(10.dp))
        ) {
            Row() {
                Icon(
                    painter = painterResource(R.drawable.ajustes),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .padding(start = 15.dp, top = 8.dp)
                        .height(50.dp)
                        .width(50.dp)
                )
                Text(
                    text = "Archivos con ${daysToKeep.roundToInt()} o más días de antigüedad",
                    style = MaterialTheme.typography.bodyLarge,
                    //textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 9.dp, start = 10.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF174375)),
                modifier = Modifier
                    .padding(bottom = 10.dp, start = 45.dp),
                onClick = { showDatePicker = true }
            ) {
                Icon(painter = painterResource(R.drawable.calender), contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cambiar fecha de antigüedad")
            }
            if (showDatePicker) {
                DatePickerModal(
                    onDateSelected = { millis ->
                        if (millis != null) {
                            val diffMs = System.currentTimeMillis() - millis
                            val diffDays = (diffMs / (1000 * 60 * 60 * 24)).toInt()

                            daysToKeep = diffDays.toFloat()
                            SettingsManager.saveDaysToKeep(context, diffDays)

                            val date = Date(millis)
                            val formattedDate =
                                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
                            Toast.makeText(
                                context,
                                "Nueva antigüedad: $diffDays días ($formattedDate)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onDismiss = { showDatePicker = false }
                )
            }
        }
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

@Preview(showBackground = true, name = "Modal de Fecha")
@Composable
@ExperimentalMaterial3Api
fun DatePickerPreview() {
    MaterialTheme {
        DatePickerModal(onDateSelected = {}, onDismiss = {})
    }
}