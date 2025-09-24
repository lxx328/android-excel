# ExcelLibrary AAR 使用说明

## 简介

ExcelLibrary 是一个功能丰富的 Android Excel 编辑器库，支持查看、编辑和操作 Excel 表格数据。该库提供了完整的 Excel 表格展示和编辑功能，包括多种单元格类型支持、背景颜色设置、符号插入等特性。

## 功能特性

### 核心功能

- **Excel 表格展示**：以表格形式展示 Excel 数据，支持合并单元格显示

- **单元格编辑**：点击任意单元格进行内容编辑

- 多种单元格类型支持(目前暂支持文本类型)

  ：

    - 文本类型（类型1）
    - 平均值类型（类型2）
    - 选项类型（类型3）
    - 范围类型（类型4）
    - 图片类型（类型5）
    - 签名类型（类型6）
    - 置灰类型（类型7）
    - 搜索项类型（类型8）

- 视觉定制

  ：

    - 背景颜色设置（支持自定义颜色）
    - 预设符号插入
    - 缩放控制（放大/缩小）

- 用户界面

  ：

    - 底部编辑栏
    - 响应式设计
    - 直观的操作界面

## 安装

### 1. 添加依赖

在您的项目级别的 `build.gradle` 文件中添加：

```
gradleallprojects {
    repositories {
        google()
        mavenCentral()
        // 添加 JitPack 仓库（如果需要从 GitHub 打包）
        maven { url 'https://jitpack.io' }
    }
}
```

在您的应用级别的 `build.gradle` 文件中添加依赖：

```
gradledependencies {
    implementation 'com.xctech:excellibrary:1.0.0'
    // 或者如果从本地 AAR 文件添加：
    // implementation files('libs/excellibrary-release.aar')
}
```

### 2. 权限配置

在您的 [AndroidManifest.xml](file:///E:/Local_project/android_pj/excelPj/app/src/main/AndroidManifest.xml) 中添加必要的权限：

xml

```xml
<uses-permission android:name="android.permission.INTERNET" /> <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

## 使用方法

### 1. 基本用法

在您的 Activity 或 Fragment 中使用 ExcelTableView：

kotlin

```kotlin
// 在布局文件中添加 <com.xctech.excellibrary.view.ExcelTableView     android:id="@+id/excelTableView"     android:layout_width="match_parent"     android:layout_height="match_parent" />
```

kotlin

```kotlin
// 在 Activity 中使用 class MainActivity : AppCompatActivity() {     private lateinit var excelTableView: ExcelTableView          override fun onCreate(savedInstanceState: Bundle?) {         super.onCreate(savedInstanceState)         setContentView(R.layout.activity_main)                  excelTableView = findViewById(R.id.excelTableView)                  // 配置 ExcelTableView         val config = ExcelTableView.Builder()             .showEditedCellBorder(true)             .editedCellBorderColor(Color.parseColor("#10B981"))             .build()         excelTableView.applyConfig(config)                  // 设置数据         val excelInfo = getExcelInfo() // 获取您的 Excel 数据         excelTableView.setExcelInfo(excelInfo)                  // 设置单元格点击监听         excelTableView.setOnCellClickListener(object : ExcelTableView.OnCellClickListener {             override fun onCellClick(row: Int, col: Int) {                 // 处理单元格点击事件                 handleCellClick(row, col)             }         })     } }
```

### 2. 使用 CellEditorDialog

kotlin

```kotlin
// 创建并显示单元格编辑对话框 private fun showCellEditorDialog() {     val dialog = CellEditorDialog.Builder(this)         .setBackgroundColorList(customColors)  // 可选：自定义颜色列表         .setPresetSymbols(customSymbols)       // 可选：自定义符号列表         .build()          // 设置监听器     dialog.setOnContentChangeListener { content ->         // 处理内容变化     }          dialog.setOnColorSelectedListener { colorString ->         // 处理颜色选择     }          dialog.setOnSymbolSelectedListener { symbol ->         // 处理符号选择     }          // 设置初始内容     dialog.setCellContent("Initial content")          // 显示对话框     dialog.show() }
```

### 3. 使用 ViewModel

kotlin

```kotlin
class ExcelActivity : AppCompatActivity() {     private val viewModel: ExcelViewModel by viewModels()          override fun onCreate(savedInstanceState: Bundle?) {         super.onCreate(savedInstanceState)         setContentView(R.layout.activity_excel)                  // 设置 Repository 上下文         viewModel.setRepositoryContext(this)                  // 加载数据         viewModel.loadExcelData(formId)                  // 观察数据变化         viewModel.excelData.observe(this) { response ->             // 更新 UI         }                  viewModel.selectedCell.observe(this) { selection ->             // 处理单元格选择         }     } }
```

## 自定义配置

### 1. 颜色配置

kotlin

```kotlin
// 使用 Builder 模式自定义颜色 val customColors = listOf(     "#000000" to "Black",     "#FF0000" to "Red",     "#00FF00" to "Green",     "#0000FF" to "Blue" )  val dialog = CellEditorDialog.Builder(context)     .setBackgroundColorList(customColors)     .build()
```

### 2. 符号配置

kotlin

```kotlin
// 自定义符号列表 val customSymbols = listOf(     "☀", "☁", "☂", "☃", "☄",      "★", "☆", "☎", "☏", "☐",      "☑", "☒", "♠", "♥", "♦", "♣" )  val dialog = CellEditorDialog.Builder(context)     .setPresetSymbols(customSymbols)     .build()
```

### 3. ExcelTableView 配置

kotlin

```kotlin
// 配置 ExcelTableView val config = ExcelTableView.Builder()     .showEditedCellBorder(true)           // 显示编辑过的单元格边框     .editedCellBorderColor(Color.GREEN)   // 设置编辑边框颜色     .build()      excelTableView.applyConfig(config)
```

## API 参考

### ExcelTableView

| 方法                                                     | 描述                   |
| :------------------------------------------------------- | :--------------------- |
| `setExcelInfo(info: ExcelInfo)`                          | 设置 Excel 数据信息    |
| `setScaleFactor(scale: Float)`                           | 设置缩放比例           |
| `setSelectedCell(row: Int, col: Int)`                    | 设置选中的单元格       |
| `setCellBackgroundColor(row: Int, col: Int, color: Int)` | 设置单元格背景色       |
| `markCellAsEdited(row: Int, col: Int, cell: ExcelCell)`  | 标记单元格为已编辑     |
| `getEditedCells(): Map<String, ExcelCell>`               | 获取所有编辑过的单元格 |
| `applyConfig(config: Config)`                            | 应用配置               |

### CellEditorDialog.Builder

| 方法                                                         | 描述             |
| :----------------------------------------------------------- | :--------------- |
| `setBackgroundColorList(colors: List<Pair<String, String>>)` | 设置颜色列表     |
| `setPresetSymbols(symbols: List<String>)`                    | 设置预设符号列表 |
| `addBackgroundColor(colorString: String, colorName: String)` | 添加单个颜色     |
| `addPresetSymbol(symbol: String)`                            | 添加单个符号     |

### ExcelViewModel

| 方法                                                         | 描述             |
| :----------------------------------------------------------- | :--------------- |
| `loadExcelData(formId: Int)`                                 | 加载 Excel 数据  |
| `selectCell(row: Int, col: Int)`                             | 选择单元格       |
| `updateCellContent(row: Int, col: Int, newValue: String)`    | 更新单元格内容   |
| `updateCellBackgroundColorString(row: Int, col: Int, colorString: String)` | 更新单元格背景色 |

## 数据模型

### ExcelResponse

kotlin

```
data class ExcelResponse(     val id: Int,     val bgcList: List<String>?,     val formNo: String,     val version: String,     val validTime: String,     val formName: String,     val deptClassLine: String,     val qaConfirm: Int,     val confirmDept: String?,     val remarks: String,     val excelInfo: ExcelInfo )
```

### ExcelInfo

kotlin

```
data class ExcelInfo(     val mergedCells: List<ExcelMerge>,     val fileName: String,     val mergedCellsCount: Int,     val tableData: List<List<ExcelCell>>,     val rowCount: Int,     val maxCols: Int )
```

### ExcelCell

kotlin

```
data class ExcelCell(     val colspan: Int,     val cellType: Int,     val mergeId: String?,     val rowspan: Int,     val bgc: String?,     val colIndex: Int?,     val rowIndex: Int?,     val merged: Boolean,     val isMainCell: Boolean,     val originalRow: Boolean,     val value: String,     val option: List<Any>,     val isEdited: Boolean = false )
```

## 技术栈

- **语言**：Kotlin

- **最低 SDK 版本**：API 24 (Android 7.0)

- **目标 SDK 版本**：API 34 (Android 14)

- **架构模式**：MVVM (Model-View-ViewModel)

- 主要库

  ：

    - AndroidX Core KTX
    - AndroidX AppCompat
    - Material Design Components
    - AndroidX RecyclerView
    - AndroidX Lifecycle (ViewModel, LiveData)
    - Apache POI (用于 Excel 处理)
    - Gson (用于 JSON 解析)

## 许可证

本库仅供学习和参考使用。

## 联系方式

如有问题或建议，请联系项目维护者1711676587@qq.com。