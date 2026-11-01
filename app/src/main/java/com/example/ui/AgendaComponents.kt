package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val AGENDA_STATUS_OPTIONS = listOf("NOVÁ", "V ŘEŠENÍ", "REALIZOVÁNO", "ZRUŠENO")
val TRANSPORT_STATUS_OPTIONS = listOf("NOVÁ", "HOTOVO", "ZRUŠENO")

fun matchesAgendaStatusTab(rawStatus: String, selectedSubTab: String): Boolean {
    val s = rawStatus.trim().uppercase()
    return when (selectedSubTab) {
        "ZRUŠENÉ", "ZRUŠENO" -> s == "ZRUŠENO" || s == "ZRUŠENÉ" || s == "ZRUŠENÁ" || s == "STORNO"
        "REALIZOVANÉ", "HOTOVO" -> s == "REALIZOVÁNO" || s == "REALIZOVANÉ" || s == "REALIZOVANÁ" || s == "VYŘÍZENO" || s == "HOTOVO" || s == "DOKONČENO"
        else -> {
            s != "ZRUŠENO" && s != "ZRUŠENÉ" && s != "ZRUŠENÁ" && s != "STORNO" &&
                s != "REALIZOVÁNO" && s != "REALIZOVANÉ" && s != "REALIZOVANÁ" && s != "VYŘÍZENO" && s != "HOTOVO" && s != "DOKONČENO"
        }
    }
}

fun formatTransportStatusLabel(rawStatus: String): String {
    val s = rawStatus.trim().uppercase()
    return when {
        s == "ZRUŠENO" || s == "ZRUŠENÉ" || s == "ZRUŠENÁ" || s == "STORNO" -> "ZRUŠENO"
        s == "HOTOVO" || s == "REALIZOVÁNO" || s == "REALIZOVANÉ" || s == "REALIZOVANÁ" || s == "VYŘÍZENO" || s == "DOKONČENO" -> "HOTOVO"
        else -> "NOVÁ"
    }
}

fun formatAgendaStatusLabel(rawStatus: String): String {
    val s = rawStatus.trim().uppercase()
    return when {
        s == "ZRUŠENO" || s == "ZRUŠENÉ" || s == "ZRUŠENÁ" || s == "STORNO" -> "ZRUŠENO"
        s == "REALIZOVÁNO" || s == "REALIZOVANÉ" || s == "REALIZOVANÁ" || s == "VYŘÍZENO" || s == "HOTOVO" || s == "DOKONČENO" -> "REALIZOVÁNO"
        s == "V ŘEŠENÍ" || s == "ŘEŠENÍ" -> "V ŘEŠENÍ"
        s == "ČEKÁ" || s == "NOVÁ" || s == "NOVÉ" || s.isBlank() -> "NOVÁ"
        else -> rawStatus.trim().uppercase()
    }
}

fun agendaStatusColor(rawStatus: String): Color {
    val s = rawStatus.trim().uppercase()
    if (s == "HOTOVO") return Color(0xFF15803D)
    return when (formatAgendaStatusLabel(rawStatus)) {
        "NOVÁ" -> Color(0xFF0284C7)
        "V ŘEŠENÍ" -> Color(0xFFF57C00)
        "REALIZOVÁNO", "HOTOVO" -> Color(0xFF15803D)
        "ZRUŠENO" -> Color(0xFFB91C1C)
        else -> Color(0xFF334155)
    }
}

@Composable
fun AgendaStatusSubMenuBar(
    selectedSubTab: String,
    onSubTabSelected: (String) -> Unit,
    noveCount: Int,
    realizovaneCount: Int,
    zruseneCount: Int,
    isDepo2: Boolean = false,
    noveLabel: String = "Nové",
    realizovaneLabel: String = "Realizované",
    zruseneLabel: String = "Zrušené"
) {
    val barBg = if (isDepo2) Color(0xFF3B2308) else Color(0xFF0B1F36)
    val tabs = listOf(
        Triple("NOVÉ", "$noveLabel ($noveCount)", Color(0xFF0284C7)),
        Triple("REALIZOVANÉ", "$realizovaneLabel ($realizovaneCount)", Color(0xFF15803D)),
        Triple("ZRUŠENÉ", "$zruseneLabel ($zruseneCount)", Color(0xFF881337))
    )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(barBg, RoundedCornerShape(8.dp))
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { idx, (key, label, activeCol) ->
                val isSelected = selectedSubTab == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (isSelected) activeCol else Color.Transparent, RoundedCornerShape(6.dp))
                        .clickable { onSubTabSelected(key) }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun SingleLineDepoBadge(
    depoText: String,
    badgeColor: Color,
    onDepoSelected: ((String) -> Unit)? = null,
    includeBothOption: Boolean = false,
    onBothSelected: (() -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
    modifier: Modifier = Modifier.height(24.dp)
) {
    var expanded by remember { mutableStateOf(false) }
    val normalizedLabel = when {
        depoText.contains("2") -> "DEPO 2"
        depoText.contains("1") -> "DEPO 1"
        depoText.isBlank() -> "DEPO 1"
        else -> depoText.uppercase()
    }

    Box {
        Surface(
            color = badgeColor,
            shape = shape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.85f)),
            modifier = (if (onDepoSelected != null) {
                Modifier.clickable { expanded = true }
            } else {
                Modifier
            }).then(modifier)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 7.dp)
            ) {
                Text(
                    text = normalizedLabel,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    softWrap = false
                )
                if (onDepoSelected != null) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        if (onDepoSelected != null) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = Color.White
            ) {
                DropdownMenuItem(
                    text = { Text("DEPO 1", fontWeight = FontWeight.ExtraBold, color = Color(0xFF0D47A1)) },
                    onClick = {
                        expanded = false
                        onDepoSelected("Depo 1")
                    }
                )
                DropdownMenuItem(
                    text = { Text("DEPO 2", fontWeight = FontWeight.ExtraBold, color = Color(0xFFE65100)) },
                    onClick = {
                        expanded = false
                        onDepoSelected("Depo 2")
                    }
                )
                if (includeBothOption && onBothSelected != null) {
                    DropdownMenuItem(
                        text = { Text("OBĚ DEPA (rozdělit)", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A)) },
                        onClick = {
                            expanded = false
                            onBothSelected()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StatusDropdownBadge(
    currentStatus: String,
    options: List<String> = AGENDA_STATUS_OPTIONS,
    onStatusSelected: (String) -> Unit,
    customColor: Color? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
    modifier: Modifier = Modifier.height(24.dp)
) {
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = when (options) {
        TRANSPORT_STATUS_OPTIONS -> formatTransportStatusLabel(currentStatus)
        AGENDA_STATUS_OPTIONS -> formatAgendaStatusLabel(currentStatus)
        else -> currentStatus.ifBlank { options.firstOrNull() ?: "NOVÁ" }
    }
    val bgCol = customColor ?: agendaStatusColor(displayLabel)

    Box {
        Surface(
            color = bgCol,
            shape = shape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.85f)),
            modifier = modifier
                .clickable { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(start = 7.dp, end = 3.dp)
            ) {
                Text(
                    text = displayLabel,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    softWrap = false
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Změnit status",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color.White
        ) {
            options.forEach { opt ->
                val optColor = if (options == AGENDA_STATUS_OPTIONS || options == TRANSPORT_STATUS_OPTIONS) agendaStatusColor(opt) else Color(0xFF0F172A)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = opt,
                            fontWeight = FontWeight.ExtraBold,
                            color = optColor
                        )
                    },
                    onClick = {
                        expanded = false
                        onStatusSelected(opt)
                    }
                )
            }
        }
    }
}

@Composable
fun AgendaLetterIconBadge(
    letter: String,
    textColor: Color = Color(0xFF0D47A1)
) {
    val isMultiChar = letter.length > 1
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.95f)),
        shadowElevation = 1.dp,
        modifier = Modifier
            .height(24.dp)
            .widthIn(min = 24.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = if (isMultiChar) 5.dp else 0.dp)
        ) {
            Text(
                text = letter,
                color = textColor,
                fontWeight = FontWeight.Black,
                fontSize = if (isMultiChar) 11.sp else 13.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }
    }
}

