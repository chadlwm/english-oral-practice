package com.example.englishoralpractice.speech

/**
 * Speech Recognition Module - Empty Stub for P1
 * 
 * This module will be implemented in P1 phase and will include:
 * - ASR (Automatic Speech Recognition) integration
 * - Pronunciation scoring
 * - Word-by-word feedback
 * - Practice session management
 * 
 * Not implemented in P0:
 * - No ASR integration
 * - No scoring SDK
 * - No learning report
 */

// Placeholder interface for future speech recognition
interface SpeechRecognizer {
    fun startListening()
    fun stopListening()
    fun release()
}

// Placeholder for recognition results
data class SpeechResult(
    val text: String,
    val confidence: Float,
    val wordTimings: List<WordTiming>
)

data class WordTiming(
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float
)

// Placeholder for pronunciation assessment
interface PronunciationAssessor {
    fun assess(expected: String, actual: SpeechResult): PronunciationScore
}

data class PronunciationScore(
    val overallScore: Float,
    val wordScores: List<WordScore>
)

data class WordScore(
    val word: String,
    val score: Float,
    val feedback: String?
)
