package com.velikececi.udfdonusturucu.ui.tools.editor.richtext

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private data class NamedColor(val label: String, val argb: Int?)

// UDFBlockEditorViews.swift'teki menülerle birebir aynı (Yok dahil).
private val TEXT_COLORS = listOf(
    NamedColor("Siyah", 0xFF000000.toInt()),
    NamedColor("Kırmızı", 0xFFD32F2F.toInt()),
    NamedColor("Lacivert", 0xFF1C2E52.toInt()),
    NamedColor("Yeşil", 0xFF2E7D32.toInt()),
    NamedColor("Mavi", 0xFF1976D2.toInt()),
)
private val HIGHLIGHT_COLORS = listOf(
    NamedColor("Yok", null),
    NamedColor("Sarı", 0x66FFEB3B.toInt()),
    NamedColor("Gri", 0x669E9E9E.toInt()),
    NamedColor("Turkuaz", 0x6600BCD4.toInt()),
)

/**
 * RichTextFormattingToolbar.swift karşılığı — [coordinator]'daki o an odaklı editöre uygulanır.
 * Odaklı bir editör yoksa (henüz hiçbir bloğa dokunulmadıysa) devre dışı görünür ama çökmez.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormattingToolbar(
    coordinator: EditorFocusCoordinator,
    onInsertField: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTextColorMenu by remember { mutableStateOf(false) }
    var showHighlightMenu by remember { mutableStateOf(false) }

    Surface(tonalElevation = 2.dp, modifier = modifier) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = { coordinator.activeHandle?.toggleBold() }) {
                Icon(Icons.Filled.FormatBold, contentDescription = "Kalın")
            }
            IconButton(onClick = { coordinator.activeHandle?.toggleItalic() }) {
                Icon(Icons.Filled.FormatItalic, contentDescription = "İtalik")
            }
            IconButton(onClick = { coordinator.activeHandle?.toggleUnderline() }) {
                Icon(Icons.Filled.FormatUnderlined, contentDescription = "Altı Çizili")
            }

            Box {
                IconButton(onClick = { showTextColorMenu = true }) {
                    Icon(Icons.Filled.FormatColorText, contentDescription = "Metin Rengi")
                }
                DropdownMenu(expanded = showTextColorMenu, onDismissRequest = { showTextColorMenu = false }) {
                    TEXT_COLORS.forEach { color ->
                        DropdownMenuItem(
                            text = { Text(color.label) },
                            leadingIcon = {
                                ColorSwatch(color.argb ?: 0x00000000)
                            },
                            onClick = {
                                coordinator.activeHandle?.setForegroundColor(color.argb)
                                showTextColorMenu = false
                            },
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { showHighlightMenu = true }) {
                    Icon(Icons.Filled.FormatColorFill, contentDescription = "Vurgu Rengi")
                }
                DropdownMenu(expanded = showHighlightMenu, onDismissRequest = { showHighlightMenu = false }) {
                    HIGHLIGHT_COLORS.forEach { color ->
                        DropdownMenuItem(
                            text = { Text(color.label) },
                            leadingIcon = { color.argb?.let { ColorSwatch(it) } },
                            onClick = {
                                coordinator.activeHandle?.setHighlightColor(color.argb)
                                showHighlightMenu = false
                            },
                        )
                    }
                }
            }

            IconButton(onClick = onInsertField) {
                Icon(Icons.Filled.PostAdd, contentDescription = "UYAP Alanı Ekle")
            }

            IconButton(onClick = { coordinator.setAlignment(0) }) {
                Icon(Icons.Filled.FormatAlignLeft, contentDescription = "Sola Yasla")
            }
            IconButton(onClick = { coordinator.setAlignment(1) }) {
                Icon(Icons.Filled.FormatAlignCenter, contentDescription = "Ortala")
            }
            IconButton(onClick = { coordinator.setAlignment(3) }) {
                Icon(Icons.Filled.FormatAlignJustify, contentDescription = "İki Yana Yasla")
            }
            IconButton(onClick = { coordinator.setAlignment(2) }) {
                Icon(Icons.Filled.FormatAlignRight, contentDescription = "Sağa Yasla")
            }
        }
    }
}

@Composable
private fun ColorSwatch(argb: Int) {
    Surface(
        color = Color(argb),
        modifier = Modifier.size(18.dp),
    ) {}
}
