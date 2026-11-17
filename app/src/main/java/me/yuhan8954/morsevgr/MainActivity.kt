package me.yuhan8954.morsevgr

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import me.yuhan8954.morsevgr.ui.theme.MorseVGRTheme

class MainActivity : ComponentActivity() {
    private lateinit var sender: TorchSender

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sender = TorchSender(getSystemService(CameraManager::class.java), lifecycleScope) { running ->
            if (running) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        setContent {
            var repeat by rememberSaveable { mutableStateOf(false) }
            var dotInput by rememberSaveable { mutableStateOf(TorchSender.DEFAULT_DOT_MS.toString()) }
            val dotMs = dotInput.toLongOrNull()?.takeIf {
                it in TorchSender.MIN_DOT_MS..TorchSender.MAX_DOT_MS
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    dotMs?.let { sender.start(repeat, it) }
                } else if (!granted) {
                    sender.showMessage("Camera permission is required to use the flash. Allow it in app settings.")
                } else {
                    sender.showMessage("Permission granted. Tap Start to begin.")
                }
            }
            MorseVGRTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MorseScreen(
                        sender = sender,
                        repeat = repeat,
                        dotInput = dotInput,
                        dotMs = dotMs,
                        onDotInputChanged = { dotInput = it },
                        onRepeatChanged = { repeat = it },
                        onToggle = {
                            if (sender.isRunning) {
                                sender.stop()
                            } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                dotMs?.let { sender.start(repeat, it) }
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onStop() {
        sender.stop()
        super.onStop()
    }
}

@Composable
private fun MorseScreen(
    sender: TorchSender,
    repeat: Boolean,
    dotInput: String,
    dotMs: Long?,
    onDotInputChanged: (String) -> Unit,
    onRepeatChanged: (Boolean) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("MORSE / FLASH", style = MaterialTheme.typography.labelLarge)
        Text("VGR", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
        Text("Send Morse code with the rear camera flash", style = MaterialTheme.typography.bodyLarge)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TorchSender.LETTERS.forEach { (letter, pattern) ->
                    val active = sender.currentLetter == letter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Text(
                            text = letter.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        )
                        pattern.forEachIndexed { index, symbol ->
                            Text(
                                text = if (symbol == '.') "·" else "−",
                                style = MaterialTheme.typography.headlineMedium,
                                fontFamily = FontFamily.Monospace,
                                color = if (active && sender.currentSymbol == index && sender.isLightOn)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(
                modifier = Modifier.size(16.dp).background(
                    if (sender.isLightOn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                ),
            )
            Text(sender.status, style = MaterialTheme.typography.bodyLarge)
        }
        OutlinedTextField(
            value = dotInput,
            onValueChange = onDotInputChanged,
            modifier = Modifier.fillMaxWidth(),
            enabled = !sender.isRunning,
            label = { Text("Dot duration (ms)") },
            supportingText = {
                Text("Enter a whole number from ${TorchSender.MIN_DOT_MS} to ${TorchSender.MAX_DOT_MS} ms.")
            },
            isError = dotMs == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Repeat", style = MaterialTheme.typography.titleMedium)
            Switch(checked = repeat, onCheckedChange = onRepeatChanged, enabled = !sender.isRunning)
        }
        Button(
            onClick = onToggle,
            enabled = sender.isRunning || dotMs != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(if (sender.isRunning) "Stop" else "Start VGR")
        }
        Text(
            if (dotMs != null) {
                "Dot $dotMs ms · Dash ${dotMs * 3} ms\n" +
                    "Symbol gap $dotMs ms · Letter gap ${dotMs * 3} ms\n" +
                    "Repeat gap ${dotMs * 7} ms"
            } else {
                "Enter a valid dot duration to preview timing and start transmission."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Transmission stops automatically when you leave the app.\nKeep the flash away from eyes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
