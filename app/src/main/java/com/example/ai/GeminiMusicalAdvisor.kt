package com.example.ai

import com.example.BuildConfig
import com.example.model.AudioAnalysisResult
import com.example.model.MusicalKey
import com.example.model.RhythmStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiMusicalAdvisor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class AiMusicalAdvice(
        val recommendedStyle: RhythmStyle,
        val recommendedBpmAdjustment: String,
        val suggestedProgression: String,
        val arrangementTip: String,
        val musicalExplanation: String
    )

    suspend fun getMusicalAdvice(
        analysis: AudioAnalysisResult,
        userPromptExtra: String = ""
    ): AiMusicalAdvice = withContext(Dispatchers.IO) {
        val apiKey = try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            field.get(null) as? String ?: ""
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                return@withContext queryGemini(analysis, apiKey)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Offline / intelligent local fallback engine
        getOfflineMusicalAdvice(analysis)
    }

    private fun queryGemini(analysis: AudioAnalysisResult, apiKey: String): AiMusicalAdvice {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val prompt = """
            You are a Bollywood music producer and arranger.
            Given this vocal analysis:
            - Detected BPM: ${analysis.detectedBpm} (Confidence: ${analysis.bpmConfidence}%)
            - Musical Key: ${analysis.musicalKey.displayName} (Confidence: ${analysis.keyConfidence}%)
            - Time Signature: ${analysis.timeSignature.displayName}
            - Pitch Range: ${analysis.pitchRangeMinNote} to ${analysis.pitchRangeMaxNote} (${analysis.pitchRangeMinHz.toInt()}Hz - ${analysis.pitchRangeMaxHz.toInt()}Hz)
            - Duration: ${(analysis.durationMs / 1000)} seconds
            - Vocal Phrases: ${analysis.phrases.size} phrases detected
            
            Recommend:
            1. Best Rhythm Style (one of: Bollywood, Romantic Bollywood, Bollywood Dance, Indian Pop, Acoustic, Rock, Pop, Hip-Hop, Lo-Fi, Garba, Gujarati Folk, Devotional)
            2. BPM adjustment suggestion (e.g. Keep at ${analysis.detectedBpm} or round to nearest whole)
            3. Best chord progression for ${analysis.musicalKey.displayName}
            4. Song arrangement tip for this vocal phrasing
            5. Short explanation of the detected musical characteristics.
            
            Return in strict JSON format:
            {
               "style": "BOLLYWOOD",
               "bpmAdjustment": "...",
               "progression": "...",
               "arrangementTip": "...",
               "explanation": "..."
            }
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("API error: ${response.code}")
            val respStr = response.body?.string() ?: throw IllegalStateException("Empty response")
            val root = JSONObject(respStr)
            val text = root.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val parsed = JSONObject(text)
            val styleStr = parsed.optString("style", "BOLLYWOOD").uppercase()
            val style = RhythmStyle.entries.firstOrNull { it.name == styleStr || it.title.uppercase() == styleStr }
                ?: RhythmStyle.BOLLYWOOD

            return AiMusicalAdvice(
                recommendedStyle = style,
                recommendedBpmAdjustment = parsed.optString("bpmAdjustment", "Keep at ${analysis.detectedBpm} BPM"),
                suggestedProgression = parsed.optString("progression", "i - VII - VI - V"),
                arrangementTip = parsed.optString("arrangementTip", "Begin with a soft tabla/shaker intro before bringing full drums."),
                musicalExplanation = parsed.optString("explanation", "Vocal lies smoothly in ${analysis.musicalKey.displayName} with strong rhythmic pulse.")
            )
        }
    }

    private fun getOfflineMusicalAdvice(analysis: AudioAnalysisResult): AiMusicalAdvice {
        val bpm = analysis.detectedBpm
        val isMinor = analysis.musicalKey.scale == com.example.model.ScaleType.MINOR

        val recommendedStyle = when {
            bpm in 80.0..102.0 && isMinor -> RhythmStyle.BOLLYWOOD
            bpm < 85.0 -> RhythmStyle.ROMANTIC_BOLLYWOOD
            bpm > 115.0 -> RhythmStyle.BOLLYWOOD_DANCE
            bpm in 103.0..115.0 -> RhythmStyle.INDIAN_POP
            else -> RhythmStyle.BOLLYWOOD
        }

        val progression = if (isMinor) {
            "${analysis.musicalKey.tonic.displayName}m → E → B → F# (or ${analysis.musicalKey.tonic.displayName}m → D# → C#m → D#)"
        } else {
            "${analysis.musicalKey.tonic.displayName} → G → Am → F (Classic Pop/Film Cadence)"
        }

        val arrangementTip = if (analysis.phrases.size <= 4) {
            "Vocal is concise (~${analysis.durationMs / 1000}s). Use a 2-bar Intro, followed by full vocal + rhythm, ending with a 2-bar ring-out."
        } else {
            "Vocal has ${analysis.phrases.size} distinct phrases. Start with subtle percussion on Phrase 1, and introduce the full kick + bass on Phrase 2 for maximum emotional impact."
        }

        val explanation = "The vocal displays a vocal frequency span from ${analysis.pitchRangeMinNote} to ${analysis.pitchRangeMaxNote}. " +
                "Autocorrelation spectral analysis indicates a primary tempo of ${analysis.detectedBpm} BPM with ${analysis.bpmConfidence.toInt()}% confidence in ${analysis.musicalKey.displayName}."

        return AiMusicalAdvice(
            recommendedStyle = recommendedStyle,
            recommendedBpmAdjustment = "Optimal tempo is ${analysis.detectedBpm} BPM",
            suggestedProgression = progression,
            arrangementTip = arrangementTip,
            musicalExplanation = explanation
        )
    }
}
