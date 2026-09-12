package com.velikececi.udfdonusturucu.ui.tools.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditHeaderFooter
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditParagraph
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditTable
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditTableCell
import com.velikececi.udfdonusturucu.ui.tools.editor.richtext.EditorFocusCoordinator
import com.velikececi.udfdonusturucu.ui.tools.editor.richtext.RichTextEditor
import com.velikececi.udfdonusturucu.ui.tools.editor.richtext.RichTextHandle

/** UDFBlockEditorViews.swift'teki `ParagraphBlockEditor` karşılığı. */
@Composable
fun ParagraphBlockEditor(
    paragraph: UdfEditParagraph,
    coordinator: EditorFocusCoordinator,
    onChange: (UdfEditParagraph) -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val handle = remember(paragraph.id) { RichTextHandle() }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Paragraf", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                onRemove?.let { remove ->
                    IconButton(onClick = remove) { Icon(Icons.Filled.Close, contentDescription = "Kaldır") }
                }
            }
            RichTextEditor(paragraph = paragraph, handle = handle, coordinator = coordinator, onParagraphChange = onChange, minLines = 3)
        }
    }
}

/** UDFBlockEditorViews.swift'teki tablo blok editörünün karşılığı. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableBlockEditor(
    table: UdfEditTable,
    coordinator: EditorFocusCoordinator,
    onTableChange: (UdfEditTable) -> Unit,
    onAddRow: () -> Unit,
    onAddColumn: () -> Unit,
    onRemoveRow: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var mergeTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tablo (${table.rows.size} satır)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { showAddMenu = true }) {
                        Icon(Icons.Filled.AddCircle, contentDescription = "Ekle")
                    }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                        DropdownMenuItem(text = { Text("Satır Ekle") }, onClick = { onAddRow(); showAddMenu = false })
                        DropdownMenuItem(text = { Text("Sütun Ekle") }, onClick = { onAddColumn(); showAddMenu = false })
                    }
                }
            }

            table.rows.forEachIndexed { rowIndex, row ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(
                        text = "Satır ${rowIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 10.dp, end = 4.dp),
                    )
                    Row(modifier = Modifier.weight(1f)) {
                        row.cells.forEachIndexed { cellIndex, cell ->
                            TableCellEditor(
                                cell = cell,
                                coordinator = coordinator,
                                onCellChange = { updated ->
                                    val newRows = table.rows.toMutableList()
                                    val newCells = row.cells.toMutableList()
                                    newCells[cellIndex] = updated
                                    newRows[rowIndex] = row.copy(cells = newCells)
                                    onTableChange(table.copy(rows = newRows))
                                },
                                onMergeClick = { mergeTarget = rowIndex to cellIndex },
                                modifier = Modifier.weight(cell.colspan.toFloat().coerceAtLeast(1f)),
                            )
                        }
                    }
                    IconButton(onClick = { onRemoveRow(rowIndex) }) {
                        Icon(Icons.Filled.RemoveCircle, contentDescription = "Satırı Sil", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    mergeTarget?.let { (rowIndex, cellIndex) ->
        CellMergeSheet(
            table = table,
            rowIndex = rowIndex,
            cellIndex = cellIndex,
            onApply = onTableChange,
            onDismiss = { mergeTarget = null },
        )
    }
}

@Composable
private fun TableCellEditor(
    cell: UdfEditTableCell,
    coordinator: EditorFocusCoordinator,
    onCellChange: (UdfEditTableCell) -> Unit,
    onMergeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val handle = remember(cell.id) { RichTextHandle() }
    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(2.dp),
    ) {
        cell.fillColorARGB?.let { argb ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color(argb)),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (cell.colspan > 1) {
                Text("C${cell.colspan}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
            }
            if (cell.rowspan > 1) {
                Text("R${cell.rowspan}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onMergeClick, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.CallSplit, contentDescription = "Birleştir / Böl", modifier = Modifier.size(14.dp))
            }
        }
        RichTextEditor(
            paragraph = cell.primaryParagraphValue(),
            handle = handle,
            coordinator = coordinator,
            onParagraphChange = { updated -> onCellChange(cell.withPrimaryParagraph(updated)) },
            minLines = 2,
        )
    }
}

/** UDFEditorView.swift'teki Header/Footer bölümlerinin karşılığı — [title] "Üst Bilgi (Header)" / "Alt Bilgi (Footer)". */
@Composable
fun HeaderFooterSection(
    title: String,
    items: List<UdfEditHeaderFooter>,
    coordinator: EditorFocusCoordinator,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onAddParagraph: (Int) -> Unit,
    onItemChange: (Int, UdfEditHeaderFooter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onAdd) { Text("Ekle") }
        }

        if (items.isEmpty()) {
            Text(
                text = "Henüz ${title.substringBefore(" (").lowercase()} yok.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items.forEachIndexed { index, item ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.type, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Sil")
                        }
                    }
                    item.paragraphs.forEachIndexed { pIndex, para ->
                        val handle = remember(para.id) { RichTextHandle() }
                        RichTextEditor(
                            paragraph = para,
                            handle = handle,
                            coordinator = coordinator,
                            onParagraphChange = { updated ->
                                val newParas = item.paragraphs.toMutableList()
                                newParas[pIndex] = updated
                                onItemChange(index, item.copy(paragraphs = newParas))
                            },
                            minLines = 1,
                        )
                    }
                    TextButton(onClick = { onAddParagraph(index) }) { Text("Paragraf Ekle") }
                }
            }
        }
    }
}
