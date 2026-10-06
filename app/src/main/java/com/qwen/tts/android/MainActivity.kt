package com.qwen.tts.android

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qwen.tts.android.data.VoiceRecorder
import com.qwen.tts.android.data.db.GenerationEntity
import com.qwen.tts.android.data.db.QwenDatabase
import com.qwen.tts.android.data.db.VoiceProfileEntity
import com.qwen.tts.android.ui.theme.QwenTtsTheme
import com.qwen.tts.studio.engine.BengaliTtsEngine
import com.qwen.tts.studio.engine.BengaliTranslator
import com.qwen.tts.studio.engine.QwenEngine
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GGUF_MAGIC = byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte())
private const val MIN_VOICE_SAMPLE_MILLIS = 3_000L
private const val DEFAULT_DEMO_TEXT = "Hello World from Qwen3 TTS running on Android on-device!"
private const val BENGALI_DEMO_TEXT = "আমি ভালো আছি। আপনি কেমন আছেন?"

private data class ModelFile(
    val name: String,
    val url: String?,
    val sizeBytes: Long,
)

private data class ModelVariant(
    val id: String,
    val label: String,
    val displayName: String,
    val files: List<ModelFile>,
    val talkerName: String,
) {
    val totalBytes: Long = files.sumOf { it.sizeBytes }
}

private data class LanguageOption(val label: String, val id: Int)
private data class BackendOption(val id: String, val label: String, val preference: Int)
private data class GenerationTimeEstimate(val totalMillis: Long, val sampleSize: Int)
private data class TtsSynthesisResult(
    val audio: FloatArray,
    val sampleRate: Int,
    val timeMillis: Long,
    val tokenizeMillis: Long = 0L,
    val encodeMillis: Long = 0L,
    val generateMillis: Long = 0L,
    val decodeMillis: Long = 0L,
    val decodeFrames: Int = 0,
    val decodeSamples: Long = 0L,
    val decodeGraphComputeMillis: Long = 0L,
)

private val languageOptions = listOf(
    LanguageOption("English", 2050),
    LanguageOption("German", 2053),
    LanguageOption("Spanish", 2054),
    LanguageOption("French", 2061),
    LanguageOption("Chinese", 2055),
    LanguageOption("Japanese", 2058),
    LanguageOption("Korean", 2064),
    LanguageOption("Russian", 2069),
)

private val backendOptions = listOf(
    BackendOption("cpu", "CPU", QwenEngine.BACKEND_CPU),
)

private val defaultBackendOption = backendOptions.first { it.id == "cpu" }

private object QwenModel {
    private const val repo = "Serveurperso/Qwen3-TTS-GGUF"
    private const val baseUrl = "https://huggingface.co/$repo/resolve/main"

    val defaultVariant: ModelVariant = variant(
        id = "q4_k_m",
        label = "Q4_K_M",
        displayName = "Qwen3-TTS 0.6B Q4_K_M",
        talkerName = "qwen-talker-0.6b-base-Q4_K_M.gguf",
        tokenizerName = "qwen-tokenizer-12hz-Q4_K_M.gguf",
        talkerSizeBytes = 628_905_056L,
        tokenizerSizeBytes = 254_974_752L,
    )

    val variants = listOf(defaultVariant)

    val obsoleteFileNames = setOf(
        "qwen-talker-0.6b-base-Q8_0.gguf",
        "qwen-talker-0.6b-base-NVFP4.gguf",
        "qwen-tokenizer-12hz-Q8_0.gguf",
    )

    // Q8_0 and NVFP4 used to be available here. The Android app now intentionally
    // ships one supported path: Q4_K_M.

    fun variantById(id: String): ModelVariant =
        variants.firstOrNull { it.id == id } ?: defaultVariant

    private fun variant(
        id: String,
        label: String,
        displayName: String,
        talkerName: String,
        tokenizerName: String,
        talkerSizeBytes: Long,
        tokenizerSizeBytes: Long,
        talkerUrl: String? = "$baseUrl/$talkerName?download=true",
        tokenizerUrl: String? = "$baseUrl/$tokenizerName?download=true",
    ): ModelVariant =
        ModelVariant(
            id = id,
            label = label,
            displayName = displayName,
            talkerName = talkerName,
            files = listOf(
                ModelFile(tokenizerName, tokenizerUrl, tokenizerSizeBytes),
                ModelFile(talkerName, talkerUrl, talkerSizeBytes),
            ),
        )
}

private object BengaliModel {
    const val id = "bengali_vits"
    const val displayName = "Bengali · male and female voices"
    val files = listOf(
        ModelFile(
            "vits-mimic3-bn-multi_low.tar.bz2",
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-mimic3-bn-multi_low.tar.bz2",
            10_000_000L,
        ),
    )
    const val estimatedBytes = 95_000_000L
    const val readyMarker = "multi-speaker-ready"
}

private val speechModelOptions = listOf(
    QwenModel.defaultVariant.id to QwenModel.defaultVariant.displayName,
    BengaliModel.id to BengaliModel.displayName,
)

private val bengaliSpeakerOptions = listOf(
    0 to "Female · rm",
    1 to "Male · 03042",
    2 to "Speaker 3 · 00737",
    3 to "Speaker 4 · 01232",
    4 to "Speaker 5 · 02194",
    5 to "Speaker 6 · 3108",
    6 to "Speaker 7 · 3713",
    7 to "Speaker 8 · 1010",
    8 to "Speaker 9 · 00779",
    9 to "Speaker 10 · 9169",
    10 to "Speaker 11 · 4046",
    11 to "Speaker 12 · 5958",
    12 to "Speaker 13 · 01701",
    13 to "Speaker 14 · 4811",
    14 to "Speaker 15 · 0834",
    15 to "Speaker 16 · 3958",
)

data class QwenTtsUiState(
    val text: String = DEFAULT_DEMO_TEXT,
    val selectedModelId: String = QwenModel.defaultVariant.id,
    val selectedBackendId: String = defaultBackendOption.id,
    val selectedVoiceId: String? = null,
    val selectedBengaliSpeakerId: Int = 0,
    val translateEnglishToBengali: Boolean = false,
    val translatedText: String? = null,
    val activeBackendName: String? = null,
    val modelReady: Boolean = false,
    val loaded: Boolean = false,
    val setupComplete: Boolean = false,
    val busy: Boolean = false,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadBytes: Long = 0L,
    val downloadTotalBytes: Long = QwenModel.defaultVariant.totalBytes,
    val status: String = "Model not downloaded",
    val error: String? = null,
    val sampleRate: Int = 0,
    val sampleCount: Int = 0,
    val synthesisMillis: Long = 0,
    val playing: Boolean = false,
    val playingGenerationId: Long? = null,
    val operationElapsedMillis: Long = 0,
    val synthesisFrames: Int = 0,
    val maxAudioTokens: Int = 512,
    val cpuThreads: Int = 0,
    val selectedCpuThreads: Int = 0,
    val selectedLanguageId: Int = 2050,
    val tokenizeMillis: Long = 0,
    val encodeMillis: Long = 0,
    val generateMillis: Long = 0,
    val decodeMillis: Long = 0,
    val decodeFrames: Int = 0,
    val decodeSamples: Long = 0,
    val decodeGraphComputeMillis: Long = 0,
    val savedFileName: String? = null,
    val snackbarMessage: String? = null,
    val estimatedSynthesisMillis: Long? = null,
    val estimateSampleCount: Int = 0,
    val supportsCloning: Boolean? = null,
    val speakerEmbeddingDim: Int = 0,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val modelDir = File(application.filesDir, "qwen3-tts-models")
    private val bengaliModelDir = File(application.filesDir, "bengali-multispeaker-model")
    private val voiceDir = File(application.filesDir, "voices")
    private val generationDir = File(application.filesDir, "generations")
    private val preferences = application.getSharedPreferences("voice-notes-settings", Application.MODE_PRIVATE)
    private val initialModelId = preferences.getString("speech-model", QwenModel.defaultVariant.id)
        ?.takeIf { saved -> speechModelOptions.any { it.first == saved } }
        ?: QwenModel.defaultVariant.id
    private val dao = QwenDatabase.getDatabase(application).qwenDao()
    private val recorder = VoiceRecorder()

    val voices = dao.observeVoices().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val generations = dao.observeGenerations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recorderState = recorder.state

    private val _uiState = MutableStateFlow(
        QwenTtsUiState(
            selectedModelId = initialModelId,
            modelReady = isSelectedModelReady(initialModelId),
            downloadTotalBytes = selectedModelBytes(initialModelId),
        ),
    )
    val uiState = _uiState.asStateFlow()

    private var engine: QwenEngine? = null
    private var bengaliEngine: BengaliTtsEngine? = null
    private var bengaliTranslator: BengaliTranslator? = null
    private var generatedAudio: FloatArray? = null
    private var playJob: Job? = null
    private var operationTickerJob: Job? = null
    private var activeTrack: AudioTrack? = null

    init {
        val threads = preferredCpuThreadCount()
        _uiState.update { it.copy(selectedCpuThreads = threads, cpuThreads = threads) }
        refreshModelState()
    }

    fun updateText(value: String) {
        _uiState.update { it.copy(text = value, translatedText = null) }
    }

    fun updateCpuThreads(value: Int) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(selectedCpuThreads = value, cpuThreads = value) }
        engine?.setCpuThreads(value)
    }

    fun updateLanguage(languageId: Int) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(selectedLanguageId = languageId) }
    }

    fun selectVoice(voiceId: String?) {
        _uiState.update { it.copy(selectedVoiceId = voiceId) }
    }

    fun selectBengaliSpeaker(speakerId: Int) {
        if (_uiState.value.busy || speakerId !in 0..15) return
        _uiState.update { it.copy(selectedBengaliSpeakerId = speakerId) }
    }

    fun setTranslateEnglishToBengali(enabled: Boolean) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(translateEnglishToBengali = enabled, translatedText = null) }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun selectModel(modelId: String) {
        if (_uiState.value.busy || _uiState.value.selectedModelId == modelId) return
        val selectedId = speechModelOptions.firstOrNull { it.first == modelId }?.first ?: QwenModel.defaultVariant.id
        stopAudio()
        engine?.close()
        engine = null
        bengaliEngine?.close()
        bengaliEngine = null
        bengaliTranslator?.close()
        bengaliTranslator = null
        generatedAudio = null
        val ready = isSelectedModelReady(selectedId)
        preferences.edit().putString("speech-model", selectedId).apply()
        _uiState.update {
            it.copy(
                selectedModelId = selectedId,
                translatedText = null,
                text = when {
                    selectedId == BengaliModel.id && it.text == DEFAULT_DEMO_TEXT -> BENGALI_DEMO_TEXT
                    selectedId != BengaliModel.id && it.text == BENGALI_DEMO_TEXT -> DEFAULT_DEMO_TEXT
                    else -> it.text
                },
                activeBackendName = null,
                modelReady = ready,
                loaded = false,
                setupComplete = false,
                status = if (ready) "Model ready" else "Model not downloaded",
                error = null,
                downloadProgress = 0f,
                downloadBytes = 0L,
                downloadTotalBytes = selectedModelBytes(selectedId),
                sampleRate = 0,
                sampleCount = 0,
                synthesisMillis = 0,
                synthesisFrames = 0,
                tokenizeMillis = 0,
                encodeMillis = 0,
                generateMillis = 0,
                decodeMillis = 0,
                decodeFrames = 0,
                decodeSamples = 0,
                decodeGraphComputeMillis = 0,
                savedFileName = null,
                estimatedSynthesisMillis = null,
                estimateSampleCount = 0,
                supportsCloning = if (selectedId == BengaliModel.id) false else null,
                speakerEmbeddingDim = 0,
            )
        }
    }

    fun downloadModel(forceRedownload: Boolean = false) {
        if (_uiState.value.downloading) return
        viewModelScope.launch {
            val variant = selectedVariant()
            if (forceRedownload) {
                engine?.close()
                engine = null
                bengaliEngine?.close()
                bengaliEngine = null
                if (isBengaliSelected()) File(bengaliModelDir, BengaliModel.readyMarker).delete()
                selectedModelFiles().forEach { file ->
                    val dir = selectedModelDir()
                    runCatching { File(dir, file.name).delete() }
                    runCatching { File(dir, "${file.name}.download").delete() }
                }
                _uiState.update { it.copy(loaded = false, modelReady = false) }
            }
            startBusy(
                status = if (forceRedownload) "Replacing model files" else "Preparing model download",
                downloading = true,
            )
            _uiState.update {
                it.copy(downloadProgress = 0f, downloadBytes = 0L, downloadTotalBytes = selectedModelBytes())
            }

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    downloadSelectedModel(variant, forceRedownload)
                }
            }

            result.fold(
                onSuccess = {
                    stopBusyTicker()
                    refreshModelState("Model ready")
                },
                onFailure = { throwable ->
                    stopBusyTicker()
                    _uiState.update {
                        it.copy(
                            busy = false,
                            downloading = false,
                            status = "Download failed",
                            error = throwable.message ?: "Download failed",
                        )
                    }
                },
            )
        }
    }

    fun loadModel() {
        if (!_uiState.value.modelReady || _uiState.value.loaded || _uiState.value.busy) return
        viewModelScope.launch {
            startBusy(status = "Loading speech model")
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (isBengaliSelected()) {
                        ensureBengaliEngineLoaded()
                        "CPU" to _uiState.value.selectedCpuThreads
                    } else {
                        val native = ensureEngineLoaded(selectedVariant())
                        native.getActiveBackendName().orEmpty() to native.getCpuThreads()
                    }
                }
            }

            result.fold(
                onSuccess = { (backend, threads) ->
                    stopBusyTicker()
                    val caps = if (isBengaliSelected()) null else engine?.getModelCapabilities()
                    _uiState.update {
                        it.copy(
                            busy = false,
                            loaded = true,
                            status = if (isBengaliSelected()) "Bengali speech ready" else if (backend.isBlank()) "Model loaded" else "Model loaded on $backend",
                            activeBackendName = backend.ifBlank { null },
                            cpuThreads = threads,
                            supportsCloning = if (isBengaliSelected()) false else caps?.supportsCloning,
                            speakerEmbeddingDim = caps?.speakerEmbeddingDim ?: 0,
                        )
                    }
                },
                onFailure = { throwable ->
                    stopBusyTicker()
                    _uiState.update {
                        it.copy(
                            busy = false,
                            loaded = false,
                            modelReady = isSelectedModelReady(),
                            status = "Load failed",
                            error = "${throwable.message ?: "Load failed"} If the model was already downloaded, use Re-download model in Settings.",
                        )
                    }
                },
            )
        }
    }

    fun synthesize() {
        val text = _uiState.value.text.trim()
        if (text.isEmpty() || _uiState.value.busy) return
        viewModelScope.launch {
            val bengaliSelected = isBengaliSelected()
            val bengaliSpeaker = _uiState.value.selectedBengaliSpeakerId
            val translateEnglish = bengaliSelected && _uiState.value.translateEnglishToBengali
            val variant = selectedVariant()
            val selectedVoice = if (bengaliSelected) null else _uiState.value.selectedVoiceId?.let { id -> voices.value.firstOrNull { it.voiceId == id } }
            val estimate = if (bengaliSelected) null else estimateSynthesisMillis(text)
            startBusy(status = if (bengaliSelected) "Generating Bengali speech" else "Synthesizing speech", resetSynthesis = true, synthesisEstimate = estimate)

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (bengaliSelected) {
                        val startedAt = SystemClock.elapsedRealtime()
                        val speechText = if (translateEnglish) {
                            _uiState.update { it.copy(status = "Translating English to Bengali") }
                            ensureBengaliTranslator().translate(text)
                        } else text
                        _uiState.update { it.copy(translatedText = if (translateEnglish) speechText else null, status = "Generating Bengali speech") }
                        val output = ensureBengaliEngineLoaded().synthesize(speechText, bengaliSpeaker)
                        TtsSynthesisResult(
                            audio = output.samples,
                            sampleRate = output.sampleRate,
                            timeMillis = SystemClock.elapsedRealtime() - startedAt,
                        )
                    } else {
                        val native = ensureEngineLoaded(variant)
                        native.setProgressCallback { frames, maxTokens ->
                            _uiState.update {
                                it.copy(
                                    loaded = true,
                                    status = "Generating speech codes",
                                    synthesisFrames = frames,
                                    maxAudioTokens = maxTokens,
                                )
                            }
                        }
                        try {
                            val nativeResult = native.synthesize(
                                text = text,
                                speakerEmbeddingPath = selectedVoice?.speakerEmbeddingPath,
                                params = QwenEngine.NativeParams(languageId = _uiState.value.selectedLanguageId),
                            )
                            if (!nativeResult.success) error(nativeResult.errorMsg ?: "Native synthesis failed")
                            val audio = nativeResult.audio ?: error(nativeResult.errorMsg ?: "Native synthesis returned no audio")
                            TtsSynthesisResult(
                                audio = audio,
                                sampleRate = nativeResult.sampleRate,
                                timeMillis = nativeResult.timeMs,
                                tokenizeMillis = nativeResult.tokenizeMs,
                                encodeMillis = nativeResult.encodeMs,
                                generateMillis = nativeResult.generateMs,
                                decodeMillis = nativeResult.decodeMs,
                                decodeFrames = nativeResult.decodeFrames,
                                decodeSamples = nativeResult.decodeSamples,
                                decodeGraphComputeMillis = nativeResult.decodeGraphComputeMs,
                            )
                        } finally {
                            _uiState.update {
                                it.copy(activeBackendName = native.getActiveBackendName()?.takeIf { name -> name.isNotBlank() })
                            }
                            native.setProgressCallback(null)
                        }
                    }
                }
            }

            result.fold(
                onSuccess = { synthesisResult ->
                    stopBusyTicker()
                    generatedAudio = synthesisResult.audio
                    val sampleRate = synthesisResult.sampleRate.takeIf { it > 0 } ?: 24_000
                    val voiceName = if (bengaliSelected) bengaliSpeakerOptions.firstOrNull { it.first == bengaliSpeaker }?.second ?: "Bengali speaker $bengaliSpeaker" else selectedVoice?.name ?: "Default Voice"
                    persistGeneratedAudio(text, synthesisResult.audio, sampleRate, voiceName, selectedVoice?.voiceId, synthesisResult.timeMillis)
                    _uiState.update {
                        it.copy(
                            busy = false,
                            loaded = true,
                            status = if (bengaliSelected) "Bengali speech ready" else "Speech ready",
                            sampleRate = sampleRate,
                            sampleCount = synthesisResult.audio.size,
                            synthesisMillis = synthesisResult.timeMillis,
                            tokenizeMillis = synthesisResult.tokenizeMillis,
                            encodeMillis = synthesisResult.encodeMillis,
                            generateMillis = synthesisResult.generateMillis,
                            decodeMillis = synthesisResult.decodeMillis,
                            decodeFrames = synthesisResult.decodeFrames,
                            decodeSamples = synthesisResult.decodeSamples,
                            decodeGraphComputeMillis = synthesisResult.decodeGraphComputeMillis,
                            estimatedSynthesisMillis = null,
                            estimateSampleCount = 0,
                            error = null,
                        )
                    }
                    playAudio()
                },
                onFailure = { throwable ->
                    stopBusyTicker()
                    _uiState.update {
                        it.copy(
                            busy = false,
                            status = "Synthesis failed",
                            error = throwable.message ?: "Synthesis failed",
                        )
                    }
                },
            )
        }
    }

    fun startVoiceRecording() {
        if (isBengaliSelected() || _uiState.value.busy || recorderState.value.isRecording) return
        val file = File(voiceDir, "pending-${System.currentTimeMillis()}.wav")
        if (!recorder.start(file)) {
            _uiState.update { it.copy(error = "Could not start microphone recording") }
        }
    }

    fun stopVoiceRecordingAndCreate(name: String) {
        val trimmedName = name.trim().ifBlank { "Voice ${voices.value.size + 1}" }
        val result = recorder.stop() ?: return
        if (result.durationMillis < MIN_VOICE_SAMPLE_MILLIS) {
            result.file.delete()
            _uiState.update {
                it.copy(error = "Record at least 3 seconds of clear speech before creating a voice.")
            }
            return
        }
        viewModelScope.launch {
            startBusy(status = "Creating voice embedding")
            val created = runCatching {
                withContext(Dispatchers.IO) {
                    val native = ensureEngineLoaded(selectedVariant())
                    val caps = native.getModelCapabilities()
                    if (caps?.supportsCloning == false) {
                        error("The loaded model does not expose a speaker encoder.")
                    }

                    val voiceId = "voice-${System.currentTimeMillis()}"
                    val targetDir = File(voiceDir, voiceId).apply { mkdirs() }
                    val reference = File(targetDir, "reference.wav")
                    result.file.copyTo(reference, overwrite = true)
                    runCatching { result.file.delete() }

                    val embedding = File(targetDir, "speaker.json")
                    if (!native.extractSpeakerEmbedding(reference.absolutePath, embedding.absolutePath)) {
                        error(native.getLastError() ?: "Speaker embedding extraction failed")
                    }

                    VoiceProfileEntity(
                        voiceId = voiceId,
                        name = makeUniqueVoiceName(trimmedName),
                        referenceWavPath = reference.absolutePath,
                        speakerEmbeddingPath = embedding.absolutePath,
                        durationMillis = result.durationMillis,
                    )
                }
            }

            created.fold(
                onSuccess = { voice ->
                    dao.insertVoice(voice)
                    stopBusyTicker()
                    _uiState.update {
                        it.copy(
                            busy = false,
                            selectedVoiceId = voice.voiceId,
                            status = "Voice created",
                            error = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    stopBusyTicker()
                    _uiState.update {
                        it.copy(
                            busy = false,
                            status = "Voice creation failed",
                            error = throwable.message ?: "Voice creation failed",
                        )
                    }
                },
            )
        }
    }

    fun cancelVoiceRecording() {
        recorder.cancel()
    }

    fun deleteVoice(voice: VoiceProfileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteVoice(voice)
            runCatching { File(voice.referenceWavPath).parentFile?.deleteRecursively() }
            if (_uiState.value.selectedVoiceId == voice.voiceId) {
                _uiState.update { it.copy(selectedVoiceId = null) }
            }
        }
    }

    fun playGeneration(generationId: Long) {
        if (_uiState.value.playing && _uiState.value.playingGenerationId == generationId) {
            stopAudio()
            return
        }
        viewModelScope.launch {
            val generation = withContext(Dispatchers.IO) { dao.getGeneration(generationId) } ?: return@launch
            val loaded = runCatching { withContext(Dispatchers.IO) { readWav(generation.wavPath) } }
            loaded.onSuccess { wav ->
                stopAudio()
                generatedAudio = wav.samples
                _uiState.update {
                    it.copy(
                        sampleRate = wav.sampleRate,
                        sampleCount = wav.samples.size,
                        status = "Speech ready",
                        error = null,
                    )
                }
                playAudio(generationId)
            }.onFailure { throwable ->
                _uiState.update { it.copy(error = throwable.message ?: "Could not play history item") }
            }
        }
    }

    fun deleteGeneration(generation: GenerationEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteGeneration(generation)
            runCatching { File(generation.wavPath).delete() }
        }
    }

    fun playAudio(historyGenerationId: Long? = null) {
        val samples = generatedAudio ?: return
        val sampleRate = _uiState.value.sampleRate.takeIf { it > 0 } ?: 24_000
        stopAudio()
        playJob = viewModelScope.launch(Dispatchers.IO) {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(samples.size * 4)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            activeTrack = track
            _uiState.update { it.copy(playing = true, playingGenerationId = historyGenerationId) }
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            track.play()
            val durationMillis = (samples.size.toDouble() / sampleRate.toDouble() * 1000.0).toLong()
            kotlinx.coroutines.delay(durationMillis.coerceAtLeast(200L))
            track.release()
            if (activeTrack == track) activeTrack = null
            _uiState.update { it.copy(playing = false, playingGenerationId = null) }
        }
    }

    fun stopAudio() {
        playJob?.cancel()
        playJob = null
        activeTrack?.let { track ->
            runCatching { track.stop() }
            runCatching { track.release() }
        }
        activeTrack = null
        _uiState.update { it.copy(playing = false, playingGenerationId = null) }
    }

    fun suggestedExportFileName(generation: GenerationEntity): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)
        return "voice-note-${LocalDateTime.now().format(formatter)}-${generation.generationId}.wav"
    }

    fun exportGenerationToUri(generation: GenerationEntity, uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri)?.use { output ->
                        File(generation.wavPath).inputStream().use { input ->
                            input.copyTo(output)
                        }
                    } ?: error("Could not open media output")
                }
            }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            savedFileName = null,
                            snackbarMessage = "Export complete",
                            status = "Exported WAV",
                            error = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            snackbarMessage = throwable.message ?: "Could not export WAV",
                            error = null,
                        )
                    }
                },
            )
        }
    }

    private fun ensureEngineLoaded(variant: ModelVariant): QwenEngine {
        val native = engine ?: QwenEngine().also { engine = it }
        val backend = selectedBackendOption()
        if (!native.setBackendPreference(backend.preference)) {
            error("Could not switch backend to ${backend.label}")
        }
        val threads = selectedCpuThreadCount()
        native.setCpuThreads(threads)
        if (!_uiState.value.loaded) {
            if (!native.loadModels(modelDir.absolutePath, variant.talkerName)) {
                error(native.getLastError() ?: "Native model load failed")
            }
            val caps = native.getModelCapabilities()
            _uiState.update {
                it.copy(
                    loaded = true,
                    cpuThreads = native.getCpuThreads(),
                    activeBackendName = native.getActiveBackendName()?.takeIf { name -> name.isNotBlank() },
                    supportsCloning = caps?.supportsCloning,
                    speakerEmbeddingDim = caps?.speakerEmbeddingDim ?: 0,
                )
            }
        }
        return native
    }

    private fun preferredCpuThreadCount(): Int {
        val available = Runtime.getRuntime().availableProcessors()
        return when {
            available >= 8 -> 6
            available >= 4 -> 4
            else -> available.coerceAtLeast(1)
        }
    }

    private fun selectedCpuThreadCount(): Int =
        _uiState.value.selectedCpuThreads.takeIf { it > 0 } ?: preferredCpuThreadCount()

    private fun selectedBackendOption(): BackendOption =
        backendOptionById(_uiState.value.selectedBackendId)

    private fun startBusy(
        status: String,
        downloading: Boolean = false,
        resetSynthesis: Boolean = false,
        synthesisEstimate: GenerationTimeEstimate? = null,
    ) {
        operationTickerJob?.cancel()
        val start = SystemClock.elapsedRealtime()
        _uiState.update {
            it.copy(
                busy = true,
                downloading = downloading,
                error = null,
                status = status,
                operationElapsedMillis = 0L,
                synthesisFrames = if (resetSynthesis) 0 else it.synthesisFrames,
                sampleCount = if (resetSynthesis) 0 else it.sampleCount,
                synthesisMillis = if (resetSynthesis) 0 else it.synthesisMillis,
                tokenizeMillis = if (resetSynthesis) 0 else it.tokenizeMillis,
                encodeMillis = if (resetSynthesis) 0 else it.encodeMillis,
                generateMillis = if (resetSynthesis) 0 else it.generateMillis,
                decodeMillis = if (resetSynthesis) 0 else it.decodeMillis,
                decodeFrames = if (resetSynthesis) 0 else it.decodeFrames,
                decodeSamples = if (resetSynthesis) 0 else it.decodeSamples,
                decodeGraphComputeMillis = if (resetSynthesis) 0 else it.decodeGraphComputeMillis,
                savedFileName = if (resetSynthesis) null else it.savedFileName,
                estimatedSynthesisMillis = synthesisEstimate?.totalMillis ?: if (resetSynthesis) null else it.estimatedSynthesisMillis,
                estimateSampleCount = synthesisEstimate?.sampleSize ?: if (resetSynthesis) 0 else it.estimateSampleCount,
            )
        }
        operationTickerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(250L)
                val elapsed = SystemClock.elapsedRealtime() - start
                _uiState.update { current ->
                    if (current.busy) current.copy(operationElapsedMillis = elapsed) else current
                }
            }
        }
    }

    private fun stopBusyTicker() {
        operationTickerJob?.cancel()
        operationTickerJob = null
    }

    private fun estimateSynthesisMillis(text: String): GenerationTimeEstimate? {
        val ratios = generations.value
            .asSequence()
            .filter { it.synthesisMillis > 0L && it.text.isNotBlank() }
            .take(20)
            .map { generation ->
                generation.synthesisMillis.toDouble() / generation.text.length.coerceAtLeast(1).toDouble()
            }
            .filter { it.isFinite() && it > 0.0 }
            .sorted()
            .toList()

        if (ratios.size < 3) return null

        val trimmed = if (ratios.size >= 5) {
            ratios.drop(1).dropLast(1)
        } else {
            ratios
        }
        val millisPerCharacter = trimmed.average()
        val estimate = (millisPerCharacter * text.length.coerceAtLeast(1)).toLong()
            .coerceAtLeast(1_000L)
        return GenerationTimeEstimate(estimate, ratios.size)
    }

    private fun refreshModelState(status: String? = null) {
        val modelId = _uiState.value.selectedModelId
        val ready = isSelectedModelReady(modelId)
        stopBusyTicker()
        _uiState.update {
            it.copy(
                modelReady = ready,
                busy = false,
                downloading = false,
                downloadTotalBytes = selectedModelBytes(modelId),
                status = status ?: if (ready) "Model ready" else "Model not downloaded",
            )
        }
    }

    private fun isBengaliSelected(): Boolean = _uiState.value.selectedModelId == BengaliModel.id

    private fun selectedModelDir(): File = if (isBengaliSelected()) bengaliModelDir else modelDir

    private fun selectedModelFiles(): List<ModelFile> =
        if (isBengaliSelected()) BengaliModel.files else selectedVariant().files

    private fun selectedModelBytes(modelId: String = _uiState.value.selectedModelId): Long =
        if (modelId == BengaliModel.id) BengaliModel.estimatedBytes
        else QwenModel.variantById(modelId).totalBytes

    private fun isSelectedModelReady(modelId: String = _uiState.value.selectedModelId): Boolean =
        if (modelId == BengaliModel.id) {
            File(bengaliModelDir, BengaliModel.readyMarker).isFile &&
                bengaliModelDir.walkTopDown().any { it.isFile && it.extension == "onnx" && it.length() > 50_000_000L } &&
                bengaliModelDir.walkTopDown().any { it.isFile && it.name == "tokens.txt" } &&
                bengaliModelDir.walkTopDown().any { it.isDirectory && it.name == "espeak-ng-data" }
        } else {
            isModelReady(QwenModel.variantById(modelId))
        }

    private fun ensureBengaliEngineLoaded(): BengaliTtsEngine {
        bengaliEngine?.let { return it }
        return BengaliTtsEngine(bengaliModelDir, _uiState.value.selectedCpuThreads.coerceAtLeast(1))
            .also { bengaliEngine = it }
    }

    private fun ensureBengaliTranslator(): BengaliTranslator =
        bengaliTranslator ?: BengaliTranslator().also { bengaliTranslator = it }

    private fun downloadSelectedModel(variant: ModelVariant, forceRedownload: Boolean) {
        if (!isBengaliSelected()) {
            modelDir.mkdirs()
            downloadFiles(variant, forceRedownload)
            return
        }
        bengaliModelDir.mkdirs()
        var completedBytes = 0L
        BengaliModel.files.forEachIndexed { index, file ->
            val target = File(bengaliModelDir, file.name)
            if (!forceRedownload && target.isFile && target.length() >= file.sizeBytes) {
                completedBytes += target.length()
                return@forEachIndexed
            }
            val temp = File(bengaliModelDir, "${file.name}.download")
            val connection = (URL(file.url!!).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 30_000
                readTimeout = 60_000
            }
            var expectedBytes = file.sizeBytes
            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) error("Model server returned HTTP $responseCode for ${file.name}")
                val responseSize = connection.contentLengthLong.takeIf { it > 0L } ?: file.sizeBytes
                expectedBytes = maxOf(file.sizeBytes, responseSize)
                connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var fileBytes = 0L
                        var lastUpdate = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            fileBytes += read
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 250L) {
                                lastUpdate = now
                                val total = completedBytes + fileBytes
                                _uiState.update {
                                    it.copy(
                                        downloadBytes = total,
                                        downloadProgress = (total.toFloat() / BengaliModel.estimatedBytes).coerceIn(0f, 0.99f),
                                        status = "Downloading ${index + 1}/${BengaliModel.files.size}: ${file.name}",
                                        downloadTotalBytes = maxOf(BengaliModel.estimatedBytes, completedBytes + responseSize),
                                    )
                                }
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            if (!temp.isFile || temp.length() < expectedBytes) {
                temp.delete()
                error("Downloaded ${file.name} is incomplete. Please try again.")
            }
            if (target.exists() && !target.delete()) {
                temp.delete()
                error("Could not replace ${file.name}")
            }
            if (!temp.renameTo(target)) {
                temp.delete()
                error("Could not save ${file.name}")
            }
            completedBytes += target.length()
        }
        if (!isSelectedModelReady(BengaliModel.id)) {
            _uiState.update { it.copy(status = "Installing Bengali voices") }
            val archive = File(bengaliModelDir, BengaliModel.files.single().name)
            try {
                extractBengaliModel(archive)
            } catch (error: Exception) {
                archive.delete()
                throw error
            }
        }
    }

    private fun extractBengaliModel(archive: File) {
        val root = bengaliModelDir.canonicalFile
        val marker = File(root, BengaliModel.readyMarker)
        marker.delete()
        var extractedBytes = 0L
        archive.inputStream().buffered().use { input ->
            TarArchiveInputStream(BZip2CompressorInputStream(input)).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    val target = File(root, entry.name).canonicalFile
                    check(target == root || target.path.startsWith(root.path + File.separator)) { "Invalid Bengali model archive path" }
                    if (entry.isDirectory) {
                        check(target.mkdirs() || target.isDirectory) { "Could not create model directory" }
                    } else if (entry.isFile) {
                        extractedBytes += entry.size
                        check(extractedBytes < 250_000_000L) { "Bengali model archive is too large" }
                        val parent = checkNotNull(target.parentFile)
                        check(parent.mkdirs() || parent.isDirectory) { "Could not create model directory" }
                        target.outputStream().use { output -> tar.copyTo(output) }
                    }
                }
            }
        }
        check(root.walkTopDown().any { it.isFile && it.extension == "onnx" && it.length() > 50_000_000L }) { "Bengali speech model is missing" }
        check(root.walkTopDown().any { it.isFile && it.name == "tokens.txt" }) { "Bengali speech tokens are missing" }
        check(root.walkTopDown().any { it.isDirectory && it.name == "espeak-ng-data" }) { "Bengali pronunciation data is missing" }
        marker.writeText("ready")
        archive.delete()
    }

    private fun selectedVariant(): ModelVariant =
        QwenModel.variantById(_uiState.value.selectedModelId)

    private fun isModelReady(variant: ModelVariant): Boolean =
        variant.files.all { file ->
            isValidModelFile(File(modelDir, file.name), file)
        }

    private fun isValidModelFile(local: File, model: ModelFile): Boolean {
        if (!local.isFile || local.length() < model.sizeBytes) return false
        return runCatching {
            FileInputStream(local).use { input ->
                val header = ByteArray(GGUF_MAGIC.size)
                var offset = 0
                while (offset < header.size) {
                    val read = input.read(header, offset, header.size - offset)
                    if (read < 0) return false
                    offset += read
                }
                header.contentEquals(GGUF_MAGIC)
            }
        }.getOrDefault(false)
    }

    fun startSetup(forceRedownload: Boolean = false) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            val modelId = _uiState.value.selectedModelId
            val bengaliSelected = modelId == BengaliModel.id
            val variant = selectedVariant()
            if (forceRedownload) {
                engine?.close()
                engine = null
                bengaliEngine?.close()
                bengaliEngine = null
                if (bengaliSelected) File(bengaliModelDir, BengaliModel.readyMarker).delete()
                selectedModelFiles().forEach { file ->
                    val dir = selectedModelDir()
                    runCatching { File(dir, file.name).delete() }
                    runCatching { File(dir, "${file.name}.download").delete() }
                }
                _uiState.update { it.copy(modelReady = false, loaded = false, setupComplete = false) }
            }

            if (!isSelectedModelReady(modelId)) {
                startBusy(status = "Downloading speech model", downloading = true)
                _uiState.update {
                    it.copy(downloadProgress = 0f, downloadBytes = 0L, downloadTotalBytes = selectedModelBytes(modelId))
                }
                val downloaded = runCatching {
                    withContext(Dispatchers.IO) {
                        downloadSelectedModel(variant, forceRedownload = false)
                    }
                }
                if (downloaded.isFailure) {
                    stopBusyTicker()
                    _uiState.update {
                        it.copy(
                            busy = false,
                            downloading = false,
                            setupComplete = false,
                            status = "Model download failed",
                            error = downloaded.exceptionOrNull()?.message ?: "Model download failed",
                        )
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        modelReady = true,
                        busy = false,
                        downloading = false,
                        downloadBytes = selectedModelBytes(modelId),
                        downloadProgress = 1f,
                        status = "Model downloaded",
                        error = null,
                    )
                }
            }

            startBusy(status = "Loading speech model")
            val loaded = runCatching {
                withContext(Dispatchers.IO) {
                    if (bengaliSelected) {
                        ensureBengaliEngineLoaded()
                        _uiState.update { it.copy(status = "Downloading English to Bengali translation model") }
                        ensureBengaliTranslator().prepare()
                        "CPU" to _uiState.value.selectedCpuThreads
                    } else {
                        val native = ensureEngineLoaded(variant)
                        native.getActiveBackendName().orEmpty() to native.getCpuThreads()
                    }
                }
            }
            stopBusyTicker()
            loaded.fold(
                onSuccess = { (backend, threads) ->
                    val caps = if (bengaliSelected) null else engine?.getModelCapabilities()
                    _uiState.update {
                        it.copy(
                            busy = false,
                            downloading = false,
                            loaded = true,
                            setupComplete = true,
                            status = if (bengaliSelected) "Bengali speech is ready" else if (backend.isBlank()) "Ready to create your voice" else "Loaded on $backend",
                            activeBackendName = backend.ifBlank { null },
                            cpuThreads = threads,
                            supportsCloning = if (bengaliSelected) false else caps?.supportsCloning,
                            speakerEmbeddingDim = caps?.speakerEmbeddingDim ?: 0,
                            error = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            downloading = false,
                            loaded = false,
                            setupComplete = false,
                            modelReady = isSelectedModelReady(modelId),
                            status = "Model setup failed",
                            error = "${throwable.message ?: "Could not load the model"} You can re-download the model and retry.",
                        )
                    }
                },
            )
        }
    }

    private fun downloadFiles(variant: ModelVariant, forceRedownload: Boolean) {
        var completedBytes = 0L
        variant.files.forEachIndexed { index, file ->
            val target = File(modelDir, file.name)
            if (!forceRedownload && isValidModelFile(target, file)) {
                completedBytes += file.sizeBytes
                return@forEachIndexed
            }
            val url = file.url ?: error("${file.name} is a local experiment file. Use scripts/install_nvfp4_android.ps1 to sideload it.")
            val temp = File(modelDir, "${file.name}.download")
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 30_000
                readTimeout = 30_000
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    error("Model server returned HTTP $responseCode for ${file.name}")
                }
                connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var fileBytes = 0L
                        var lastUpdate = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            fileBytes += read
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 250L) {
                                lastUpdate = now
                                val total = completedBytes + fileBytes
                                _uiState.update {
                                    it.copy(
                                        downloadBytes = total,
                                        downloadProgress = total.toFloat() / variant.totalBytes.toFloat(),
                                        status = "Downloading ${index + 1}/${variant.files.size}: ${file.name}",
                                    )
                                }
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            if (temp.length() < file.sizeBytes || !isValidModelFile(temp, file)) {
                temp.delete()
                error("Downloaded ${file.name} is incomplete or is not a valid GGUF model. Please try again.")
            }
            if (target.exists() && !target.delete()) {
                temp.delete()
                error("Could not replace ${file.name}")
            }
            if (!temp.renameTo(target)) {
                temp.delete()
                error("Could not save ${file.name}")
            }
            completedBytes += file.sizeBytes
        }
    }

    private fun persistGeneratedAudio(
        text: String,
        samples: FloatArray,
        sampleRate: Int,
        voiceName: String,
        voiceId: String?,
        synthesisMillis: Long,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            generationDir.mkdirs()
            val file = File(generationDir, "generation-${System.currentTimeMillis()}.wav")
            file.outputStream().use { writeWav(it, samples, sampleRate) }
            dao.insertGeneration(
                GenerationEntity(
                    text = text,
                    voiceId = voiceId,
                    voiceName = voiceName,
                    wavPath = file.absolutePath,
                    sampleRate = sampleRate,
                    sampleCount = samples.size,
                    synthesisMillis = synthesisMillis,
                ),
            )
        }
    }

    private fun makeUniqueVoiceName(name: String): String {
        val existing = voices.value.map { it.name }.toSet()
        if (name !in existing) return name
        var index = 2
        while ("$name $index" in existing) index += 1
        return "$name $index"
    }

    private fun writeWav(output: OutputStream, samples: FloatArray, sampleRate: Int) {
        val dataBytes = samples.size * 2
        output.writeAscii("RIFF")
        output.writeIntLe(36 + dataBytes)
        output.writeAscii("WAVE")
        output.writeAscii("fmt ")
        output.writeIntLe(16)
        output.writeShortLe(1)
        output.writeShortLe(1)
        output.writeIntLe(sampleRate)
        output.writeIntLe(sampleRate * 2)
        output.writeShortLe(2)
        output.writeShortLe(16)
        output.writeAscii("data")
        output.writeIntLe(dataBytes)
        samples.forEach { sample ->
            val clamped = sample.coerceIn(-1f, 1f)
            output.writeShortLe((clamped * 32767f).toInt())
        }
    }

    override fun onCleared() {
        recorder.cancel()
        stopAudio()
        stopBusyTicker()
        engine?.close()
        engine = null
        bengaliEngine?.close()
        bengaliTranslator?.close()
        bengaliEngine = null
        super.onCleared()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            QwenTtsTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    QwenTtsApp()
                }
            }
        }
    }
}

private sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
) {
    object Setup : AppDestination("setup", "Setup", { Icon(Icons.Default.GraphicEq, contentDescription = null) })
    object Studio : AppDestination("studio", "Studio", { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) })
    object Voices : AppDestination("voices", "Voices", { Icon(Icons.Default.RecordVoiceOver, contentDescription = null) })
    object History : AppDestination("history", "History", { Icon(Icons.Default.History, contentDescription = null) })
    object Settings : AppDestination("settings", "Settings", { Icon(Icons.Default.Settings, contentDescription = null) })
}

private val appDestinations = listOf(
    AppDestination.Studio,
    AppDestination.Voices,
    AppDestination.History,
    AppDestination.Settings,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QwenTtsApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppDestination.Studio.route
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        val message = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearSnackbarMessage()
    }

    LaunchedEffect(state.setupComplete, currentRoute) {
        if (state.setupComplete && currentRoute == AppDestination.Setup.route) {
            navController.navigate(AppDestination.Voices.route) {
                popUpTo(AppDestination.Setup.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentRoute != AppDestination.Setup.route) {
                NavigationBar {
                    appDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(AppDestination.Studio.route) { saveState = true }
                                }
                            },
                            icon = destination.icon,
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Setup.route,
            modifier = Modifier.padding(padding),
            enterTransition = {
                val direction = if (destinationIndex(targetState.destination.route) >= destinationIndex(initialState.destination.route)) {
                    AnimatedContentTransitionScope.SlideDirection.Start
                } else {
                    AnimatedContentTransitionScope.SlideDirection.End
                }
                slideIntoContainer(direction, animationSpec = tween(260))
            },
            exitTransition = {
                val direction = if (destinationIndex(targetState.destination.route) >= destinationIndex(initialState.destination.route)) {
                    AnimatedContentTransitionScope.SlideDirection.Start
                } else {
                    AnimatedContentTransitionScope.SlideDirection.End
                }
                slideOutOfContainer(direction, animationSpec = tween(260))
            },
            popEnterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = tween(260))
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = tween(260))
            },
        ) {
            composable(AppDestination.Setup.route) {
                SetupScreen(
                    viewModel = viewModel,
                    onStart = viewModel::startSetup,
                    onModelChange = viewModel::selectModel,
                )
            }
            composable(AppDestination.Studio.route) {
                StudioScreen(viewModel)
            }
            composable(AppDestination.Voices.route) {
                VoicesScreen(viewModel)
            }
            composable(AppDestination.History.route) {
                HistoryScreen(viewModel)
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen(viewModel)
            }
        }
    }
}

private fun destinationIndex(route: String?): Int =
    appDestinations.indexOfFirst { it.route == route }.takeIf { it >= 0 } ?: 0

@Composable
private fun SetupScreen(viewModel: MainViewModel, onStart: (Boolean) -> Unit, onModelChange: (String) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val bengaliSelected = state.selectedModelId == BengaliModel.id

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text("Let’s set up Voice Notes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (bengaliSelected) {
                "Start once to install the Bengali voices and English to Bengali translation. Then choose a voice."
            } else {
                "Start once to download and load the speech model. When setup finishes, you’ll go straight to the voice creation screen."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose a speech model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            speechModelOptions.forEach { (id, label) ->
                FilterChip(
                    selected = state.selectedModelId == id,
                    onClick = { onModelChange(id) },
                    enabled = !state.busy,
                    label = { Text(label) },
                )
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(if (bengaliSelected) "Bengali text to speech" else "Speech model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(speechModelOptions.first { it.first == state.selectedModelId }.second, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (bengaliSelected) {
                        "About 95 MB speech model plus a small on-device translation model • 16 Bengali speakers • no voice cloning"
                    } else {
                        "About 0.8 GB • downloads to this device • voice cloning available"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.busy) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.downloading) {
                    LinearProgressIndicator(
                        progress = { state.downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    if (state.downloading) {
                        "${state.status} (${(state.downloadProgress * 100).toInt()}%)"
                    } else {
                        state.status
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Text(state.status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        state.error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = { onStart(state.error != null && state.modelReady) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.error != null && state.modelReady) "Re-download & retry" else "Start setup")
        }

        Text(
            "The model is downloaded only once. You can use the app offline after setup completes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val voices by viewModel.voices.collectAsState()
    var showVoicePicker by remember { mutableStateOf(false) }
    val bengaliSelected = state.selectedModelId == BengaliModel.id
    val selectedVoiceName = if (bengaliSelected) bengaliSpeakerOptions.firstOrNull { it.first == state.selectedBengaliSpeakerId }?.second ?: "Bengali voice"
    else voices.firstOrNull { it.voiceId == state.selectedVoiceId }?.name ?: "Default Voice"

    if (showVoicePicker) {
        if (bengaliSelected) {
            BengaliVoicePickerSheet(
                selectedSpeakerId = state.selectedBengaliSpeakerId,
                onDismiss = { showVoicePicker = false },
                onVoiceSelected = { speakerId ->
                    viewModel.selectBengaliSpeaker(speakerId)
                    showVoicePicker = false
                },
            )
        } else {
            VoicePickerSheet(
                voices = voices,
                selectedVoiceId = state.selectedVoiceId,
                onDismiss = { showVoicePicker = false },
                onVoiceSelected = { voiceId ->
                    viewModel.selectVoice(voiceId)
                    showVoicePicker = false
                },
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Text to speech", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            state.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            ComposerPanel(
                state = state,
                selectedVoiceName = selectedVoiceName,
                bengaliVoice = bengaliSelected,
                onTextChange = viewModel::updateText,
                onVoicePickerClick = { if (!state.busy) showVoicePicker = true },
                onLanguageChange = viewModel::updateLanguage,
                onTranslateEnglishChange = viewModel::setTranslateEnglishToBengali,
            )
            if ((state.busy && !state.downloading) || state.sampleCount > 0) {
                ResultPanel(
                    state = state,
                    onPlay = viewModel::playAudio,
                    onStop = viewModel::stopAudio,
                )
            }
        }

        Surface(
            tonalElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Button(
                onClick = viewModel::synthesize,
                enabled = state.modelReady && state.text.isNotBlank() && !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.busy && !state.downloading) "Generating..." else "Generate")
            }
        }
    }
}

@Composable
private fun VoicesScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val voices by viewModel.voices.collectAsState()
    val recorderState by viewModel.recorderState.collectAsState()
    val context = LocalContext.current
    var voiceName by remember { mutableStateOf("") }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) viewModel.startVoiceRecording()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        state.error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (state.selectedModelId == BengaliModel.id) {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bengali voices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Choose a male or female voice. More built-in speakers are available in Studio. Voice cloning is unavailable for Bengali.")
                    bengaliSpeakerOptions.take(2).forEach { (id, label) ->
                        FilterChip(
                            selected = state.selectedBengaliSpeakerId == id,
                            onClick = { viewModel.selectBengaliSpeaker(id) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        } else ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Text("New Voice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                OutlinedTextField(
                    value = voiceName,
                    onValueChange = { voiceName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Name") },
                )
                if (recorderState.isRecording) {
                    LinearProgressIndicator(
                        progress = { recorderState.level.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Recording ${formatDuration(recorderState.elapsedMillis)}", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (recorderState.isRecording) {
                                viewModel.stopVoiceRecordingAndCreate(voiceName)
                                voiceName = ""
                            } else if (hasPermission) {
                                viewModel.startVoiceRecording()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        enabled = !state.busy || recorderState.isRecording,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(if (recorderState.isRecording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (recorderState.isRecording) "Stop & Create" else "Record")
                    }
                    OutlinedButton(
                        onClick = viewModel::cancelVoiceRecording,
                        enabled = recorderState.isRecording,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                }
                if (state.supportsCloning == false) {
                    Text(
                        "The current model does not expose voice cloning capabilities.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (state.selectedModelId != BengaliModel.id) ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Voices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (voices.isEmpty()) {
                    Text("No saved voices yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    voices.forEach { voice ->
                        VoiceRow(
                            voice = voice,
                            selected = state.selectedVoiceId == voice.voiceId,
                            onSelect = { viewModel.selectVoice(voice.voiceId) },
                            onDelete = { viewModel.deleteVoice(voice) },
                        )
                    }
                }
            }
        }
    }
}

private fun shareGeneration(context: android.content.Context, generation: GenerationEntity) {
    val audioFile = File(generation.wavPath)
    check(audioFile.isFile) { "Audio file is no longer available" }
    val audioUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        audioFile,
    )
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/wav"
        putExtra(Intent.EXTRA_STREAM, audioUri)
        putExtra(Intent.EXTRA_SUBJECT, "Voice note")
        clipData = android.content.ClipData.newUri(context.contentResolver, "Voice note", audioUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Send voice note"))
}

@Composable
private fun HistoryScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val generations by viewModel.generations.collectAsState()
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<GenerationEntity?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/wav"),
    ) { uri ->
        val generation = pendingExport
        pendingExport = null
        if (uri != null && generation != null) {
            viewModel.exportGenerationToUri(generation, uri)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (generations.isEmpty()) {
            item {
                Text("No generated audio yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(generations, key = { it.generationId }) { generation ->
            val isPlayingThis = state.playing && state.playingGenerationId == generation.generationId
            var menuExpanded by remember(generation.generationId) { mutableStateOf(false) }
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(generation.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${generation.voiceName} · ${formatSeconds(generation.sampleCount.toDouble() / generation.sampleRate.coerceAtLeast(1))} · ${formatDate(generation.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            if (isPlayingThis) {
                                viewModel.stopAudio()
                            } else {
                                viewModel.playGeneration(generation.generationId)
                            }
                        },
                    ) {
                        Icon(
                            if (isPlayingThis) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isPlayingThis) "Stop" else "Play")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    runCatching { shareGeneration(context, generation) }
                                        .onFailure { throwable ->
                                            android.widget.Toast.makeText(
                                                context,
                                                throwable.message ?: "Could not share audio",
                                                android.widget.Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    pendingExport = generation
                                    exportLauncher.launch(viewModel.suggestedExportFileName(generation))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.deleteGeneration(generation)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ModelPanel(
            state = state,
            onModelChange = viewModel::selectModel,
            onDownload = viewModel::downloadModel,
            onLoad = viewModel::loadModel,
        )
        RuntimePanel(
            state = state,
            onCpuThreadsChange = viewModel::updateCpuThreads,
        )
    }
}

@Composable
private fun ComposerPanel(
    state: QwenTtsUiState,
    selectedVoiceName: String,
    bengaliVoice: Boolean,
    onTextChange: (String) -> Unit,
    onVoicePickerClick: () -> Unit,
    onLanguageChange: (Int) -> Unit,
    onTranslateEnglishChange: (Boolean) -> Unit,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                maxLines = 10,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                label = { Text("Text") },
            )
            OutlinedButton(
                onClick = onVoicePickerClick,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text("Voice", style = MaterialTheme.typography.labelMedium)
                    Text(selectedVoiceName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!bengaliVoice) {
                LanguageDropdown(
                    selectedLanguageId = state.selectedLanguageId,
                    enabled = !state.busy,
                    onLanguageChange = onLanguageChange,
                )
            } else {
                Text("Input language", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.translateEnglishToBengali,
                        onClick = { onTranslateEnglishChange(false) },
                        enabled = !state.busy,
                        label = { Text("Bengali text") },
                    )
                    FilterChip(
                        selected = state.translateEnglishToBengali,
                        onClick = { onTranslateEnglishChange(true) },
                        enabled = !state.busy,
                        label = { Text("English → Bengali") },
                    )
                }
                if (state.translateEnglishToBengali) {
                    Text("English text is translated on this device before speech generation. Translation by Google.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.translatedText?.let { translated -> Text(translated, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BengaliVoicePickerSheet(
    selectedSpeakerId: Int,
    onDismiss: () -> Unit,
    onVoiceSelected: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Choose Bengali voice", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            bengaliSpeakerOptions.forEach { (id, label) ->
                VoicePickerItem(
                    title = label,
                    subtitle = "Built-in speaker ${id + 1}",
                    selected = selectedSpeakerId == id,
                    onClick = { onVoiceSelected(id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePickerSheet(
    voices: List<VoiceProfileEntity>,
    selectedVoiceId: String?,
    onDismiss: () -> Unit,
    onVoiceSelected: (String?) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Choose voice", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            VoicePickerItem(
                title = "Default Voice",
                subtitle = "Use the built-in speaker",
                selected = selectedVoiceId == null,
                onClick = { onVoiceSelected(null) },
            )
            voices.forEach { voice ->
                VoicePickerItem(
                    title = voice.name,
                    subtitle = "${formatDuration(voice.durationMillis)} reference",
                    selected = selectedVoiceId == voice.voiceId,
                    onClick = { onVoiceSelected(voice.voiceId) },
                )
            }
        }
    }
}

@Composable
private fun VoicePickerItem(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Icon(Icons.Default.RecordVoiceOver, contentDescription = "Selected", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun VoiceRow(
    voice: VoiceProfileEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilterChip(selected = selected, onClick = onSelect, label = { Text(if (selected) "Active" else "Use") })
        Column(Modifier.weight(1f)) {
            Text(voice.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatDuration(voice.durationMillis)} reference · ${formatDate(voice.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    selectedLanguageId: Int,
    enabled: Boolean,
    onLanguageChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = languageOptions.firstOrNull { it.id == selectedLanguageId } ?: languageOptions.first()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            label = { Text("Language") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languageOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onLanguageChange(option.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelPanel(
    state: QwenTtsUiState,
    onModelChange: (String) -> Unit,
    onDownload: (Boolean) -> Unit,
    onLoad: () -> Unit,
) {
    val bengaliSelected = state.selectedModelId == BengaliModel.id
    val modelLabel = speechModelOptions.firstOrNull { it.first == state.selectedModelId }?.second ?: QwenModel.defaultVariant.displayName
    val modelBytes = if (bengaliSelected) BengaliModel.estimatedBytes else QwenModel.defaultVariant.totalBytes
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Speech model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            speechModelOptions.forEach { (id, label) ->
                FilterChip(
                    selected = state.selectedModelId == id,
                    onClick = { onModelChange(id) },
                    enabled = !state.busy,
                    label = { Text(label) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(modelLabel, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${formatBytes(modelBytes)} model package${if (bengaliSelected) " · no cloning" else " · cloning"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(onClick = {}, label = { Text(if (state.modelReady) "Ready" else "Missing") })
            }

            if (state.downloading) {
                LinearProgressIndicator(progress = { state.downloadProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${formatBytes(state.downloadBytes)} / ${formatBytes(state.downloadTotalBytes)}", style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onDownload(state.modelReady) },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.modelReady) "Re-download" else "Download model")
                }
                OutlinedButton(
                    onClick = onLoad,
                    enabled = state.modelReady && !state.loaded && !state.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.loaded) "Loaded" else "Load")
                }
            }
        }
    }
}

@Composable
private fun RuntimePanel(
    state: QwenTtsUiState,
    onCpuThreadsChange: (Int) -> Unit,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Text("Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "CPU backend",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4, 6).forEach { threads ->
                    FilterChip(
                        selected = state.selectedCpuThreads == threads,
                        onClick = { onCpuThreadsChange(threads) },
                        enabled = !state.busy,
                        label = { Text("$threads threads") },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultPanel(state: QwenTtsUiState, onPlay: () -> Unit, onStop: () -> Unit) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.busy && !state.downloading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                GenerationStats(state)
            } else {
                if (state.sampleCount > 0 && state.sampleRate > 0) {
                    Text(
                        "Audio ${formatSeconds(state.sampleCount.toDouble() / state.sampleRate.toDouble())} · generated in ${formatDuration(state.synthesisMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { if (state.playing) onStop() else onPlay() },
                    enabled = state.sampleCount > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(
                        if (state.playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.playing) "Stop" else "Play")
                }
            }
        }
    }
}

@Composable
private fun GenerationStats(state: QwenTtsUiState) {
    val generatedAudioSeconds = state.synthesisFrames * 0.08
    val remainingMillis = state.estimatedSynthesisMillis
        ?.minus(state.operationElapsedMillis)
        ?.coerceAtLeast(0L)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Elapsed ${formatDuration(state.operationElapsedMillis)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.synthesisFrames > 0) {
            Text(
                "Audio so far ~${formatSeconds(generatedAudioSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (remainingMillis != null && state.estimateSampleCount >= 3) {
            Text(
                "Estimated remaining ${formatDuration(remainingMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class WavData(val samples: FloatArray, val sampleRate: Int)

private fun readWav(path: String): WavData {
    val bytes = File(path).readBytes()
    require(bytes.size > 44) { "WAV file is empty" }
    fun intLe(offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
    fun shortLe(offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    var cursor = 12
    var sampleRate = 24_000
    var channels = 1
    var bits = 16
    var dataOffset = -1
    var dataSize = 0
    while (cursor + 8 <= bytes.size) {
        val id = String(bytes, cursor, 4, Charsets.US_ASCII)
        val size = intLe(cursor + 4)
        val payload = cursor + 8
        when (id) {
            "fmt " -> {
                channels = shortLe(payload + 2).coerceAtLeast(1)
                sampleRate = intLe(payload + 4)
                bits = shortLe(payload + 14)
            }
            "data" -> {
                dataOffset = payload
                dataSize = size
                break
            }
        }
        cursor = payload + size
    }
    require(dataOffset >= 0 && bits == 16) { "Only PCM16 WAV history items are supported" }
    val frameBytes = channels * 2
    val frameCount = dataSize / frameBytes
    val samples = FloatArray(frameCount)
    var offset = dataOffset
    for (i in 0 until frameCount) {
        var sum = 0f
        for (channel in 0 until channels) {
            val value = ((bytes[offset + 1].toInt() shl 8) or (bytes[offset].toInt() and 0xff)).toShort()
            sum += value / 32768f
            offset += 2
        }
        samples[i] = sum / channels
    }
    return WavData(samples, sampleRate)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return "%.1f %s".format(value, units[unitIndex])
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) "%d:%02d".format(Locale.US, minutes, seconds) else "%ds".format(Locale.US, seconds)
}

private fun formatSeconds(seconds: Double): String =
    "%.1fs".format(Locale.US, seconds.coerceAtLeast(0.0))

private fun formatDate(epochMillis: Long): String {
    val date = java.text.SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault())
    return date.format(java.util.Date(epochMillis))
}

private fun backendOptionById(id: String): BackendOption =
    backendOptions.firstOrNull { it.id == id } ?: defaultBackendOption

private fun OutputStream.writeAscii(value: String) {
    write(value.toByteArray(Charsets.US_ASCII))
}

private fun OutputStream.writeIntLe(value: Int) {
    write(value and 0xff)
    write((value shr 8) and 0xff)
    write((value shr 16) and 0xff)
    write((value shr 24) and 0xff)
}

private fun OutputStream.writeShortLe(value: Int) {
    write(value and 0xff)
    write((value shr 8) and 0xff)
}
