package com.velikececi.udfdonusturucu.ui.tools.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditTable
import com.velikececi.udfdonusturucu.core.tools.editor.UdfTableMergeHelper

/**
 * CellMergeSheet.swift karşılığı — colspan/rowspan steppers + hızlı birleştirme aksiyonları.
 * [onApply] her zaman TAM tabloyu döner; çağıran taraf ilgili bloğu güncellemekle yükümlüdür.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellMergeSheet(
    table: UdfEditTable,
    rowIndex: Int,
    cellIndex: Int,
    onApply: (UdfEditTable) -> Unit,
    onDismiss: () -> Unit,
) {
    val row = table.rows.getOrNull(rowIndex)
    val cell = row?.cells?.getOrNull(cellIndex)
    if (row == null || cell == null) {
        onDismiss()
        return
    }

    val maxCols = UdfTableMergeHelper.logicalColumnCount(table)
    val start = UdfTableMergeHelper.columnStart(row, cellIndex)
    val maxColspan = maxOf(maxCols - start, 1)
    val maxRowspan = maxOf(table.rows.size - rowIndex, 1)

    var colspan by remember(rowIndex, cellIndex) { mutableStateOf(cell.colspan) }
    var rowspan by remember(rowIndex, cellIndex) { mutableStateOf(cell.rowspan) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Boyut", style = MaterialTheme.typography.titleMedium)
            StepperRow(label = "Colspan", value = colspan, range = 1..maxColspan, onChange = { colspan = it })
            StepperRow(label = "Rowspan", value = rowspan, range = 1..maxRowspan, onChange = { rowspan = it })

            Text("Hızlı Birleştirme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            TextButton(onClick = { onApply(UdfTableMergeHelper.mergeRight(table, rowIndex, cellIndex)); onDismiss() }) {
                Text("Sağdaki Hücreyle Birleştir (colspan)")
            }
            TextButton(onClick = { onApply(UdfTableMergeHelper.mergeDown(table, rowIndex, cellIndex)); onDismiss() }) {
                Text("Alttaki Hücreyle Birleştir (rowspan)")
            }
            TextButton(onClick = { onApply(UdfTableMergeHelper.splitCell(table, rowIndex, cellIndex)); onDismiss() }) {
                Text("Birleştirmeyi Kaldır", color = MaterialTheme.colorScheme.error)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("İptal") }
                Button(
                    onClick = {
                        var updated = UdfTableMergeHelper.setColspan(table, rowIndex, cellIndex, colspan)
                        updated = UdfTableMergeHelper.setRowspan(updated, rowIndex, cellIndex, rowspan)
                        onApply(updated)
                        onDismiss()
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Uygula") }
            }
        }
    }
}

@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = { if (value > range.first) onChange(value - 1) }) {
            Icon(Icons.Filled.Remove, contentDescription = "Azalt")
        }
        Text("$value", modifier = Modifier.padding(horizontal = 8.dp))
        IconButton(onClick = { if (value < range.last) onChange(value + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "Artır")
        }
    }
}
