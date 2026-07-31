package com.uzairansar.hermex.ui.chat

internal data class TranscriptScrollObservation(
    val isUserDragging: Boolean,
    val lastScrolledBackward: Boolean,
    val isNearBottom: Boolean,
)

internal fun transcriptFollowState(
    currentlyFollowing: Boolean,
    observation: TranscriptScrollObservation,
): Boolean = when {
    !observation.isUserDragging -> currentlyFollowing
    observation.lastScrolledBackward -> false
    observation.isNearBottom -> true
    else -> currentlyFollowing
}

internal fun shouldAutoScrollTranscript(
    followsBottom: Boolean,
    isScrollInProgress: Boolean,
): Boolean = followsBottom && !isScrollInProgress
