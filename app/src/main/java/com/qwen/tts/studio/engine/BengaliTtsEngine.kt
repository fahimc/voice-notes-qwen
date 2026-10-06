package com.qwen.tts.studio.engine

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/** Multi-speaker Bengali VITS model; speaker IDs are part of the model. */
class BengaliTtsEngine(modelDir: File, numThreads: Int) : AutoCloseable {
    private val modelFile = modelDir.walkTopDown().firstOrNull { it.isFile && it.extension == "onnx" }
        ?: error("Bengali model file is missing")
    private val tokensFile = modelDir.walkTopDown().firstOrNull { it.isFile && it.name == TOKENS_FILE }
        ?: error("Bengali tokens are missing")
    private val espeakDir = modelDir.walkTopDown().firstOrNull { it.isDirectory && it.name == "espeak-ng-data" }
        ?: error("Bengali pronunciation data is missing")
    private val engine = OfflineTts(
        config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = modelFile.absolutePath,
                    tokens = tokensFile.absolutePath,
                    dataDir = espeakDir.absolutePath,
                ),
                numThreads = numThreads,
                provider = "cpu",
            ),
        ),
    )

    fun synthesize(text: String, speakerId: Int): Output {
        require(speakerId in 0..15) { "Invalid Bengali speaker" }
        val generated = engine.generate(text = text, sid = speakerId)
        return Output(generated.samples, generated.sampleRate)
    }

    override fun close() {
        engine.release()
    }

    data class Output(val samples: FloatArray, val sampleRate: Int)

    companion object {
        const val TOKENS_FILE = "tokens.txt"
    }
}
