package com.clairedoc.app.pipeline

import android.util.Log
import com.clairedoc.app.data.model.DocumentResultDto
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import javax.inject.Inject

private const val TAG = "ClaireDoc_JsonParser"

/**
 * Defensively extracts a [DocumentResultDto] from raw model output.
 *
 * Handles the two most common failure modes:
 *  1. Model wraps JSON in markdown fences  (```json … ```)
 *  2. Model prepends/appends explanatory text around the JSON object
 */
class JsonParser @Inject constructor(
    private val gson: Gson
) {
    // Matches ``` optionally followed by "json", captures inner content.
    private val fencePattern = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```")

    // Finds the outermost balanced {...} block in the string.
    // Used as last-resort extraction when fences are absent.
    private val jsonObjectPattern = Regex("\\{[\\s\\S]*\\}")

    /**
     * Returns a [DocumentResultDto] or null if the raw string cannot be
     * parsed at all. The caller is responsible for handling null as an Error.
     */
    fun parse(raw: String): DocumentResultDto? {
        val cleaned = extractJson(raw.trim())
        return try {
            gson.fromJson(cleaned, DocumentResultDto::class.java)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON parse failed. cleaned=[$cleaned]", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected parse error", e)
            null
        }
    }

    private fun extractJson(raw: String): String {
        // 1. Try markdown fence extraction first
        val fenceMatch = fencePattern.find(raw)
        if (fenceMatch != null) return fenceMatch.groupValues[1].trim()

        // 2. Try finding the first {...} block
        val objMatch = jsonObjectPattern.find(raw)
        if (objMatch != null) return objMatch.value

        // 3. Return raw string and let Gson report the error
        return raw
    }
}
