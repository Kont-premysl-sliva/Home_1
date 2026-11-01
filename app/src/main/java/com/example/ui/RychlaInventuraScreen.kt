package com.example.ui

import androidx.compose.runtime.Composable
import com.example.data.InventoryRecord

@Composable
fun RychlaInventuraScreen(
    viewModel: MainViewModel,
    existingInventory: InventoryRecord? = null,
    onOpenDrawer: () -> Unit = {},
    onNavigateBack: () -> Unit = onOpenDrawer
) {
    InventuryScreen(
        viewModel = viewModel,
        onOpenDrawer = onOpenDrawer,
        onNavigateBack = onNavigateBack,
        onNavigateToRychlaInventura = {}
    )
}
