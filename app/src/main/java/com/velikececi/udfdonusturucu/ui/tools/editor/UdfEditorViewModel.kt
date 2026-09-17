package com.velikececi.udfdonusturucu.ui.tools.editor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditBlock
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditDocument
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditHeaderFooter
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditParagraph
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditRun
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditTable
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditTableCell
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditTableRow
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditableDocument
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditorService
import com.velikececi.udfdonusturucu.core.tools.editor.UdfTableMergeHelper
import com.velikececi.udfdonusturucu.core.tools.editor.plainText
import com.velikececi.udfdonusturucu.data.ConversionRecord
import com.velikececi.udfdonusturucu.data.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UdfEditorUiState(
    val isLoading: Boolean = false,
    val editable: UdfEditableDocument? = null,
    val document: UdfEditDocument? = null,
    val originalFingerprint: String? = null,
    val isSaving: Boolean = false,
    val savedFile: File? = null,
    val errorMessage: String? = null,
) {
    val hasChanges: Boolean
        get() {
            val doc = document ?: return false
            val original = originalFingerprint ?: return false
            return UdfEditorService.fingerprint(doc) != original
        }

    val canSave: Boolean
        get() {
            val doc = document ?: return false
            if (editable == null || isSaving || !hasChanges) return false
            return doc.plainText().isNotBlank()
        }
}

/** UDFEditorView.swift'in durum yönetimi karşılığı. */
class UdfEditorViewModel(
    private val context: Context,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UdfEditorUiState())
    val uiState: StateFlow<UdfEditorUiState> = _uiState

    fun load(file: File) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val editable = withContext(Dispatchers.Default) { UdfEditorService.load(file) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        editable = editable,
                        document = editable.model,
                        originalFingerprint = UdfEditorService.fingerprint(editable.model),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Belge yüklenemedi.") }
            }
        }
    }

    /** Toolbar "Dosya Değiştir" onayından sonra çağrılır — ekranı sıfırlar, yeni dosya seçimine döner. */
    fun reset() {
        _uiState.value = UdfEditorUiState()
    }

    fun save() {
        val state = _uiState.value
        val document = state.document ?: return
        val editable = state.editable ?: return
        if (!state.canSave) return

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) { UdfEditorService.save(document, editable.baseName, context) }
                historyRepository.addRecord(
                    ConversionRecord.create(
                        originalFileName = result.name,
                        outputFormat = "UDF",
                        success = true,
                        outputPath = result.absolutePath,
                    ),
                )
                _uiState.update {
                    it.copy(isSaving = false, savedFile = result, originalFingerprint = UdfEditorService.fingerprint(document))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "UDF kaydedilemedi: ${e.message}") }
            }
        }
    }

    private fun updateDocument(newDocument: UdfEditDocument) {
        _uiState.update { it.copy(document = newDocument, savedFile = null) }
    }

    // MARK: - Blok işlemleri

    fun updateBlock(index: Int, block: UdfEditBlock) {
        val document = _uiState.value.document ?: return
        if (index !in document.blocks.indices) return
        val blocks = document.blocks.toMutableList()
        blocks[index] = block
        updateDocument(document.copy(blocks = blocks))
    }

    fun addParagraphBlock() {
        val document = _uiState.value.document ?: return
        updateDocument(document.copy(blocks = document.blocks + UdfEditBlock.ParagraphBlock(UdfEditParagraph(runs = listOf(UdfEditRun(text = ""))))))
    }

    fun addTableBlock() {
        val document = _uiState.value.document ?: return
        val table = UdfEditTable(columnCount = 2, rows = listOf(UdfEditTableRow(cells = listOf(UdfEditTableCell(), UdfEditTableCell()))))
        updateDocument(document.copy(blocks = document.blocks + UdfEditBlock.TableBlock(table)))
    }

    fun removeBlock(index: Int) {
        val document = _uiState.value.document ?: return
        if (index !in document.blocks.indices) return
        val blocks = document.blocks.toMutableList()
        blocks.removeAt(index)
        updateDocument(document.copy(blocks = blocks))
    }

    // MARK: - Tablo işlemleri

    fun addTableRow(blockIndex: Int) {
        val document = _uiState.value.document ?: return
        val block = document.blocks.getOrNull(blockIndex) as? UdfEditBlock.TableBlock ?: return
        val columnCount = UdfTableMergeHelper.logicalColumnCount(block.table)
        val newRow = UdfEditTableRow(cells = List(columnCount) { UdfEditTableCell() })
        updateBlock(blockIndex, UdfEditBlock.TableBlock(block.table.copy(rows = block.table.rows + newRow)))
    }

    fun addTableColumn(blockIndex: Int) {
        val document = _uiState.value.document ?: return
        val block = document.blocks.getOrNull(blockIndex) as? UdfEditBlock.TableBlock ?: return
        val newRows = block.table.rows.map { row -> row.copy(cells = row.cells + UdfEditTableCell()) }
        updateBlock(blockIndex, UdfEditBlock.TableBlock(block.table.copy(columnCount = block.table.columnCount + 1, rows = newRows)))
    }

    fun removeTableRow(blockIndex: Int, rowIndex: Int) {
        val document = _uiState.value.document ?: return
        val block = document.blocks.getOrNull(blockIndex) as? UdfEditBlock.TableBlock ?: return
        if (rowIndex !in block.table.rows.indices) return
        val rows = block.table.rows.toMutableList()
        rows.removeAt(rowIndex)
        updateBlock(blockIndex, UdfEditBlock.TableBlock(block.table.copy(rows = rows)))
    }

    fun updateTableCell(blockIndex: Int, rowIndex: Int, cellIndex: Int, cell: UdfEditTableCell) {
        val document = _uiState.value.document ?: return
        val block = document.blocks.getOrNull(blockIndex) as? UdfEditBlock.TableBlock ?: return
        if (rowIndex !in block.table.rows.indices) return
        val rows = block.table.rows.toMutableList()
        val cells = rows[rowIndex].cells.toMutableList()
        if (cellIndex !in cells.indices) return
        cells[cellIndex] = cell
        rows[rowIndex] = rows[rowIndex].copy(cells = cells)
        updateBlock(blockIndex, UdfEditBlock.TableBlock(block.table.copy(rows = rows)))
    }

    /** [CellMergeSheet] gibi araçlardan gelen [UdfTableMergeHelper] dönüşümlerini uygular. */
    fun applyTableTransform(blockIndex: Int, transform: (UdfEditTable) -> UdfEditTable) {
        val document = _uiState.value.document ?: return
        val block = document.blocks.getOrNull(blockIndex) as? UdfEditBlock.TableBlock ?: return
        updateBlock(blockIndex, UdfEditBlock.TableBlock(transform(block.table)))
    }

    // MARK: - Header / Footer

    fun addHeader() {
        val document = _uiState.value.document ?: return
        updateDocument(document.copy(headers = document.headers + UdfEditHeaderFooter()))
    }

    fun addFooter() {
        val document = _uiState.value.document ?: return
        updateDocument(document.copy(footers = document.footers + UdfEditHeaderFooter()))
    }

    fun removeHeader(index: Int) {
        val document = _uiState.value.document ?: return
        if (index !in document.headers.indices) return
        val list = document.headers.toMutableList()
        list.removeAt(index)
        updateDocument(document.copy(headers = list))
    }

    fun removeFooter(index: Int) {
        val document = _uiState.value.document ?: return
        if (index !in document.footers.indices) return
        val list = document.footers.toMutableList()
        list.removeAt(index)
        updateDocument(document.copy(footers = list))
    }

    fun updateHeader(index: Int, item: UdfEditHeaderFooter) {
        val document = _uiState.value.document ?: return
        if (index !in document.headers.indices) return
        val list = document.headers.toMutableList()
        list[index] = item
        updateDocument(document.copy(headers = list))
    }

    fun updateFooter(index: Int, item: UdfEditHeaderFooter) {
        val document = _uiState.value.document ?: return
        if (index !in document.footers.indices) return
        val list = document.footers.toMutableList()
        list[index] = item
        updateDocument(document.copy(footers = list))
    }

    fun addHeaderParagraph(headerIndex: Int) {
        val header = _uiState.value.document?.headers?.getOrNull(headerIndex) ?: return
        updateHeader(headerIndex, header.copy(paragraphs = header.paragraphs + UdfEditParagraph(runs = listOf(UdfEditRun(text = "")))))
    }

    fun addFooterParagraph(footerIndex: Int) {
        val footer = _uiState.value.document?.footers?.getOrNull(footerIndex) ?: return
        updateFooter(footerIndex, footer.copy(paragraphs = footer.paragraphs + UdfEditParagraph(runs = listOf(UdfEditRun(text = "")))))
    }
}
