package com.qwen.tts.studio.engine

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/** Fixed-speaker Bengali VITS model; this engine does not support voice cloning. */
class BengaliTtsEngine(modelDir: File, numThreads: Int) : AutoCloseable {
    private val engine = OfflineTts(
        config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(modelDir, MODEL_FILE).absolutePath,
                    tokens = File(modelDir, TOKENS_FILE).absolutePath,
                ),
                numThreads = numThreads,
                provider = "cpu",
            ),
        ),
    )

    fun synthesize(text: String): Output {
        val generated = engine.generate(text = text)
        return Output(generated.samples, generated.sampleRate)
    }

    override fun close() {
        engine.release()
    }

    data class Output(val samples: FloatArray, val sampleRate: Int)

    companion object {
        const val MODEL_FILE = "model.onnx"
        const val TOKENS_FILE = "tokens.txt"
    }
}
