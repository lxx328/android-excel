package com.xctech.excellibrary.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xctech.excellibrary.data.*
import com.xctech.excellibrary.repository.ExcelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExcelViewModel : ViewModel() {
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

    // 添加当前选中的sheet索引
    private var currentSheetIndex = 0

    // 添加视图状态恢复事件LiveData
    private val _viewStateRestoreEvent = MutableLiveData<ViewStateEvent>()
    val viewStateRestoreEvent: LiveData<ViewStateEvent> = _viewStateRestoreEvent

    // 添加重置视图状态事件LiveData
    private val _resetViewStateEvent = MutableLiveData<Boolean>()
    val resetViewStateEvent: LiveData<Boolean> = _resetViewStateEvent

    // 添加加载状态LiveData
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var _backgroundData: ExcelResponse? = null

    // 添加当前sheet更新事件
    private val _currentSheetUpdateEvent = MutableLiveData<Pair<Int, String>>()
    val currentSheetUpdateEvent: LiveData<Pair<Int, String>> = _currentSheetUpdateEvent

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
    fun loadExcelDataTset(formId: Int) {
        viewModelScope.launch {
            try {
                val response = repository?.getExcelData(formId)
                _excelData.value = response!!
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun loadExcelData(formData: ExcelResponse) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                _excelData.value = formData
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = e.message
            }finally {
                _isLoading.value = false
            }
        }
    }

    fun loadExcelData(formData: ExcelResponse, state: SheetRefreshState) {
        viewModelScope.launch {
            try {
                // 显示加载状态
                _isLoading.value = true

                // 获取新数据

                formData.let { resp ->
                    // 确保有sheet数据
                    if (resp.excelInfo.isEmpty()) {
                        _errorMessage.value = "没有可用的sheet数据"
                        _resetViewStateEvent.postValue(true)
                        return@let
                    }

                    // 尝试在新数据中找到匹配的sheet
                    var targetSheetIndex = -1
                    var targetSheet: ExcelInfo? = null

                    // 首先尝试通过sheetIndex匹配
                    val sheetByIndex = resp.excelInfo.find { it.sheetIndex == state.sheetIndex }
                    if (sheetByIndex != null) {
                        targetSheetIndex = resp.excelInfo.indexOf(sheetByIndex)
                        targetSheet = sheetByIndex
                        Log.d("ExcelViewModel", "Reload: found sheet by index: ${state.sheetIndex}")
                    }
                    // 然后尝试按名称匹配
                    else {
                        val sheetByName = resp.excelInfo.find { it.sheetName == state.sheetName }
                        if (sheetByName != null) {
                            targetSheetIndex = resp.excelInfo.indexOf(sheetByName)
                            targetSheet = sheetByName
                            Log.d("ExcelViewModel", "Reload: found sheet by name: ${state.sheetName}")
                        }
                    }

                    // 如果仍然找不到匹配的sheet，使用第一个sheet
                    if (targetSheet == null) {
                        targetSheetIndex = 0
                        targetSheet = resp.excelInfo[0]
                        Log.d("ExcelViewModel", "Reload: falling back to first sheet")

                        // 由于没找到匹配的sheet，触发重置事件
                        _resetViewStateEvent.postValue(true)
                    } else {
                        // 找到了匹配的sheet，触发视图状态恢复事件
                        _viewStateRestoreEvent.postValue(ViewStateEvent(
                            scale = state.scale,
                            offsetX = state.offsetX,
                            offsetY = state.offsetY,
                            sheetName = targetSheet.sheetName,
                            sheetIndex = targetSheet.sheetIndex
                        ))
                    }
                    // 更新当前sheet信息
                    currentSheetIndex = targetSheet.sheetIndex

                    // 更新数据模型
                    _excelData.value = resp

                    // 触发当前sheet更新事件
                    _currentSheetUpdateEvent.value = Pair(targetSheetIndex, targetSheet.sheetName)
                } ?: run {
                    // 响应为空
                    _errorMessage.value = "加载数据失败: 响应为空"
                    _resetViewStateEvent.postValue(true)
                }
            } catch (e: Exception) {
                // 异常处理
                Log.e("ExcelViewModel", "Failed to reload data: ${e.message}", e)
                _errorMessage.value = "加载数据失败: ${e.message}"
                _resetViewStateEvent.postValue(true)
            } finally {
                // 隐藏加载状态
                _isLoading.value = false
            }
        }
    }



    // 选择单元格
    fun selectCell(row: Int, col: Int) {
        _excelData.value?.let { data ->
            // 首先找到当前应该使用的sheet数据
            var currentSheetInfo = data.excelInfo[currentSheetIndex]
            // 如果有多个sheet，查找与currentSheetName匹配的sheet
            if (data.excelInfo.isNotEmpty()) {
                val matchingSheet = data.excelInfo.find { it.sheetIndex == currentSheetIndex }
                if (matchingSheet != null) {
                    currentSheetInfo = matchingSheet
                }
            }

            // 从当前sheet中获取单元格数据
            val cell = currentSheetInfo.tableData.getOrNull(row)?.getOrNull(col)
            cell?.let {
                // 保存原始单元格数据
                originalCell = it.copy()
                // 初始化当前编辑值
                currentEditingValue = it.value
                // 更新当前sheet名称
                currentSheetIndex = currentSheetInfo.sheetIndex
//                currentSheetName = currentSheetInfo.sheetName
                val mergeInfo = findMergeInfo(row, col, currentSheetInfo.mergedCells)
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

                // 确保有sheet数据
                if (currentData.excelInfo.isEmpty()) {
                    _errorMessage.value = "No sheet data available"
                    return@launch
                }

                // 获取当前sheet
                val currentSheetIndex = getCurrentSheetIndex()
                if (currentSheetIndex < 0 || currentSheetIndex >= currentData.excelInfo.size) {
                    _errorMessage.value = "Invalid current sheet index"
                    return@launch
                }

                // 获取原始值
                val currentSheet = currentData.excelInfo[currentSheetIndex]
                val originalValue = originalCell?.value ?: ""
                val currentValue = currentEditingValue

                // 检查内容是否真正发生了变化
                if (currentValue == originalValue) {
                    return@launch
                }

                // 更新数据
                val updatedSheetList = currentData.excelInfo.mapIndexed { index, sheet ->
                    if (index == currentSheetIndex) {
                        // 更新当前sheet的数据
                        val updatedTableData = sheet.tableData.mapIndexed { rowIndex, rowData ->
                            if (rowIndex == row) {
                                rowData.mapIndexed { colIndex, cell ->
                                    if (colIndex == col) {
                                        cell.copyWithEdit(currentValue)
                                    } else cell
                                }
                            } else rowData
                        }
                        sheet.copy(tableData = updatedTableData)
                    } else sheet
                }

                // 更新数据模型
                _excelData.value = currentData.copy(excelInfo = updatedSheetList)

                // 通知View更新
                _cellUpdateEvent.value = CellUpdateEvent(row, col, UpdateType.CONTENT)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update cell: ${e.message}"
                e.printStackTrace()
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

                // 更新数据但不触发完整的UI刷新
                // 发送单元格更新事件而不是更新整个excelData
                _cellUpdateEvent.value = CellUpdateEvent(row, col, UpdateType.CONTENT)

                // 在后台更新数据模型
                updateDataModelInBackground(currentData, row, col, newValue)
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
                    // 确保有sheet数据
                    if (data.excelInfo.isEmpty()) {
                        _errorMessage.value = "No sheet data available"
                        return@launch
                    }

                    // 获取当前sheet索引
                    val currentSheetIndex = getCurrentSheetIndex()
                    if (currentSheetIndex < 0 || currentSheetIndex >= data.excelInfo.size) {
                        _errorMessage.value = "Invalid current sheet index"
                        return@launch
                    }

                    // 获取当前单元格
                    val currentSheet = data.excelInfo[currentSheetIndex]
                    val currentCell = currentSheet.tableData.getOrNull(row)?.getOrNull(col)
                    if (currentCell == null) {
                        _errorMessage.value = "Cell not found"
                        return@launch
                    }

                    // 检查背景色是否真正发生了变化
                    val originalBgc = currentCell.bgc ?: ""
                    if (colorString == originalBgc) {
                        return@launch
                    }

                    // 更新数据
                    val updatedSheetList = data.excelInfo.mapIndexed { index, sheet ->
                        if (index == currentSheetIndex) {
                            // 更新当前sheet的数据
                            val updatedTableData = sheet.tableData.mapIndexed { rowIndex, rowData ->
                                if (rowIndex == row) {
                                    rowData.mapIndexed { colIndex, cell ->
                                        if (colIndex == col) {
                                            // 更新bgc字段并标记为已编辑
                                            cell.copy(bgc = colorString, isEdited = true)
                                        } else cell
                                    }
                                } else rowData
                            }
                            sheet.copy(tableData = updatedTableData)
                        } else sheet
                    }

                    // 更新数据模型
                    _excelData.value = data.copy(excelInfo = updatedSheetList)

                    // 通知View更新
                    _cellUpdateEvent.value = CellUpdateEvent(row, col, UpdateType.BACKGROUND_COLOR)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update cell color: ${e.message}"
                e.printStackTrace()
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
    fun getEditedCells(isRestoreState: Boolean ): Map<Int, Map<String, ExcelCell>> {
        // 创建嵌套Map结构，外层key为sheetIndex，内层为cell位置映射
        val result = mutableMapOf<Int, MutableMap<String, ExcelCell>>()

        _excelData.value?.let { data ->
            // 遍历所有sheet
            data.excelInfo.forEach { sheet ->
                val sheetIndex = sheet.sheetIndex

                // 遍历sheet中的所有单元格
                sheet.tableData.forEachIndexed { rowIndex, row ->
                    row.forEachIndexed { colIndex, cell ->
                        if (cell.isEdited) {
                            // 为该sheet创建或获取单元格Map
                            val cellsInSheet = result.getOrPut(sheetIndex) { mutableMapOf() }

                            // 使用"row,col"格式作为key
                            val cellKey = "$rowIndex,$colIndex"
                            cellsInSheet[cellKey] = cell

                            if (isRestoreState) {
                                // 恢复编辑状态
                                cell.isEdited = false
                            }
                        }
                    }
                }
            }
        }

        return result
    }

    // 获取所有编辑过的单元格
    fun getEditedCells(): Map<Int, Map<String, ExcelCell>> {
        // 创建嵌套Map结构，外层key为sheetIndex，内层为cell位置映射
        val result = mutableMapOf<Int, MutableMap<String, ExcelCell>>()

        _excelData.value?.let { data ->
            // 遍历所有sheet
            data.excelInfo.forEach { sheet ->
                val sheetIndex = sheet.sheetIndex

                // 遍历sheet中的所有单元格
                sheet.tableData.forEachIndexed { rowIndex, row ->
                    row.forEachIndexed { colIndex, cell ->
                        if (cell.isEdited) {
                            // 为该sheet创建或获取单元格Map
                            val cellsInSheet = result.getOrPut(sheetIndex) { mutableMapOf() }

                            // 使用"row,col"格式作为key
                            val cellKey = "$rowIndex,$colIndex"
                            cellsInSheet[cellKey] = cell
                        }
                    }
                }
            }
        }

        return result
    }

    // 更新当前sheet名称和索引
    fun updateCurrentSheet(sheetName: String, sheetIndex: Int) {
//        currentSheetName = sheetName
        currentSheetIndex = sheetIndex

        _excelData.value?.let { currentData ->
            if (currentData.excelInfo.isNotEmpty() && sheetIndex < currentData.excelInfo.size) {
                // 触发当前sheet更新事件
                _currentSheetUpdateEvent.value = Pair(sheetIndex, sheetName)
            }
        }
    }

    // 获取当前活动的sheet
    fun getCurrentSheet(): ExcelInfo? {
        return _excelData.value?.excelInfo?.getOrNull(currentSheetIndex)
    }

    // 后台更新数据模型，避免触发UI刷新
    private fun updateDataModelInBackground(currentData: ExcelResponse, row: Int, col: Int, newValue: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // 获取当前sheet索引
                val currentSheetIndex = getCurrentSheetIndex()
                if (currentSheetIndex < 0 || currentSheetIndex >= currentData.excelInfo.size) {
                    return@launch
                }

                // 更新数据但不触发UI刷新
                val updatedSheetList = currentData.excelInfo.mapIndexed { index, sheet ->
                    if (index == currentSheetIndex) {
                        // 更新当前sheet的数据
                        val updatedTableData = sheet.tableData.mapIndexed { rowIndex, rowData ->
                            if (rowIndex == row) {
                                rowData.mapIndexed { colIndex, cell ->
                                    if (colIndex == col) {
                                        cell.copyWithEdit(newValue)
                                    } else cell
                                }
                            } else rowData
                        }
                        sheet.copy(tableData = updatedTableData)
                    } else sheet
                }

                // 更新后台数据，不触发LiveData更新
                _backgroundData = currentData.copy(excelInfo = updatedSheetList)
            } catch (e: Exception) {
                Log.e("ExcelViewModel", "Failed to update data model in background: ${e.message}", e)
            }
        }
    }

    // 获取当前sheet索引的辅助方法
    private fun getCurrentSheetIndex(): Int {
        return currentSheetIndex
    }

    // 获取特定sheet的ExcelInfo对象
    fun getSheetByIndex(sheetIndex: Int): ExcelInfo? {
        return _excelData.value?.excelInfo?.find { it.sheetIndex == sheetIndex }
    }

    // 获取特定sheet的ExcelInfo对象（按名称）
    fun getSheetByName(sheetName: String): ExcelInfo? {
        return _excelData.value?.excelInfo?.find { it.sheetName == sheetName }
    }

    // 设置当前活动sheet索引
    fun setCurrentSheetIndex(index: Int) {
        _excelData.value?.let { data ->
            if (index >= 0 && index < data.excelInfo.size) {
                currentSheetIndex = index
            }
        }
    }

    // 视图状态恢复事件
    data class ViewStateEvent(
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
        val sheetName: String,
        val sheetIndex: Int
    )

    // 用于传递状态的数据类
    data class SheetRefreshState(
        val formId: Int,
        val sheetIndex: Int,
        val sheetName: String,
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float
    )
}