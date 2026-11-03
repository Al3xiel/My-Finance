package com.al3xiel.my_finances.core.navigation

import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.al3xiel.my_finances.R

@Composable
fun RegisterFab(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick) {
        Icon(painter = painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.register_fab))
    }
}
