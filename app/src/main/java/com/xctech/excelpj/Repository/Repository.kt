package com.xctech.excelpj.Repository

import android.content.Context
import com.xctech.excelpj.data.ExcelCell
import com.xctech.excelpj.data.ExcelInfo
import com.xctech.excelpj.data.ExcelMerge
import com.xctech.excelpj.data.ExcelResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import java.io.InputStream
import java.util.Scanner

class ExcelRepository(private val context: Context? = null) {

    // 模拟从服务器获取数据
    suspend fun getExcelData(formId: Int): ExcelResponse = withContext(Dispatchers.IO) {
        // 根据formId选择不同的数据源
        when (formId) {
            1 -> mockExcelResponse()
            2 -> loadFromAssets("excel_a.json")
            3 -> loadFromAssets("excel_data.json")
            4 -> loadFromAssets("excel_v2.json")
            5 -> loadFromAssets("excel_v3.json")
            6 -> loadFromAssets("excel_v4.json")
            else -> mockExcelResponse()
        }
    }

    suspend fun updateCell(formId: Int, row: Int, col: Int, value: String) = withContext(Dispatchers.IO) {
        // 实际的更新请求
    }

    // 从assets加载JSON数据
    private fun loadFromAssets(fileName: String): ExcelResponse {
        return try {
            val inputStream: InputStream = context?.assets?.open(fileName)!!
            val size: Int = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val json = String(buffer, Charsets.UTF_8)

            // 解析JSON
            val gson = Gson()
            val wrapper = gson.fromJson(json, ExcelDataWrapper::class.java)
            wrapper.data
        } catch (e: Exception) {
            e.printStackTrace()
            mockExcelResponse()
        }
    }

    private fun mockExcelResponse(): ExcelResponse {
        // 返回模拟数据用于测试
        return ExcelResponse(
            id = 1,
            formNo = "FORM001",
            version = "1.0",
            validTime = "2025-12-31",
            formName = "Sample Excel Form",
            deptClassLine = "Engineering",
            qaConfirm = 1,
            confirmDept = "QA Department",
            remarks = "Test form",
            excelInfo = List(1) {
                ExcelInfo(
                    mergedCells = listOf(
                        ExcelMerge(0, 2, 0, 0, 0, 0, "merge1")
                    ),
                    fileName = "sample.xlsx",
                    mergedCellsCount = 1,
                    tableData = generateMockTableData(),
                    rowCount = 10,
                    maxCols = 5,
                    sheetName = "Sheet1",
                    sheetIndex = 0
                )
            }
        )
    }

    private fun generateMockTableData(): List<List<ExcelCell>> {
        return List(10) { row ->
            List(5) { col ->
                ExcelCell(
                    colspan = 1,
                    cellType = 1, // 文本类型
                    mergeId = "",
                    rowspan = 1,
                    merged = false,
                    isMainCell = false,
                    originalRow = true,
                    value = "Cell $row,$col",
                    option = emptyList()
                )
            }
        }
    }
}

// 用于解析JSON的包装类
data class ExcelDataWrapper(
    val code: Int,
    val data: ExcelResponse,
    val msg: String
)