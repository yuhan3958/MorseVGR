package me.yuhan8954.morsevgr

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class TorchSender(
    private val cameraManager: CameraManager,
    private val scope: CoroutineScope,
    private val onRunningChanged: (Boolean) -> Unit,
) {
    var isRunning by mutableStateOf(false)
        private set
    var isLightOn by mutableStateOf(false)
        private set
    var currentLetter by mutableStateOf<Char?>(null)
        private set
    var currentSymbol by mutableStateOf<Int?>(null)
        private set
    var status by mutableStateOf("Tap Start to send VGR with the rear flash.")
        private set

    private var job: Job? = null
    private var cameraId: String? = null

    fun showMessage(message: String) {
        status = message
    }

    fun start(repeat: Boolean, dotMs: Long) {
        if (isRunning) return
        if (dotMs !in MIN_DOT_MS..MAX_DOT_MS) {
            status = "Enter a dot duration from $MIN_DOT_MS to $MAX_DOT_MS ms."
            return
        }
        val dashMs = dotMs * 3
        val letterGapMs = dotMs * 3
        val messageGapMs = dotMs * 7
        val rearFlash = try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val info = cameraManager.getCameraCharacteristics(id)
                info.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                    info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (error: CameraAccessException) {
            status = "Cannot access the camera. Close other camera apps and try again."
            return
        } catch (error: SecurityException) {
            status = "Camera permission is required. Allow it in app settings."
            return
        }
        if (rearFlash == null) {
            status = "This device has no available rear camera flash."
            return
        }
        cameraId = rearFlash
        isRunning = true
        onRunningChanged(true)
        job = scope.launch {
            try {
                do {
                    LETTERS.forEachIndexed { letterIndex, (letter, pattern) ->
                        currentLetter = letter
                        pattern.forEachIndexed { symbolIndex, symbol ->
                            currentSymbol = symbolIndex
                            status = "Sending $letter · ${symbolIndex + 1}/${pattern.length}"
                            setLight(true)
                            delay((if (symbol == '.') dotMs else dashMs).milliseconds)
                            setLight(false)
                            currentSymbol = null
                            if (symbolIndex < pattern.lastIndex) delay(dotMs.milliseconds)
                        }
                        if (letterIndex < LETTERS.lastIndex) {
                            status = "Letter gap · $letterGapMs ms"
                            delay(letterGapMs.milliseconds)
                        }
                    }
                    currentLetter = null
                    if (repeat) {
                        status = "Repeat gap · $messageGapMs ms"
                        delay(messageGapMs.milliseconds)
                    }
                } while (repeat)
                status = "VGR transmission complete."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: CameraAccessException) {
                status = "Flash unavailable. Close other camera apps and try again."
            } catch (error: SecurityException) {
                status = "Camera permission is required. Allow it in app settings."
            } catch (error: IllegalArgumentException) {
                status = "The camera connection changed. Please try again."
            } finally {
                turnOff()
                currentLetter = null
                currentSymbol = null
                isRunning = false
                onRunningChanged(false)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        if (isRunning) status = "Transmission stopped."
        turnOff()
        currentLetter = null
        currentSymbol = null
        isRunning = false
        onRunningChanged(false)
    }

    private fun setLight(enabled: Boolean) {
        cameraManager.setTorchMode(checkNotNull(cameraId), enabled)
        isLightOn = enabled
    }

    private fun turnOff() {
        val id = cameraId ?: return
        try {
            cameraManager.setTorchMode(id, false)
            cameraId = null
        } catch (error: CameraAccessException) {
            status = "Could not turn off the flash. Check the camera status."
        } catch (error: SecurityException) {
            status = "Camera permission changed. Check whether the flash is off."
        } catch (error: IllegalArgumentException) {
            status = "The camera connection changed. Check whether the flash is off."
        } finally {
            isLightOn = false
        }
    }

    companion object {
        val LETTERS = listOf('V' to "...-", 'G' to "--.", 'R' to ".-.")
        const val DEFAULT_DOT_MS = 400L
        const val MIN_DOT_MS = 1L
        const val MAX_DOT_MS = 60_000L
    }
}
