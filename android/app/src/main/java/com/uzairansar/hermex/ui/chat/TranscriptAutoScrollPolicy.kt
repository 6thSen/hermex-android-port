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

internal fun isTranscriptBottomVisible(
    totalItemsCount: Int,
    lastVisibleIndex: Int,
    lastVisibleOffset: Int,
    lastVisibleSize: Int,
    viewportEndOffset: Int,
    tolerancePixels: Int = 2,
): Boolean = totalItemsCount == 0 || (
    lastVisibleIndex == totalItemsCount - 1 &&
        lastVisibleOffset + lastVisibleSize <= viewportEndOffset + tolerancePixels
    )
