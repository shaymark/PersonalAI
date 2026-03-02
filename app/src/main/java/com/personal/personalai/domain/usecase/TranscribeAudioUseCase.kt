package com.personal.personalai.domain.usecase

import com.personal.personalai.domain.repository.AiRepository
import java.io.File
import javax.inject.Inject

/**
 * Use case that delegates audio transcription to [AiRepository].
 * Follows the same thin-wrapper pattern as all other use cases in this project.
 */
class TranscribeAudioUseCase @Inject constructor(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(audioFile: File): Result<String> =
        aiRepository.transcribeAudio(audioFile)
}
