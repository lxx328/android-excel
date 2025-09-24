package com.xctech.excellibrary.data
//一个excel响应类
/**
 * 注释
 * @param id 表单id
 * @param formNo 表单编号
 * @param version 表单版本
 * @param bgcList 表单背景颜色
 * @param validTime 表单有效期
 * @param formName 表单名称
 * @param deptClassLine 部门分类线
 * @param qaConfirm 是否需要QA确认
 * @param confirmDept 确认部门
 * @param remarks 备注
 * @param excelInfo excel信息
 */
data class ExcelResponse(val id:Int, val bgcList: List<String> ?= emptyList(),
                         val formNo:String, val version:String, val validTime:String,
                         val formName:String, val deptClassLine:String, val qaConfirm:Int,
                         val confirmDept:String?, val remarks:String, val excelInfo:ExcelInfo)

/**
 * 注释
 * @param mergedCells 合并单元格
 * @param fileName 文件名
 * @param mergedCellsCount 合并单元格数量
 * @param tableData 表格数据
 * @param rowCount 行数
 * @param maxCols 最大列数
 */
data class ExcelInfo(val mergedCells:List<ExcelMerge>, val fileName:String ,val mergedCellsCount:Int,val tableData :List<List<ExcelCell>> ,val rowCount:Int, val maxCols:Int)

/**
 * 注释
 * @param mainCol 主列
 * @param maxCol 最大列
 * @param minRow 最小行
 * @param minCol 最小列
 * @param maxRow 最大行
 * @param mainRow 主行
 * @param id 合并单元格id
 */
data class ExcelMerge(val mainCol:Int, val maxCol:Int, val minRow:Int, val minCol:Int,
                      val maxRow:Int, val mainRow:Int, val id:String)

/**
 * 注释
 * @param colspan 列跨度
 * @param cellType 单元格类型 单元格类型：1-文本，2-平均值，3-选项，4-范围，5-图片，6-签名，7-置灰，8-搜索项
 * @param mergeId 合并单元格id
 * @param rowspan 行跨度
 * @param merged 是否合并单元格
 * @param isMainCell 是否主单元格
 * @param originalRow 是否原始行
 * @param value 单元格值
 * @param option 单元格选项
 */
data class ExcelCell(val colspan:Int, val cellType:Int, val mergeId: String?, val rowspan:Int,
                     val bgc:String? = null, val colIndex:Int? = null, val rowIndex:Int ? = null,
                     val merged:Boolean, val isMainCell:Boolean, val originalRow:Boolean,
                     val value:String, val option:List<Any>, val isEdited: Boolean = false){
    // 创建一个带有新值和编辑状态的副本
    fun copyWithEdit(newValue: String): ExcelCell {
        return this.copy(value = newValue, isEdited = true)
    }

    // 比较两个单元格是否相等（不考虑编辑状态）
    fun isContentEqual(other: ExcelCell): Boolean {
        return this.colspan == other.colspan &&
                this.cellType == other.cellType &&
                this.mergeId == other.mergeId &&
                this.rowspan == other.rowspan &&
                this.merged == other.merged &&
                this.isMainCell == other.isMainCell &&
                this.originalRow == other.originalRow &&
                this.value == other.value &&
                this.option == other.option&&
                this.bgc == other.bgc
    }
}