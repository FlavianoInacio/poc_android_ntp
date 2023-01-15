package com.flaviano.pocntpserver.presentation.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flaviano.pocntpserver.R
import com.flaviano.pocntpserver.presentation.main.viewmodel.MainUIAction
import com.flaviano.pocntpserver.presentation.main.viewmodel.MainUiState

@Composable
fun MainScreen(uiState: MainUiState, uiAction: MainUIAction) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = uiState.date,
            textAlign = TextAlign.Center,
            fontSize = 30.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { uiAction.getDateUpdated() }) {
            Text(text = stringResource(id = R.string.get_date_time_updated), fontSize = 16.sp)
        }
    }
}