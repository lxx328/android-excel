package com.xctech.excellibrary.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.xctech.excellibrary.CellDiff
import com.xctech.excellibrary.Excel2DDiffUtil
import com.xctech.excellibrary.data.ExcelCell
import com.xctech.excellibrary.data.ExcelInfo

import kotlin.math.max
import kotlin.math.min

class ExcelTableView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 回调接口
    interface OnCellClickListener {
        fun onCellClick(row: Int, col: Int)
    }

    // 添加差异计算器
    private val diffUtil = Excel2DDiffUtil()

    // 保存旧数据用于比较
    private var oldTableData: List<List<ExcelCell>>? = null

    // 配置类
    data class Config(
        val showEditedCellBorder: Boolean = false,
        val editedCellBorderColor: Int = Color.parseColor("#10B981") // 绿色
    )

    // 构造器模式的Builder类
    class Builder {
        private var showEditedCellBorder = false
        private var editedCellBorderColor = Color.parseColor("#10B981") // 绿色

        fun showEditedCellBorder(show: Boolean) = apply { this.showEditedCellBorder = show }
        fun editedCellBorderColor(color: Int) = apply { this.editedCellBorderColor = color }

        fun build(): Config {
            return Config(showEditedCellBorder, editedCellBorderColor)
        }
    }

    private var cellClickListener: OnCellClickListener? = null
    private var excelInfo: ExcelInfo? = null
    private var scaleFactor = 1f

    // 自定义背景色映射
    private val customBackgroundColors = mutableMapOf<String, Int>()

    // 编辑过的单元格记录
    private val editedCells = mutableMapOf<String, ExcelCell>()

    // 配置
    private var config = Config()

    // 绘制相关 - 保持之前的代码
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * resources.displayMetrics.density
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
    }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val selectedPaint = Paint().apply {
        color = Color.parseColor("#007AFF")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val editedCellPaint = Paint().apply {
        color = 0 // 将在使用时设置
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val mergedCellPaint = Paint().apply {
        color = Color.parseColor("#F5F5F5")
        style = Paint.Style.FILL
    }

    // 尺寸
    private var cellWidth = 120f
    private var cellHeight = 60f
    private var offsetX = 0f
    private var offsetY = 0f

    // 选中状态
    private var selectedRow = -1
    private var selectedCol = -1

    // 手势检测
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    // 单元格类型颜色
//    private val cellTypeColors = mapOf(
//        1 to Color.WHITE,           // 文本
//        2 to Color.parseColor("#E3F2FD"), // 平均值
//        3 to Color.parseColor("#F3E5F5"), // 选项
//        4 to Color.parseColor("#E8F5E9"), // 范围
//        5 to Color.parseColor("#FFF3E0"), // 图片
//        6 to Color.parseColor("#E1F5FE"), // 签名
//        7 to Color.parseColor("#F5F5F5"), // 置灰
//        8 to Color.parseColor("#FFFDE7")  // 搜索项
//    )

    // 应用配置
    fun applyConfig(config: Config) {
        this.config = config
        invalidate()
    }

    fun setOnCellClickListener(listener: OnCellClickListener) {
        this.cellClickListener = listener
    }

    fun setExcelInfo(info: ExcelInfo) {
        clearEditedCells()

        this.excelInfo = info
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }




    fun setScaleFactor(scale: Float) {
        scaleFactor = scale.coerceIn(0.5f, 3f)
        constrainOffsets()
        invalidate()
    }

    fun setSelectedCell(row: Int, col: Int) {
        selectedRow = row
        selectedCol = col
        invalidate()
    }

    // 设置单元格背景色
    fun setCellBackgroundColor(row: Int, col: Int, color: Int) {
        customBackgroundColors["$row,$col"] = color
        invalidate()
    }

    // 标记单元格为已编辑
    fun markCellAsEdited(row: Int, col: Int, cell: ExcelCell) {
        editedCells["$row,$col"] = cell
        invalidate()
    }

    // 获取所有编辑过的单元格
    fun getEditedCells(): Map<String, ExcelCell> = editedCells.toMap()

    // 清除编辑记录
    fun clearEditedCells() {
        editedCells.clear()
        invalidate()
    }

    // 更新单元格内容
    fun updateCell(row: Int, col: Int) {
        // 触发重绘
        invalidate()
    }

    private fun drawCell(canvas: Canvas, row: Int, col: Int, cell: ExcelCell, info: ExcelInfo) {
        val cellRect = getCellRect(row, col, cell, info)

        // 绘制背景 - 优先使用自定义背景色，然后是单元格的bgc字段
        var backgroundColor: Int? = null

        // 首先检查临时背景色映射
        backgroundColor = customBackgroundColors["$row,$col"]

        // 如果没有临时背景色，检查单元格的bgc字段
        if (backgroundColor == null && !cell.bgc.isNullOrEmpty() && cell.bgc != "white") {
            try {
                backgroundColor = Color.parseColor(cell.bgc)
            } catch (e: IllegalArgumentException) {
                // 如果解析颜色失败，使用默认颜色
                backgroundColor = Color.WHITE
            }
        }

        // 如果还没有背景色，使用单元格类型颜色
        if (backgroundColor == null) {
            backgroundColor =  Color.WHITE
        }

        cellPaint.color = backgroundColor
        cellPaint.style = Paint.Style.FILL
        canvas.drawRect(cellRect, cellPaint)

        // 绘制边框
        canvas.drawRect(cellRect, gridPaint)

        // 如果启用了编辑状态边框且该单元格已被编辑，则绘制特殊边框
        if (config.showEditedCellBorder && editedCells.containsKey("$row,$col")) {
            editedCellPaint.color = config.editedCellBorderColor
            canvas.drawRect(cellRect, editedCellPaint)
        }

        // 绘制内容
        drawTextCell(canvas, cellRect, cell)
//        when (cell.cellType) {
//            1, 2, 3, 4, 8 -> drawTextCell(canvas, cellRect, cell)
//            5 -> drawImageCell(canvas, cellRect, cell)
//            6 -> drawSignatureCell(canvas, cellRect, cell)
//            7 -> drawDisabledCell(canvas, cellRect)
//        }
    }

    // 智能更新方法
    fun updateExcelInfo(newInfo: ExcelInfo, preserveState: Boolean = true) {
        //清空编辑状态
        clearEditedCells()

        val oldInfo = this.excelInfo

        if (oldInfo == null) {
            // 首次设置数据
            setExcelInfo(newInfo)
            return
        }

        // 计算差异
        val diffResult = diffUtil.calculateDiff(
            oldInfo.tableData,
            newInfo.tableData
        )

        // 更新数据
        this.excelInfo = newInfo

        // 保留用户状态
        if (preserveState) {
            // 保留滚动位置
            constrainOffsets()

            // 迁移编辑状态
            migrateStatesWithDiff(diffResult)

            // 验证选中状态
            validateSelection(newInfo)
        } else {
            resetAllStates()
        }


        // 根据差异结果决定刷新策略
        when {
            diffResult.changedCells.isEmpty() &&
                    diffResult.addedRows.isEmpty() &&
                    diffResult.removedRows.isEmpty() -> {
                // 没有变化，不需要刷新
                return
            }

            diffResult.changedBounds != null &&
                    shouldUsePartialInvalidate(diffResult.changedBounds) -> {
                // 局部刷新
                invalidateRegion(diffResult.changedBounds)
            }

            else -> {
                // 全量刷新
                invalidate()
            }
        }
    }

    // 使用差异结果迁移状态
    private fun migrateStatesWithDiff(diffResult: Excel2DDiffUtil.DiffResult) {
        val newEditedCells = mutableMapOf<String, ExcelCell>()
        val newCustomColors = mutableMapOf<String, Int>()

        // 处理变化的单元格
        diffResult.changedCells.forEach { diff ->
            val key = "${diff.row},${diff.col}"

            // 如果是内容变化但保留了编辑状态
            if (diff.newCell?.isEdited == true || editedCells.containsKey(key)) {
                diff.newCell?.let { newEditedCells[key] = it }
            }

            // 保留自定义背景色
            customBackgroundColors[key]?.let { color ->
                newCustomColors[key] = color
            }
        }

        // 处理未变化的单元格（保留其状态）
        editedCells.forEach { (key, cell) ->
            if (!newEditedCells.containsKey(key)) {
                val (row, col) = key.split(",").map { it.toInt() }
                if (!diffResult.removedRows.contains(row)) {
                    // 如果行没有被删除，保留编辑状态
                    excelInfo?.tableData?.getOrNull(row)?.getOrNull(col)?.let {
                        newEditedCells[key] = it
                    }
                }
            }
        }

        customBackgroundColors.forEach { (key, color) ->
            if (!newCustomColors.containsKey(key)) {
                val (row, col) = key.split(",").map { it.toInt() }
                if (!diffResult.removedRows.contains(row)) {
                    newCustomColors[key] = color
                }
            }
        }

        editedCells.clear()
        editedCells.putAll(newEditedCells)
        customBackgroundColors.clear()
        customBackgroundColors.putAll(newCustomColors)
    }

    // 判断是否应该使用局部刷新
    private fun shouldUsePartialInvalidate(bounds: Rect): Boolean {
        // 如果变化区域小于总面积的30%，使用局部刷新
        val totalCells = (excelInfo?.rowCount ?: 0) * (excelInfo?.maxCols ?: 0)
        val changedCells = (bounds.right - bounds.left + 1) * (bounds.bottom - bounds.top + 1)
        return changedCells.toFloat() / totalCells < 0.3f
    }

    // 局部刷新
    private fun invalidateRegion(cellBounds: Rect) {
        val left = (cellBounds.left * cellWidth * scaleFactor + offsetX * scaleFactor).toInt()
        val top = (cellBounds.top * cellHeight * scaleFactor + offsetY * scaleFactor).toInt()
        val right = ((cellBounds.right + 1) * cellWidth * scaleFactor + offsetX * scaleFactor).toInt()
        val bottom = ((cellBounds.bottom + 1) * cellHeight * scaleFactor + offsetY * scaleFactor).toInt()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            invalidate(left, top, right, bottom)
        } else {
            invalidate()
        }
    }

    // 批量更新单元格

    private fun areCellsEqual(old: ExcelCell, new: ExcelCell): Boolean {
        return old.value == new.value &&
                old.bgc == new.bgc &&
                old.cellType == new.cellType &&
                old.isEdited == new.isEdited
    }

    private fun validateSelection(info: ExcelInfo) {
        if (selectedRow >= info.rowCount || selectedCol >= info.maxCols) {
            selectedRow = -1
            selectedCol = -1
        }
    }

    private fun resetAllStates() {
        offsetX = 0f
        offsetY = 0f
        selectedRow = -1
        selectedCol = -1
        editedCells.clear()
        customBackgroundColors.clear()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        excelInfo?.let { info ->
            canvas.save()
            canvas.scale(scaleFactor, scaleFactor)
            canvas.translate(offsetX, offsetY)

            // 绘制合并单元格背景
            drawMergedCells(canvas, info)

            // 绘制所有单元格
            drawCells(canvas, info)

            // 绘制选中框
            drawSelection(canvas)

            canvas.restore()
        }
    }

    //全局更新
    fun updateExcelInfoAll(newInfo: ExcelInfo) {
        //清空编辑状态
        editedCells.clear()

        excelInfo = newInfo
        //复原位置
        offsetX = 0f
        offsetY = 0f
        //复原放缩
        scaleFactor = 1f
        //复原选中
        selectedRow = -1
        selectedCol = -1
        editedCells.clear()
        invalidate()
    }

    private fun drawMergedCells(canvas: Canvas, info: ExcelInfo) {
        info.mergedCells.forEach { merge ->
            val left = merge.minCol * cellWidth
            val top = merge.minRow * cellHeight
            val right = (merge.maxCol + 1) * cellWidth
            val bottom = (merge.maxRow + 1) * cellHeight

            canvas.drawRect(left, top, right, bottom, mergedCellPaint)
            canvas.drawRect(left, top, right, bottom, gridPaint)
        }
    }

    private fun drawCells(canvas: Canvas, info: ExcelInfo) {
        // 计算可见范围，优化绘制性能
        val startRow = max(0, (-offsetY / cellHeight).toInt() - 1)
        val endRow = min(info.rowCount, ((height / scaleFactor - offsetY) / cellHeight).toInt() + 2)
        val startCol = max(0, (-offsetX / cellWidth).toInt() - 1)
        val endCol = min(info.maxCols, ((width / scaleFactor - offsetX) / cellWidth).toInt() + 2)

        for (rowIndex in startRow until endRow) {
            val row = info.tableData.getOrNull(rowIndex) ?: continue
            for (colIndex in startCol until endCol) {
                val cell = row.getOrNull(colIndex) ?: continue
                if (!shouldSkipCell(rowIndex, colIndex, cell, info)) {
                    drawCell(canvas, rowIndex, colIndex, cell, info)
                }
            }
        }
    }

    private fun shouldSkipCell(row: Int, col: Int, cell: ExcelCell, info: ExcelInfo): Boolean {
        // 如果是合并单元格且不是主单元格，则跳过绘制
        if (cell.merged && !cell.isMainCell) return true

        // 检查是否有其他合并单元格覆盖了这个位置
        for (merge in info.mergedCells) {
            if (row > merge.minRow && row <= merge.maxRow &&
                col > merge.minCol && col <= merge.maxCol) {
                return true
            }
        }

        return false
    }

    private fun getCellRect(row: Int, col: Int, cell: ExcelCell, info: ExcelInfo): RectF {
        return if (cell.merged && cell.isMainCell) {
            // 主单元格使用合并信息确定边界
            val merge = info.mergedCells.find { it.id == cell.mergeId }
            if (merge != null) {
                RectF(
                    merge.minCol * cellWidth,
                    merge.minRow * cellHeight,
                    (merge.maxCol + 1) * cellWidth,
                    (merge.maxRow + 1) * cellHeight
                )
            } else {
                // 如果没有找到合并信息，使用默认的行列跨度
                RectF(
                    col * cellWidth,
                    row * cellHeight,
                    (col + cell.colspan) * cellWidth,
                    (row + cell.rowspan) * cellHeight
                )
            }
        } else if (cell.merged && !cell.isMainCell) {
            // 对于非主单元格，查找其所属的主单元格边界
            val merge = info.mergedCells.find { merge ->
                row in merge.minRow..merge.maxRow && col in merge.minCol..merge.maxCol
            }
            if (merge != null) {
                RectF(
                    merge.minCol * cellWidth,
                    merge.minRow * cellHeight,
                    (merge.maxCol + 1) * cellWidth,
                    (merge.maxRow + 1) * cellHeight
                )
            } else {
                // 如果没有找到合并信息，使用单个单元格大小
                RectF(
                    col * cellWidth,
                    row * cellHeight,
                    (col + 1) * cellWidth,
                    (row + 1) * cellHeight
                )
            }
        } else {
            // 普通单元格
            RectF(
                col * cellWidth,
                row * cellHeight,
                (col + 1) * cellWidth,
                (row + 1) * cellHeight
            )
        }
    }


    private fun drawTextCell(canvas: Canvas, rect: RectF, cell: ExcelCell) {
        if (cell.value.isNotEmpty()) {
            val text = when (cell.cellType) {
//                2 -> "AVG: ${cell.value}"
//                3 -> cell.option.firstOrNull()?.toString() ?: cell.value
                else -> cell.value
            }

            // 保存画布状态
            canvas.save()

            // 限制文本绘制在单元格范围内
            canvas.clipRect(rect)

            // 计算文本位置
            val textX = rect.centerX()
            val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2

            // 绘制文本
            canvas.drawText(text, textX, textY, textPaint)

            // 恢复画布状态
            canvas.restore()
        }
    }
    private fun drawImageCell(canvas: Canvas, rect: RectF, cell: ExcelCell) {
        val iconSize = min(rect.width(), rect.height()) * 0.5f
        val iconRect = RectF(
            rect.centerX() - iconSize / 2,
            rect.centerY() - iconSize / 2,
            rect.centerX() + iconSize / 2,
            rect.centerY() + iconSize / 2
        )

        cellPaint.color = Color.GRAY
        cellPaint.style = Paint.Style.STROKE
        cellPaint.strokeWidth = 2f
        canvas.drawRect(iconRect, cellPaint)

        textPaint.textSize = iconSize * 0.6f
        canvas.drawText("📷", rect.centerX(), rect.centerY() + textPaint.textSize / 3, textPaint)
        textPaint.textSize = 14f * resources.displayMetrics.density
    }

    private fun drawSignatureCell(canvas: Canvas, rect: RectF, cell: ExcelCell) {
        val lineY = rect.bottom - cellHeight * 0.3f
        val lineStartX = rect.left + cellWidth * 0.1f
        val lineEndX = rect.right - cellWidth * 0.1f

        cellPaint.color = Color.BLACK
        cellPaint.strokeWidth = 1f
        canvas.drawLine(lineStartX, lineY, lineEndX, lineY, cellPaint)

        if (cell.value.isNotEmpty()) {
            canvas.drawText(cell.value, rect.centerX(), lineY - 10f, textPaint)
        }
    }

    private fun drawDisabledCell(canvas: Canvas, rect: RectF) {
        cellPaint.color = Color.parseColor("#80000000")
        cellPaint.style = Paint.Style.FILL
        canvas.drawRect(rect, cellPaint)
    }

    private fun drawSelection(canvas: Canvas) {
        if (selectedRow >= 0 && selectedCol >= 0) {
            excelInfo?.let { info ->
                val cell = info.tableData.getOrNull(selectedRow)?.getOrNull(selectedCol)
                cell?.let {
                    val rect = getCellRect(selectedRow, selectedCol, it, info)
                    canvas.drawRect(rect, selectedPaint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.5f, 3f)
            constrainOffsets()
            invalidate()
            return true
        }
    }
    inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            offsetX -= distanceX / scaleFactor
            offsetY -= distanceY / scaleFactor
            constrainOffsets()
            invalidate()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val col = ((e.x / scaleFactor - offsetX) / cellWidth).toInt()
            val row = ((e.y / scaleFactor - offsetY) / cellHeight).toInt()

            excelInfo?.let { info ->
                if (row in 0 until info.rowCount && col in 0 until info.maxCols) {
                    // 检查是否点击的是合并单元格的子单元格
                    val cell = info.tableData.getOrNull(row)?.getOrNull(col)
                    if (cell != null) {
                        // 如果是合并单元格的子单元格，则选择主单元格
                        if (cell.merged && !cell.isMainCell) {
                            // 查找主单元格的位置
                            val mainCellPosition = findMainCellPosition(row, col, info)
                            if (mainCellPosition != null) {
                                cellClickListener?.onCellClick(mainCellPosition.first, mainCellPosition.second)
                            }
                        } else {
                            // 正常单元格或主单元格直接触发点击
                            cellClickListener?.onCellClick(row, col)
                        }
                    }
                }
            }
            return true
        }
    }

    // 查找合并单元格的主单元格位置
    private fun findMainCellPosition(row: Int, col: Int, info: ExcelInfo): Pair<Int, Int>? {
        // 查找包含当前行列的合并单元格
        val merge = info.mergedCells.find { merge ->
            row in merge.minRow..merge.maxRow && col in merge.minCol..merge.maxCol
        }

        // 如果找到了合并单元格，返回主单元格位置
        merge?.let {
            return Pair(it.mainRow, it.mainCol)
        }

        return null
    }

    private fun constrainOffsets() {
        excelInfo?.let { info ->
            val viewWidth = width / scaleFactor
            val viewHeight = height / scaleFactor
            val contentWidth = info.maxCols * cellWidth
            val contentHeight = info.rowCount * cellHeight

            offsetX = when {
                contentWidth <= viewWidth -> 0f
                else -> {
                    val minX = -(contentWidth - viewWidth)
                    val maxX = 0f
                    offsetX.coerceIn(minX, maxX)
                }
            }

            offsetY = when {
                contentHeight <= viewHeight -> 0f
                else -> {
                    val minY = -(contentHeight - viewHeight)
                    val maxY = 0f
                    offsetY.coerceIn(minY, maxY)
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        constrainOffsets()
    }
}