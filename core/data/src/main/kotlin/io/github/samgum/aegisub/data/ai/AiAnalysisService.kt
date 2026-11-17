package io.github.samgum.aegisub.data.ai

import android.os.Build
import io.github.samgum.aegisub.data.settings.UserSettings
import io.github.samgum.aegisub.domain.sgmy.ChineseConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SGMY AI 字幕语法分析与辅助翻译服务（完整复刻 samgum/Aegisub `dialog_ai_analysis.cpp`）：
 * - 内置与桌面 SGMY 完全一致的 5 大 OpenAI 兼容服务商预设
 * - API Key 默认仅驻留内存会话（`sessionApiKey`），绝不落盘明文泄漏
 * - 支持「语法/句意拆解」「单行/上下文翻译（保留 ASS 标签）」「字幕润色校对」三种模式
 *
 * @author 伤感咩吖
 */
@Singleton
class AiAnalysisService @Inject constructor() {

    data class ProviderPreset(
        val name: String,
        val baseUrl: String,
        val defaultModel: String,
    )

    enum class TaskMode {
        GRAMMAR_ANALYSIS,
        TRANSLATE_LINE,
        POLISH_SUBTITLE,
    }

    companion object {
        val PRESETS = listOf(
            ProviderPreset("DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
            ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1", "deepseek/deepseek-chat"),
            ProviderPreset("Gemini (OpenAI 兼容)", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
            ProviderPreset("阿里云百炼 (DashScope)", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
            ProviderPreset("自定义 OpenAI 兼容端点", "https://api.openai.com/v1", "gpt-4o-mini"),
        )
    }

    @Volatile
    var sessionApiKey: String = ""

    private val cache = ConcurrentHashMap<String, String>()

    private fun resolveEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
    }

    suspend fun runTask(
        subtitleText: String,
        contextLines: List<String> = emptyList(),
        mode: TaskMode = TaskMode.GRAMMAR_ANALYSIS,
        settings: UserSettings,
        overrideApiKey: String? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        val key = (overrideApiKey ?: sessionApiKey).trim()
        if (key.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("请先在 AI 设置中输入 API Key（仅保存在当前运行内存中）"))
        }
        if (subtitleText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("当前行字幕内容为空"))
        }

        val cacheKey = "${mode.name}|${settings.aiBaseUrl}|${settings.aiModel}|${settings.aiTargetLang}|$subtitleText"
        if (settings.aiCacheSession) {
            cache[cacheKey]?.let { return@withContext Result.success(it) }
        }

        val systemPrompt = when (mode) {
            TaskMode.GRAMMAR_ANALYSIS ->
                "You are an expert subtitle linguist and translator in Aegisub SGMY. " +
                    "Explain the grammar, vocabulary, idioms, tone, and natural ${settings.aiTargetLang} translation " +
                    "of the given subtitle line concisely and clearly in ${settings.aiTargetLang}."
            TaskMode.TRANSLATE_LINE ->
                "You are a professional subtitle translator in Aegisub SGMY. " +
                    "Translate the subtitle line into natural, concise ${settings.aiTargetLang} suitable for on-screen subtitles. " +
                    "Preserve any ASS override tags in {...} and line breaks \\N intact. Output ONLY the translated subtitle line."
            TaskMode.POLISH_SUBTITLE ->
                "You are a senior subtitle editor in Aegisub SGMY. " +
                    "Check for typos, awkward phrasing, punctuation issues, or excessive length, and provide a polished version " +
                    "plus a 1-sentence note in ${settings.aiTargetLang}. Preserve ASS {...} tags."
        }

        val userPrompt = buildString {
            if (contextLines.isNotEmpty()) {
                appendLine("Surrounding context lines:")
                contextLines.forEach { appendLine("- $it") }
                appendLine()
            }
            append("Target subtitle line:\n$subtitleText")
        }

        runCatching {
            val payload = JSONObject().apply {
                put("model", settings.aiModel.ifBlank { "deepseek-chat" })
                put("temperature", settings.aiTemperature.toDouble())
                put("max_tokens", settings.aiMaxTokens)
                put(
                    "messages",
                    JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", systemPrompt))
                        put(JSONObject().put("role", "user").put("content", userPrompt))
                    },
                )
            }

            val conn = (URL(resolveEndpoint(settings.aiBaseUrl)).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $key")
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            val rawResp = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            conn.disconnect()

            if (code !in 200..299) {
                error("HTTP $code: ${rawResp.take(400)}")
            }

            val json = JSONObject(rawResp)
            val content = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                .orEmpty()
            if (content.isEmpty()) error("AI 返回内容为空")

            if (settings.aiCacheSession) {
                cache[cacheKey] = content
            }
            content
        }
    }
}

/**
 * Android 平台简繁转换桥接：API 29+ 优先调用系统原生 ICU `Transliterator`，
 * 低版本或宿主机单测自动回退到 [ChineseConverter.fallbackTransliterate]。
 */
object PlatformChineseTransliterator {
    fun convert(text: String, toTraditional: Boolean): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val id = if (toTraditional) "Simplified-Traditional" else "Traditional-Simplified"
            val result = runCatching {
                android.icu.text.Transliterator.getInstance(id).transliterate(text)
            }.getOrNull()
            if (result != null) return result
        }
        return ChineseConverter.fallbackTransliterate(text, toTraditional)
    }
}
