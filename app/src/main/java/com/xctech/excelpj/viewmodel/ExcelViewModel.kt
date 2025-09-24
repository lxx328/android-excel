package com.xctech.excelpj.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xctech.excelpj.Repository.ExcelRepository
import com.xctech.excelpj.data.*
import kotlinx.coroutines.launch

class ExcelViewModel(
) : ViewModel() {
    // 使用可空的Repository
    private var repository: ExcelRepository? = null

    // Excel数据
    private val _excelData = MutableLiveData<ExcelResponse>()
    val excelData: LiveData<ExcelResponse> = _excelData

    // 当前选中的单元格
    private val _selectedCell = MutableLiveData<CellSelection?>()
    val selectedCell: LiveData<CellSelection?> = _selectedCell

    // 编辑状态
    private val _isEditing = MutableLiveData<Boolean>(false)
    val isEditing: LiveData<Boolean> = _isEditing

    // 缩放比例
    private val _scaleFactor = MutableLiveData<Float>(1f)
    val scaleFactor: LiveData<Float> = _scaleFactor

    // 错误信息
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    // 单元格更新事件
    private val _cellUpdateEvent = MutableLiveData<CellUpdateEvent>()
    val cellUpdateEvent: LiveData<CellUpdateEvent> = _cellUpdateEvent

    // 存储原始单元格数据，用于比较是否真正发生了变化
    private var originalCell: ExcelCell? = null
    // 存储当前编辑的值
    private var currentEditingValue: String = ""

    data class CellSelection(
        val row: Int,
        val col: Int,
        val cell: ExcelCell,
        val mergeInfo: ExcelMerge? = null
    )

    data class CellUpdateEvent(
        val row: Int,
        val col: Int,
        val type: UpdateType
    )

    enum class UpdateType {
        CONTENT, BACKGROUND_COLOR, ALL
    }

    // 设置Repository的context
    fun setRepositoryContext(context: Context) {
        repository = ExcelRepository(context)
    }

    // 加载Excel数据
    fun loadExcelData(formId: Int) {
        viewModelScope.launch {
            try {
                val response = repository?.getExcelData(formId)
                _excelData.value = response!!
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    // 选择单元格
    fun selectCell(row: Int, col: Int) {
        _excelData.value?.let { data ->
            val cell = data.excelInfo.tableData.getOrNull(row)?.getOrNull(col)
            cell?.let {
                // 保存原始单元格数据
                originalCell = it.copy()
                // 初始化当前编辑值
                currentEditingValue = it.value
                val mergeInfo = findMergeInfo(row, col, data.excelInfo.mergedCells)
                _selectedCell.value = CellSelection(row, col, it, mergeInfo)
                _isEditing.value = true
            }
        }
    }

    // 更新当前编辑的值（不立即保存到数据模型）
    fun updateCurrentEditingValue(newValue: String) {
        currentEditingValue = newValue
    }

    // 应用编辑的值到数据模型
    fun applyEditedValue(row: Int, col: Int) {
        viewModelScope.launch {
            try {
                // 检查数据是否可用
                val currentData = _excelData.value
                if (currentData == null) {
                    _errorMessage.value = "Excel data is not available"
                    return@launch
                }

                // 获取原始值
                val originalValue = originalCell?.value ?: ""

                // 检查内容是否真正发生了变化
                if (currentEditingValue == originalValue) {
                    // 内容没有变化，不需要标记为已编辑
                    return@launch
                }

                // 内容确实发生了变化，更新本地数据
                val updatedTableData = currentData.excelInfo.tableData.mapIndexed { rowIndex, rowData ->
                    if (rowIndex == row) {
                        rowData.mapIndexed { colIndex, cell ->
                            if (colIndex == col) {
                                cell.copyWithEdit(currentEditingValue)
                            } else cell
                        }
                    } else rowData
                }

                val updatedExcelInfo = currentData.excelInfo.copy(tableData = updatedTableData)
                _excelData.value = currentData.copy(excelInfo = updatedExcelInfo)

                // 通知View更新
                _cellUpdateEvent.value = CellUpdateEvent(row, col, UpdateType.CONTENT)

                // 保存到服务器
                repository?.updateCell(currentData.id, row, col, currentEditingValue)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update cell: ${e.message}"
            }
        }
    }

    // 根据单元格的rowIndex和colIndex选择单元格
    fun selectCellByIndex(rowIndex: Int, colIndex: Int) {
        _excelData.value?.let { data ->
            // 遍历表格数据找到匹配的单元格
            for (row in data.excelInfo.tableData.indices) {
                for (col in data.excelInfo.tableData[row].indices) {
                    val cell = data.excelInfo.tableData[row][col]
                    if (cell.rowIndex == rowIndex && cell.colIndex == colIndex) {
                        // 保存原始单元格数据
                        originalCell = cell.copy()
                        val mergeInfo = findMergeInfo(row, col, data.excelInfo.mergedCells)
                        _selectedCell.value = CellSelection(row, col, cell, mergeInfo)
                        _isEditing.value = true
                        return
                    }
                }
            }
        }
    }


    // 更新单元格内容
    fun updateCellContent(row: Int, col: Int, newValue: String) {
        viewModelScope.launch {
            try {
                // 检查数据是否可用
                val currentData = _excelData.value
                if (currentData == null) {
                    _errorMessage.value = "Excel data is not available"
                    return@launch
                }

                // 检查内容是否真正发生了变化
                val originalValue = originalCell?.value ?: ""
                if (newValue == originalValue) {
                    // 内容没有变化，不需要标记为已编辑
                    return@launch
                }

                // 更新本地数据
                val updatedTableData = currentData.excelInfo.tableData.mapIndexed { rowIndex, rowData ->
                    if (rowIndex == row) {
                        rowData.mapIndexed { colIndex, cell ->
                            if (colIndex == col) {
                                cell.copyWithEdit(newValue)
                            } else cell
                        }
                    } else rowData
                }

                val updatedExcelInfo = currentData.excelInfo.copy(tableData = updatedTableData)
                _excelData.value = currentData.copy(excelInfo = updatedExcelInfo)

                // 通知View更新 - 确保这行会被执行
                _cellUpdateEvent.value = CellUpdateEvent(row, col, UpdateType.CONTENT)

                // 保存到服务器
//                repository?.updateCell(currentData.id, row, col, newValue)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update cell: ${e.message}"
            }
        }
    }

    // 更新单元格背景色
    fun updateCellBackgroundColor(row: Int, col: Int, color: Int) {
        viewModelScope.launch {
            try {
                _excelData.value?.let { data ->
                    // 检查背景色是否真正发生了变化
                    val originalColor = getCellBackgroundColor(row, col)
                    if (originalColor == color) {
                        // 背景色没有变化，不需要标记为已编辑
                        return@launch
                    }

                    // 更新背景色映射
                    cellBackgroundColors["$row,$col"] = color

                    // 通知View更新
                    _cellUpdateEvent.value = CellUpdateEvent(row, col, UpdateType.BACKGROUND_COLOR)

                    // 可以保存到服务器
                    // repository.updateCellStyle(data.id, row, col, backgroundColor = color)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update cell color: ${e.message}"
            }
        }
    }

    // 更新单元格背景色（使用颜色字符串）
    fun updateCellBackgroundColorString(row: Int, col: Int, colorString: String) {
        viewModelScope.launch {
            try {
                _excelData.value?.let { data ->
                    // 获取当前单元格
                    val currentCell = data.excelInfo.tableData.getOrNull(row)?.getOrNull(col)
                    if (currentCell != null) {
                        // 检查背景色是否真正发生了变化
                        val originalBgc = currentCell.bgc ?: ""
                        if (colorString == originalBgc) {
                            // 背景色没有变化，不需要标记为已编辑
                            return@launch
                        }

                        // 更新单元格数据
                        val updatedTableData = data.excelInfo.tableData.mapIndexed { rowIndex, rowData ->
                            if (rowIndex == row) {
                                rowData.mapIndexed { colIndex, cell ->
                                    if (colIndex == col) {
                                        // 更新bgc字段并标记为已编辑
                                        cell.copy(bgc = colorString, isEdited = true)
                                    } else cell
                                }
                            } else rowData
                        }

                        val updatedExcelInfo = data.excelInfo.copy(tableData = updatedTableData)
                        _excelData.value = data.copy(excelInfo = updatedExcelInfo)

                        // 通知View更新
                        _cellUpdateEvent.value = CellUpdateEvent(row, col, UpdateType.BACKGROUND_COLOR)

                        // 保存到服务器
                        // repository?.updateCell(data.id, row, col, newValue)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update cell color: ${e.message}"
            }
        }
    }

    // 临时存储单元格背景色
    private val cellBackgroundColors = mutableMapOf<String, Int>()

    fun getCellBackgroundColor(row: Int, col: Int): Int? {
        return cellBackgroundColors["$row,$col"]
    }

    // 查找合并单元格信息
    private fun findMergeInfo(row: Int, col: Int, mergedCells: List<ExcelMerge>): ExcelMerge? {
        return mergedCells.find { merge ->
            row in merge.minRow..merge.maxRow && col in merge.minCol..merge.maxCol
        }
    }

    // 缩放控制
    fun zoomIn() {
        _scaleFactor.value = (_scaleFactor.value ?: 1f).coerceAtMost(2.8f) + 0.2f
    }

    fun zoomOut() {
        _scaleFactor.value = (_scaleFactor.value ?: 1f).coerceAtLeast(0.7f) - 0.2f
    }

    fun closeEditor() {
        _isEditing.value = false
        _selectedCell.value = null
        originalCell = null
    }


    // 获取所有编辑过的单元格
    fun getEditedCells(): Map<String, ExcelCell> {
        val editedCells = mutableMapOf<String, ExcelCell>()
        _excelData.value?.let { data ->
            data.excelInfo.tableData.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { colIndex, cell ->
                    if (cell.isEdited) {
                        editedCells["$rowIndex,$colIndex"] = cell
                    }
                }
            }
        }
        return editedCells
    }
}