package io.github.numq.example.hub.presentation

import io.github.numq.example.feature.Reducer
import io.github.numq.example.feature.Transition

class HubInteractionReducer : Reducer<HubCommand.Interaction, HubState, HubEvent> {
    override suspend fun reduce(
        state: HubState, command: HubCommand.Interaction
    ): Transition<HubState, HubEvent> = when (command) {
        is HubCommand.Interaction.ShowFileChooser -> transition(state.copy(isFileChooserVisible = true))

        is HubCommand.Interaction.HideFileChooser -> transition(state.copy(isFileChooserVisible = false))

        is HubCommand.Interaction.ShowInputDialog -> transition(state.copy(isInputDialogVisible = true))

        is HubCommand.Interaction.HideInputDialog -> transition(state.copy(isInputDialogVisible = false))

        is HubCommand.Interaction.SetDragAndDropActive -> transition(
            state.copy(isInputDialogVisible = false, isDragAndDropActive = true)
        )

        is HubCommand.Interaction.SetDragAndDropInactive -> transition(state.copy(isDragAndDropActive = false))

        is HubCommand.Interaction.SetSliderStep -> transition(
            state.copy(
                sliderStep = command.step.coerceIn(
                    HubState.MIN_SLIDER_STEP, HubState.SLIDER_STEPS
                )
            )
        )
    }
}