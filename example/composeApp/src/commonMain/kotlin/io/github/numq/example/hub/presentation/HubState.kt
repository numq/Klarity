package io.github.numq.example.hub.presentation

import io.github.numq.example.hub.Hub

data class HubState(
    val hub: Hub = Hub(),
    val sliderSteps: Int = SLIDER_STEPS,
    val sliderStep: Int = MIN_SLIDER_STEP,
    val isFileChooserVisible: Boolean = false,
    val isInputDialogVisible: Boolean = false,
    val isDragAndDropActive: Boolean = false,
) {
    companion object {
        const val SLIDER_STEPS = 5
        const val MIN_SLIDER_STEP = 0
    }
}