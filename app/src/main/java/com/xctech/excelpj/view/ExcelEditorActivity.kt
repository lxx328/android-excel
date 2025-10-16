package com.xctech.excelpj.view

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.view.size
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.xctech.excelpj.R
import com.xctech.excelpj.data.ExcelInfo
import com.xctech.excelpj.databinding.ActivityExcelEditorBinding
import com.xctech.excelpj.dialog.ExcelImageDialog
import com.xctech.excelpj.viewmodel.ExcelViewModel
import kotlinx.coroutines.*

class ExcelEditorActivity : AppCompatActivity(), ExcelTableView.OnCellClickListener {

    private lateinit var binding: ActivityExcelEditorBinding
    private val viewModel: ExcelViewModel by viewModels()
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    // 添加CellEditorDialog
    private var cellEditorDialog: Dialog? = null
    private var dialogBinding: com.xctech.excelpj.databinding.DialogCellEditorBinding? = null

    // 用于延迟更新
    private var updateJob: Job? = null
    private val updateScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 当前选中的sheet索引
    private var currentSheetIndex = 0

    private var isUpdatingSheetFromCode = false

    private val backgroundColors = listOf(
        "" to "White", // 空字符串表示白色
        "#FFEBEE" to "Red",
        "#E3F2FD" to "Blue",
        "#E8F5E9" to "Green",
        "#FFF9C4" to "Yellow",
        "#F3E5F5" to "Purple",
        "#FCE4EC" to "Pink"
    )

    private val presetSymbols = listOf(
        "Select symbol...",
        "♥", "❀", "★", "✓", "✗",
        "●", "■", "▲", "◆", "☺",
        "→", "←", "↑", "↓", "⇒"
    )

    private val presetSymbolsB = listOf(
        "♥", "❀", "★", "✓", "✗",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExcelEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 使用构造器模式创建ExcelTableView
        // 使用构造器模式配置ExcelTableView
        val config = ExcelTableView.Builder()
            .showEditedCellBorder(true)
            .maxRetryCount(3)
            .isFocus( true)
            .build()
        binding.excelTableView.applyConfig(config)

        setupViews()
        observeViewModel()

        // 加载数据
        val formId = intent.getIntExtra("FORM_ID", 5)

        // 设置表单ID用于状态管理
        binding.excelTableView.setFormId(formId)

        // 传递context给ViewModel
        viewModel.setRepositoryContext(this)
        viewModel.loadExcelData(formId)
    }

    override fun onDestroy() {
        super.onDestroy()
        updateScope.cancel()
        dismissCellEditorDialog()
    }

    private fun setupViews() {
        // 设置Excel表格视图
        binding.excelTableView.setOnCellClickListener(this)

        // 设置底部编辑栏
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomEditBar)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.peekHeight = 0
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    viewModel.closeEditor()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        // 设置颜色选择器
        setupColorPalette()

        // 设置缩放控制
        binding.btnZoomIn.setOnClickListener { viewModel.zoomIn() }
        binding.btnZoomOut.setOnClickListener { viewModel.zoomOut() }

        // 设置编辑框监听 - 实时更新
        setupEditTextListener()

        setQueryData()
        // 设置重新加载按钮
        setupReloadButton()
    }

    private fun setupEditTextListener() {
        var isUpdatingFromViewModel = false

        binding.tabLayoutSheets.tabMode = TabLayout.MODE_FIXED

        binding.etCellContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdatingFromViewModel) return
                // 取消之前的更新任务
                updateJob?.cancel()
                // 延迟20ms更新，避免频繁更新
                updateJob = updateScope.launch {
                    delay(20)
                    viewModel.selectedCell.value?.let { selection ->
                        viewModel.updateCellContent(
                            selection.row,
                            selection.col,
                            s.toString()
                        )
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {

            }
        })

        // 设置完成按钮监听
        binding.etCellContent.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                // 立即更新，不延迟
                updateJob?.cancel()
                viewModel.selectedCell.value?.let { selection ->
                    val newValue = binding.etCellContent.text.toString()
                    viewModel.updateCellContent(
                        selection.row,
                        selection.col,
                        newValue
                    )
                    // 确保当前选中的单元格与编辑框内容同步
                    binding.etCellContent.setText(newValue)
                }
                // 隐藏键盘
                binding.etCellContent.clearFocus()
                true
            } else {
                false
            }
        }

        // 观察选中单元格变化时更新编辑框内容
        viewModel.selectedCell.observe(this) { selection ->
            selection?.let {
                isUpdatingFromViewModel = true
                binding.etCellContent.setText(it.cell.value)
                // 只有在编辑框有焦点时才设置光标位置
                if (binding.etCellContent.hasFocus()) {
                    binding.etCellContent.text?.let { it1 ->
                        it.cell.value.length.coerceAtMost(
                            it1.length)
                    }?.let { it2 -> binding.etCellContent.setSelection(it2) }
                }
                isUpdatingFromViewModel = false
            }
        }
    }

    private fun setupColorPalette() {
        binding.colorPalette.removeAllViews()

        backgroundColors.forEach { (colorString, colorName) ->
            val colorView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.color_circle_size),
                    resources.getDimensionPixelSize(R.dimen.color_circle_size)
                ).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.color_circle_margin)
                }

                // 设置背景
                background = ContextCompat.getDrawable(this@ExcelEditorActivity, R.drawable.color_item_background)

                // 使用颜色字符串设置背景色
                if (colorString.isNotEmpty()) {
                    try {
                        val color = Color.parseColor(colorString)
                        background?.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                            color, BlendModeCompat.SRC_ATOP
                        )
                    } catch (e: IllegalArgumentException) {
                        // 如果颜色解析失败，使用默认白色
                    }
                }

                setOnClickListener {
                    // 实现颜色选择逻辑
                    viewModel.selectedCell.value?.let { selection ->
                        viewModel.updateCellBackgroundColorString(selection.row, selection.col, colorString)
                    }
                }
            }
            binding.colorPalette.addView(colorView)
        }
    }

    // 视图状态数据类
    data class ViewState(
        val scaleFactor: Float,
        val offsetX: Float,
        val offsetY: Float,
        val selectedSheetIndex: Int
    )

    private fun observeViewModel() {
        // 观察Excel数据
        // 观察Excel数据
        viewModel.excelData.observe(this) { response ->
            // 隐藏加载状态
            showLoading(false)

            // 保存当前视图状态（如果有）
            val currentViewState = if (binding.excelTableView.getExcelInfo() != null) {
                binding.excelTableView.saveCurrentViewState()
            } else {
                null
            }

            // 检查response是否为null
            if (response == null) {
                Log.e("ExcelEditorActivity", "Received null response from viewModel")
                showError("加载数据失败：接收到空数据")
                return@observe
            }

            // 基本信息显示
            binding.tvTitle.text = response.formName
            binding.tvDescription.text = "${response.formNo} v${response.version}"

            // 设置表单ID
            binding.excelTableView.setFormId(response.id)

            // 获取当前sheet
            val currentSheet = viewModel.getCurrentSheet()

            // 检查是否有多个sheet
            if (response.excelInfo.size > 1) {
                Log.d("ExcelEditorActivity", "Multiple sheets found: ${response.excelInfo.size}")

                // 显示sheet切换控件
                setupSheetTabs(response.excelInfo)

                // 设置当前sheet到视图中
                currentSheet?.let { sheet ->
                    binding.excelTableView.setExcelInfo(
                        sheet,
                        saveCurrentState = true,
                        preserveViewState = currentViewState != null
                    )
                }
            } else if (response.excelInfo.isNotEmpty()) {
                // 只有一个sheet
                Log.d("ExcelEditorActivity", "Single sheet found")

                // 隐藏TabLayout
                binding.tabLayoutSheets.visibility = View.GONE

                // 设置唯一的sheet
                binding.excelTableView.setExcelInfo(
                    response.excelInfo[0],
                    saveCurrentState = true,
                    preserveViewState = currentViewState != null
                )
            } else {
                // 没有sheet数据
                Log.e("ExcelEditorActivity", "No sheet data available")
                showError("表单没有可用数据")
            }

            // 如果有临时视图状态，则恢复
            if (currentViewState != null) {
                binding.excelTableView.applyViewState(
                    currentViewState.scaleFactor,
                    currentViewState.offsetX,
                    currentViewState.offsetY
                )
            }

            // 每次数据变更时进行一次状态检查
            binding.root.postDelayed({
                val currentSheet = viewModel.getCurrentSheet()
                val currentScaleFactor = binding.excelTableView.getCurrentScaleFactor()
                Log.d("ExcelEditorActivity", "Data updated - current sheet: ${currentSheet?.sheetName}, " +
                        "scale: $currentScaleFactor")

                // 可以在这里添加额外检查
            }, 500)
        }

        // 观察当前sheet更新事件
        viewModel.currentSheetUpdateEvent.observe(this) { (sheetIndex, sheetName) ->
            Log.d("ExcelEditorActivity", "Sheet updated: index=$sheetIndex, name=$sheetName")

            // 更新Tab选择
            if (!isUpdatingSheetFromCode && sheetIndex < binding.tabLayoutSheets.tabCount) {
                isUpdatingSheetFromCode = true
                try {
                    binding.tabLayoutSheets.getTabAt(sheetIndex)?.select()
                } finally {
                    isUpdatingSheetFromCode = false
                }
            }

            // 更新当前显示的sheet
            viewModel.excelData.value?.let { data ->
                data.excelInfo.getOrNull(sheetIndex)?.let { sheet ->
                    // 保存当前视图状态
                    val currentViewState = binding.excelTableView.saveCurrentViewState()

                    // 更新显示内容
                    binding.excelTableView.setExcelInfo(
                        sheet,
                        saveCurrentState = true,
                        preserveViewState = true
                    )

                    // 应用视图状态
                    binding.excelTableView.applyViewState(
                        currentViewState.scaleFactor,
                        currentViewState.offsetX,
                        currentViewState.offsetY
                    )
                }
            }
        }

        // 观察选中的单元格
        viewModel.selectedCell.observe(this) { selection ->
            selection?.let {
                binding.excelTableView.setSelectedCell(it.row, it.col)

                //区分可读图片和编辑框
                when (it.cell.cellType) {
                    9 -> {
                        showCellImageDialog(it.cell.value)
                    }

                    else -> {

                        // 显示Dialog
                        showCellEditorDialog(binding.excelTableView.getRequestFocusOnEdit())

                        // 更新Dialog中的编辑框内容
                        dialogBinding?.etCellContent?.setText(it.cell.value)

                        // 设置光标位置到文本末尾
                        dialogBinding?.etCellContent?.setSelection(it.cell.value.length)

                        // 根据单元格类型显示不同的编辑选项
                        when (it.cell.cellType) {
                            3 -> { // 选项类型
                                // 如果有预定义选项，设置到预设内容
                                if (it.cell.option.isNotEmpty()) {
                                    setupDialogOptionsContent(it.cell.option)
                                }
                            }

                            5, 6 -> { // 图片或签名
                                // 可以在这里添加特殊处理
                            }

                            7 -> { // 置灰
                                dialogBinding?.etCellContent?.isEnabled = false
                            }


                            else -> {
                                dialogBinding?.etCellContent?.isEnabled = true
                            }
                        }

                    }
                }
            }
        }

        // 观察单元格更新事件
        viewModel.cellUpdateEvent.observe(this) { event ->
            // 保存当前视图状态
            val currentViewState = binding.excelTableView.saveCurrentViewState()

            when (event.type) {
                ExcelViewModel.UpdateType.CONTENT -> {
                    binding.excelTableView.updateCell(event.row, event.col)

                    // 标记单元格为已编辑
                    viewModel.excelData.value?.let { data ->
                        val cell = data.excelInfo[currentSheetIndex].tableData.getOrNull(event.row)
                            ?.getOrNull(event.col)
                        cell?.let {
                            binding.excelTableView.markCellAsEdited(event.row, event.col, it)
                        }
                    }
                }

                ExcelViewModel.UpdateType.BACKGROUND_COLOR -> {
                    viewModel.getCellBackgroundColor(event.row, event.col)?.let { color ->
                        binding.excelTableView.setCellBackgroundColor(event.row, event.col, color)
                    }
                }

                ExcelViewModel.UpdateType.ALL -> {
                    binding.excelTableView.invalidate()
                }
            }

            // 恢复视图状态
            binding.excelTableView.applyViewState(
                currentViewState.scaleFactor,
                currentViewState.offsetX,
                currentViewState.offsetY
            )
        }

        // 观察编辑状态
        viewModel.isEditing.observe(this) { isEditing ->
            bottomSheetBehavior.state = if (isEditing) {
                BottomSheetBehavior.STATE_EXPANDED
            } else {
                BottomSheetBehavior.STATE_HIDDEN
            }
        }

        // 观察缩放比例
        viewModel.scaleFactor.observe(this) { scale ->
            binding.excelTableView.setScaleFactor(scale)
        }

        // 观察错误信息
        viewModel.errorMessage.observe(this) { message ->
            com.google.android.material.snackbar.Snackbar
                .make(
                    binding.root,
                    message,
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                )
                .show()
        }

        viewModel.viewStateRestoreEvent.observe(this) { event ->
            Log.d("ExcelEditorActivity", "View state restore event: sheet=${event.sheetName}, " +
                    "index=${event.sheetIndex}, scale=${event.scale}")

            // 如果scale为0，表示使用已保存的状态，不应用传入的状态
            if (event.scale > 0) {
                // 应用传入的视图状态
                binding.excelTableView.applyViewState(event.scale, event.offsetX, event.offsetY)
            }

            // 切换到目标sheet（但不应用视图状态）
            viewModel.excelData.value?.let { data ->
                // 找到目标sheet
                val targetSheetIndex = data.excelInfo.indexOfFirst {
                    it.sheetIndex == event.sheetIndex || it.sheetName == event.sheetName
                }

                if (targetSheetIndex >= 0 && targetSheetIndex < binding.tabLayoutSheets.tabCount) {
                    // 标记为程序切换
                    isUpdatingSheetFromCode = true
                    try {
                        binding.tabLayoutSheets.getTabAt(targetSheetIndex)?.select()
                        currentSheetIndex = targetSheetIndex
                    } finally {
                        isUpdatingSheetFromCode = false
                    }
                }
            }
        }

    }

    // 设置sheet切换的TabLayout
    private fun setupSheetTabs(excelInfoList: List<ExcelInfo>) {
        // 先检查列表是否为空
        if (excelInfoList.isEmpty()) {
            binding.tabLayoutSheets.visibility = View.GONE
            return
        }

        // 只有在有多个sheet时才显示
        if (excelInfoList.size <= 1) {
            binding.tabLayoutSheets.visibility = View.GONE
            return
        }

        // 保存当前选中的tab位置
        val currentSelectedTabPosition = binding.tabLayoutSheets.selectedTabPosition
        val currentSelectedSheetName = if (currentSelectedTabPosition >= 0 &&
            binding.tabLayoutSheets.tabCount > 0) {
            binding.tabLayoutSheets.getTabAt(currentSelectedTabPosition)?.text?.toString()
        } else {
            null
        }

        // 获取当前sheet索引
        val currentSheet = viewModel.getCurrentSheet()
        val currentSheetIndex = currentSheet?.sheetIndex ?: 0

        // 清除现有监听器和tabs
        binding.tabLayoutSheets.clearOnTabSelectedListeners()
        binding.tabLayoutSheets.removeAllTabs()
        binding.tabLayoutSheets.visibility = View.VISIBLE

        // 按sheetIndex排序后添加tabs
        val sortedSheets = excelInfoList.sortedBy { it.sheetIndex }

        sortedSheets.forEach { sheet ->
            val tab = binding.tabLayoutSheets.newTab()
            tab.text = sheet.sheetName
            tab.tag = sheet.sheetIndex  // 使用tag存储sheetIndex
            binding.tabLayoutSheets.addTab(tab, false)  // false表示不自动选择
        }

        // 找到应该选择的tab索引
        var targetTabIndex = -1

        // 优先使用当前sheet索引
        targetTabIndex = binding.tabLayoutSheets.tabCount - 1
        for (i in 0 until binding.tabLayoutSheets.tabCount) {
            val tab = binding.tabLayoutSheets.getTabAt(i)
            if (tab?.tag as? Int == currentSheetIndex) {
                targetTabIndex = i
                break
            }
        }

        // 如果找不到匹配的索引，尝试使用名称匹配
        if (targetTabIndex < 0 && !currentSelectedSheetName.isNullOrEmpty()) {
            for (i in 0 until binding.tabLayoutSheets.tabCount) {
                if (binding.tabLayoutSheets.getTabAt(i)?.text == currentSelectedSheetName) {
                    targetTabIndex = i
                    break
                }
            }
        }

        // 如果还是找不到，使用第一个
        if (targetTabIndex < 0 || targetTabIndex >= binding.tabLayoutSheets.tabCount) {
            targetTabIndex = 0
        }

        // 设置Tab选择监听器
        binding.tabLayoutSheets.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (isUpdatingSheetFromCode) {
                    Log.d("ExcelEditorActivity", "Tab selection ignored (program update)")
                    return
                }

                tab?.let {
                    val position = it.position
                    val sheetIndex = it.tag as? Int ?: position

                    // 保存当前状态
                    binding.excelTableView.saveCurrentSheetState()

                    // 重置选择状态
                    binding.excelTableView.setSelectedCell(-1, -1)
                    viewModel.closeEditor()

                    // 查找目标sheet
                    viewModel.excelData.value?.let { data ->
                        val targetSheet = data.excelInfo.find { it.sheetIndex == sheetIndex }
                        targetSheet?.let { sheet ->
                            // 更新当前sheet
                            viewModel.updateCurrentSheet(sheet.sheetName, sheet.sheetIndex)

                            // 强制使用该sheet自己的状态，绝对禁止preserveViewState
                            binding.excelTableView.setExcelInfo(
                                sheet,
                                saveCurrentState = false,  // 已经保存过了
                                preserveViewState = false  // 强制不保留
                            )

                            Log.d("ExcelEditorActivity",
                                "Switched to sheet[${sheet.sheetIndex}]: ${sheet.sheetName} - " +
                                        "scale: ${binding.excelTableView.getCurrentScaleFactor()}")
                        }
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // 选择目标tab
        if (targetTabIndex >= 0 && targetTabIndex < binding.tabLayoutSheets.tabCount) {
            Log.d("ExcelEditorActivity", "Selecting tab at position: $targetTabIndex")
            isUpdatingSheetFromCode = true
            try {
                binding.tabLayoutSheets.getTabAt(targetTabIndex)?.select()
            } finally {
                isUpdatingSheetFromCode = false
            }
        }
    }
    private fun setupDialogOptionsContent(options: List<Any>) {
        dialogBinding?.presetContentContainer?.removeAllViews()

        options.forEach { option ->
            val textView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    resources.getDimensionPixelSize(R.dimen.button_height)
                ).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_small)
                }

                text = option.toString()
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.rounded_background)
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.spacing_small),
                    0,
                    resources.getDimensionPixelSize(R.dimen.spacing_small),
                    0
                )

                setOnClickListener {
                    viewModel.selectedCell.value?.let { selection ->
                        viewModel.updateCellContent(
                            selection.row,
                            selection.col,
                            option.toString()
                        )
                        dialogBinding?.etCellContent?.setText(option.toString())
                    }
                }
            }
            dialogBinding?.presetContentContainer?.addView(textView)
        }
    }

    private fun showCellEditorDialog(isFocus: Boolean) {
        // 创建Dialog
        cellEditorDialog = Dialog(this, R.style.BottomSheetDialogTheme)
        dialogBinding = com.xctech.excelpj.databinding.DialogCellEditorBinding.inflate(layoutInflater)
        cellEditorDialog?.setContentView(dialogBinding!!.root)

        // 设置Dialog属性 高宽都设为屏幕的60%
        val window = cellEditorDialog?.window
        window?.attributes = window?.attributes?.apply {
            width = (resources.displayMetrics.widthPixels * 0.6).toInt()
            height = (resources.displayMetrics.heightPixels * 0.4).toInt()
        }
        window?.setGravity(Gravity.CENTER)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        cellEditorDialog!!.setOnDismissListener{
            updateJob?.cancel()
            viewModel.selectedCell.value?.let { selection ->
                viewModel.applyEditedValue(selection.row, selection.col)
            }
            // 隐藏键盘
            dialogBinding?.etCellContent?.clearFocus()

        }
        cellEditorDialog?.setOnShowListener {
            // 显示键盘
            if (isFocus) {
                dialogBinding?.etCellContent?.requestFocus()
            }
        }

        // 设置预设颜色内容
        setupDialogColorContent()

        // 设置预设选项内容
        setupDialogPresetContent()

        // 设置编辑框监听
        setupDialogEditTextListener()

        // 显示Dialog
        cellEditorDialog?.show()
    }

    private fun showCellImageDialog(imageUrl: String) {
        try {
            // 正确解析URI
            val uri = Uri.parse(imageUrl)
            Log.d("ImageDialog", "显示图片URI: $uri")

            val dialog = ExcelImageDialog.Builder(this)
                .setImageUri(uri)
                .setTitle("图片查看")
                .setBackgroundColor(Color.WHITE)
                .setLoadingText("正在加载图片...")
                .setErrorText("加载失败，点击重试")
                .enableSave(true)
                .enableShare(true)
                .setCancelable(true)
                .build()

            dialog.show()
        } catch (e: Exception) {
            Log.e("ImageDialog", "解析图片URL失败", e)
        }
    }
    private fun setupDialogColorContent() {

        dialogBinding?.colorPalette?.removeAllViews()

        backgroundColors.forEach { (colorRes, colorName) ->
            val container = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.color_circle_size),
                    resources.getDimensionPixelSize(R.dimen.color_circle_size)
                ).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.color_circle_margin)
                }
            }

            // 内部颜色视图
            val colorView = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(2, 2, 2, 2) // 为边框留出空间
                }

                // 设置圆角背景
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 18f * resources.displayMetrics.density
                    setColor(parseColorSafely(colorRes))
                }
            }

            container.addView(colorView)

            // 点击事件
            container.setOnClickListener {
                viewModel.selectedCell.value?.let { selection ->
                    viewModel.updateCellBackgroundColorString(selection.row, selection.col, colorRes)
                    updateColorSelection(container, dialogBinding?.colorPalette)
                }
            }

            dialogBinding?.colorPalette?.addView(container)
        }        // 设置当前选中颜色的高亮
        updateColorSelectionForCurrentCell()
    }

    private fun parseColorSafely(colorString: String, defaultColor: Int = Color.WHITE): Int {
        return if (colorString.isNotEmpty()) {
            try {
                Color.parseColor(colorString)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                defaultColor
            }
        } else {
            defaultColor
        }
    }

    // 更新颜色选择器的选中状态
    private fun updateColorSelection(selectedView: View, colorPalette: LinearLayout?) {
        colorPalette?.let { palette ->
            // 移除所有边框
            for (i in 0 until palette.childCount) {
                val child = palette.getChildAt(i)
                if (child is FrameLayout) {
                    child.foreground = null
                }
            }

            // 为选中项添加边框
            if (selectedView is FrameLayout) {
                selectedView.foreground = ContextCompat.getDrawable(this, R.drawable.color_item_selected_border)
            }
        }
    }

    // 根据当前单元格的背景色更新颜色选择器的选中状态
    private fun updateColorSelectionForCurrentCell() {
        viewModel.selectedCell.value?.let { selection ->
            // 获取当前单元格的背景色
            val currentBgc = selection.cell.bgc ?: ""

            // 找到匹配的颜色项并设置选中状态
            dialogBinding?.colorPalette?.let { palette ->
                for (i in 0 until backgroundColors.size) {
                    val (colorString, _) = backgroundColors[i]
                    if (colorString == currentBgc) {
                        val colorView = palette.getChildAt(i)
                        if (colorView is View) {
                            updateColorSelection(colorView, palette)
                            break
                        }
                    }
                }
            }
        }
    }

    private fun setupDialogPresetContent() {

        dialogBinding?.presetContentContainer?.removeAllViews()

        presetSymbols.forEachIndexed { index, symbol ->
            if (index >= 0) { // 跳过第一个"Select symbol..."项
                val textView = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        resources.getDimensionPixelSize(R.dimen.button_height)
                    ).apply {
                        marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_small)
                    }

                    text = symbol
                    gravity = Gravity.CENTER
                    setBackgroundResource(R.drawable.rounded_background)
                    setPadding(
                        resources.getDimensionPixelSize(R.dimen.spacing_small),
                        0,
                        resources.getDimensionPixelSize(R.dimen.spacing_small),
                        0
                    )

                    setOnClickListener {
                        // 在编辑框末尾追加内容而不是覆盖
                        //检测当前是否dialogBinding?.etCellContent有光标有的话在光标处增加无的话在末尾增加
                        val editText = dialogBinding?.etCellContent
                        val currentText = editText?.text?.toString() ?: ""
                        val selectionStart = editText?.selectionStart ?: currentText.length

                        // 在光标位置插入符号
                        val newText = StringBuilder(currentText).apply {
                            insert(selectionStart, symbol)
                        }.toString()

                        editText?.setText(newText)

                        // 将光标移动到插入符号之后的位置
                        val newCursorPosition = selectionStart + symbol.length
                        editText?.setSelection(newCursorPosition)

                        // 更新ViewModel中的当前编辑值
                        viewModel.updateCurrentEditingValue(newText)
                    }
                }
                dialogBinding?.presetContentContainer?.addView(textView)
            }
        }
    }

    private fun setupDialogEditTextListener() {
        var isUpdatingFromViewModel = false

        dialogBinding?.etCellContent?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingFromViewModel) return

                // 更新当前编辑的值
                viewModel.updateCurrentEditingValue(s.toString())
            }
        })

    }

    private fun setQueryData(){
        binding.btnQuery.setOnClickListener(){
            //调用viewmodel方法
            val a = viewModel.getEditedCells()
            //规则打印成json的log
            Log.d("ExcelEditorActivity", Gson().toJson(a))
        }
    }

    private fun dismissCellEditorDialog() {
        cellEditorDialog?.dismiss()
        cellEditorDialog = null
        dialogBinding = null
    }
    override fun onCellClick(row: Int, col: Int) {
        viewModel.selectCell(row, col)
    }

    private fun setupReloadButton() {
        binding.btnReload.setOnClickListener {
            // 获取当前和目标表单ID
            val currentFormId = binding.excelTableView.getFormId()
            val targetFormId = intent.getIntExtra("FORM_ID", 5)

            // 获取当前sheet信息
            val currentSheet = viewModel.getCurrentSheet()

            Log.d("ExcelEditorActivity",
                "Reload: current form=$currentFormId, target form=$targetFormId, " +
                        "current sheet=${currentSheet?.sheetName}, index=${currentSheet?.sheetIndex}")

            // 显示加载状态
            showLoading(true)

            // 保护措施
            binding.root.postDelayed({
                if (binding.progressBar.visibility == View.VISIBLE) {
                    showLoading(false)
                }
            }, 5000)

            // 在刷新前强制保存当前sheet状态
            binding.excelTableView.saveCurrentSheetState()

            if (currentFormId == targetFormId && currentFormId != -1 && currentSheet != null) {
                // 同一表单ID - 保留当前sheet信息用于恢复
                val refreshState = ExcelViewModel.SheetRefreshState(
                    formId = currentFormId,
                    sheetIndex = currentSheet.sheetIndex,
                    sheetName = currentSheet.sheetName,
                    scale = 0f,  // 设为0表示使用已保存的状态，不覆盖
                    offsetX = 0f,
                    offsetY = 0f
                )

                // 使用保存的状态刷新数据
                viewModel.reloadExcelData(targetFormId, refreshState)
            } else {
                // 不同表单ID - 清空所有状态
                binding.excelTableView.clearAllSheetStates()

                // 加载新表单
                viewModel.loadExcelData(targetFormId)
            }
        }
    }

    // 显示/隐藏加载状态
    private fun showLoading(isLoading: Boolean) {
        Log.d("ExcelEditorActivity", "showLoading called with: $isLoading")

        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    // 显示错误消息
    private fun showError(message: String) {
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .show()
    }

}