// ExcelDiffUtil.kt
package com.xctech.excellibrary
import android.graphics.Rect
import androidx.recyclerview.widget.DiffUtil
import com.xctech.excellibrary.data.ExcelCell
import kotlin.math.max
import kotlin.math.min

data class CellDiff(
    val row: Int,
    val col: Int,
    val oldCell: ExcelCell?,
    val newCell: ExcelCell?
)

class ExcelDiffCallback(
    private val oldData: List<List<ExcelCell>>,
    private val newData: List<List<ExcelCell>>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldData.size

    override fun getNewListSize(): Int = newData.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // 比较行是否相同
        return oldItemPosition == newItemPosition
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldRow = oldData.getOrNull(oldItemPosition) ?: return false
        val newRow = newData.getOrNull(newItemPosition) ?: return false

        if (oldRow.size != newRow.size) return false

        return oldRow.indices.all { col ->
            areExcelCellsEqual(oldRow[col], newRow[col])
        }
    }

    private fun areExcelCellsEqual(old: ExcelCell, new: ExcelCell): Boolean {
        return old.value == new.value &&
                old.bgc == new.bgc &&
                old.cellType == new.cellType &&
                old.merged == new.merged &&
                old.isMainCell == new.isMainCell &&
                old.option == new.option &&
                old.isEdited == new.isEdited
    }
}

// 自定义的差异计算器，专门处理二维数据
class Excel2DDiffUtil {

    data class DiffResult(
        val changedCells: List<CellDiff>,
        val addedRows: List<Int>,
        val removedRows: List<Int>,
        val changedBounds: Rect? // 变化区域的边界
    )

    fun calculateDiff(
        oldData: List<List<ExcelCell>>,
        newData: List<List<ExcelCell>>
    ): DiffResult {
        val changedCells = mutableListOf<CellDiff>()
        val addedRows = mutableListOf<Int>()
        val removedRows = mutableListOf<Int>()

        var minChangedRow = Int.MAX_VALUE
        var maxChangedRow = Int.MIN_VALUE
        var minChangedCol = Int.MAX_VALUE
        var maxChangedCol = Int.MIN_VALUE

        // 计算行级别的差异
        val maxRows = max(oldData.size, newData.size)

        for (row in 0 until maxRows) {
            when {
                row >= oldData.size -> {
                    // 新增的行
                    addedRows.add(row)
                    minChangedRow = min(minChangedRow, row)
                    maxChangedRow = max(maxChangedRow, row)
                }
                row >= newData.size -> {
                    // 删除的行
                    removedRows.add(row)
                    minChangedRow = min(minChangedRow, row)
                    maxChangedRow = max(maxChangedRow, row)
                }
                else -> {
                    // 比较现有行的单元格
                    val oldRow = oldData[row]
                    val newRow = newData[row]
                    val maxCols = max(oldRow.size, newRow.size)

                    for (col in 0 until maxCols) {
                        val oldCell = oldRow.getOrNull(col)
                        val newCell = newRow.getOrNull(col)

                        if (!areCellsEqual(oldCell, newCell)) {
                            changedCells.add(CellDiff(row, col, oldCell, newCell))
                            minChangedRow = min(minChangedRow, row)
                            maxChangedRow = max(maxChangedRow, row)
                            minChangedCol = min(minChangedCol, col)
                            maxChangedCol = max(maxChangedCol, col)
                        }
                    }
                }
            }
        }

        // 计算变化区域
        val changedBounds = if (changedCells.isNotEmpty() || addedRows.isNotEmpty() || removedRows.isNotEmpty()) {
            Rect(minChangedCol, minChangedRow, maxChangedCol, maxChangedRow)
        } else null

        return DiffResult(changedCells, addedRows, removedRows, changedBounds)
    }

    private fun areCellsEqual(old: ExcelCell?, new: ExcelCell?): Boolean {
        if (old == null && new == null) return true
        if (old == null || new == null) return false

        return old.value == new.value &&
                old.bgc == new.bgc &&
                old.cellType == new.cellType &&
                old.merged == new.merged &&
                old.isMainCell == new.isMainCell &&
                old.option == new.option &&
                old.isEdited == new.isEdited &&
                old.colspan == new.colspan &&
                old.rowspan == new.rowspan
    }
}