package ru.max.superresolution.monitor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MonitorRepository {
  private val _uiState = MutableStateFlow(MonitorUiState())
  val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

  fun update(state: MonitorUiState) {
    _uiState.value = state
  }
}
