package com.example.englishoralpractice.subtitle

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

data class SubtitleCue(
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

sealed class SrtParseResult {
    data class Success(val cues: List<SubtitleCue>) : SrtParseResult()
    data class Error(val message: String) : SrtParseResult()
}

object SrtParser {
    
    private val TIMESTAMP_PATTERN = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})"""
    )
    
    fun parse(context: Context, uri: Uri): SrtParseResult {
        return try {
            val content = readContent(context, uri)
                ?: return SrtParseResult.Error("Unable to read subtitle file")
            
            parseContent(content)
        } catch (e: SecurityException) {
            SrtParseResult.Error("Permission denied to read subtitle file")
        } catch (e: Exception) {
            SrtParseResult.Error("Failed to parse subtitle file: ${e.message}")
        }
    }
    
    private fun readContent(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun parseContent(content: String): SrtParseResult {
        val cues = mutableListOf<SubtitleCue>()
        val blocks = content.trim()
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split(Regex("\n\\s*\n"))
            .filter { it.isNotBlank() }
        
        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.size < 2) continue
            
            val indexLine = lines[0].trim()
            val index = indexLine.toIntOrNull()
            if (index == null) {
                continue
            }
            
            val timestampLine = lines[1].trim()
            val timestampMatch = TIMESTAMP_PATTERN.find(timestampLine)
            if (timestampMatch == null) {
                return SrtParseResult.Error("Invalid timestamp format at cue $index")
            }
            
            val (startH, startM, startS, startMs, endH, endM, endS, endMs) = 
                timestampMatch.destructured
            
            val startTimeMs = parseTimestampComponents(
                startH.toInt(), startM.toInt(), startS.toInt(), startMs.toInt()
            )
            val endTimeMs = parseTimestampComponents(
                endH.toInt(), endM.toInt(), endS.toInt(), endMs.toInt()
            )
            
            if (startTimeMs == null || endTimeMs == null) {
                return SrtParseResult.Error("Invalid timestamp values at cue $index")
            }
            
            val text = if (lines.size > 2) {
                lines.subList(2, lines.size).joinToString("\n").trim()
            } else {
                ""
            }
            
            cues.add(SubtitleCue(
                index = index,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                text = text
            ))
        }
        
        if (cues.isEmpty()) {
            return SrtParseResult.Error("No valid subtitle cues found")
        }
        
        return SrtParseResult.Success(cues.sortedBy { it.startTimeMs })
    }
    
    private fun parseTimestampComponents(hours: Int, minutes: Int, seconds: Int, millis: Int): Long? {
        if (minutes < 0 || minutes > 59 || seconds < 0 || seconds > 59 || millis < 0 || millis > 999) {
            return null
        }
        return (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L) + millis
    }
    
    fun parseTimestamp(timestamp: String): Long? {
        val parts = timestamp.trim().split(":", ",", ".")
        if (parts.size != 4) return null
        
        val hours = parts[0].toIntOrNull() ?: return null
        val minutes = parts[1].toIntOrNull() ?: return null
        val seconds = parts[2].toIntOrNull() ?: return null
        val millis = parts[3].toIntOrNull() ?: return null
        
        return parseTimestampComponents(hours, minutes, seconds, millis)
    }
}
