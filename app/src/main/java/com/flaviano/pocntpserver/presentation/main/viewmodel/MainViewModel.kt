package com.flaviano.pocntpserver.presentation.main.viewmodel

import androidx.lifecycle.ViewModel
import com.flaviano.pocntpserver.domain.usecase.GetTrueTimeNowUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


class MainViewModel(val getTrueTimeNowUseCase: GetTrueTimeNowUseCase) : ViewModel(), MainUIAction {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    override fun getDateUpdated() {
        getTrueTimeNowUseCase().onSuccess { date ->
            val dateFormat = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(date.time),
                ZoneId.of(ZONE_ID)
            ).format(DateTimeFormatter.ofPattern(FORMAT_DATE_PATTERN))
            _uiState.update { it.copy(date = dateFormat) }
        }.onFailure {
            // write here feedback when trueTime is not initialized
        }
    }

    companion object {
        const val ZONE_ID = "America/Sao_Paulo"
        const val FORMAT_DATE_PATTERN = "dd/MM/yyyy  HH:mm:ss"
    }

}