package com.xctech.excelpj.view

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.Gson
import com.xctech.excelpj.R
import com.xctech.excelpj.databinding.ActivityExcelEditorBinding
import com.xctech.excelpj.databinding.DialogCellEditorBinding
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

//    private val backgroundColors = listOf(
//        R.color.white to "White",
//        R.color.light_red to "Red",
//        R.color.light_blue to "Blue",
//        R.color.light_green to "Green",
//        R.color.light_yellow to "Yellow",
//        R.color.light_purple to "Purple",
//        R.color.light_pink to "Pink"
//    )

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
            .build()
        binding.excelTableView.applyConfig(config)

        setupViews()
        observeViewModel()

        // 加载数据
        val formId = intent.getIntExtra("FORM_ID", 4)
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

        // 设置符号选择器
        setupSymbolSpinner()

        // 设置缩放控制
        binding.btnZoomIn.setOnClickListener { viewModel.zoomIn() }
        binding.btnZoomOut.setOnClickListener { viewModel.zoomOut() }

        // 设置编辑框监听 - 实时更新
        setupEditTextListener()

        setQueryData()
    }

    private fun setupEditTextListener() {
        var isUpdatingFromViewModel = false

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
//    private fun setupColorPalette() {
//        binding.colorPalette.removeAllViews()
//
//        backgroundColors.forEach { (colorRes, colorName) ->
//            val colorView = View(this).apply {
//                layoutParams = android.widget.LinearLayout.LayoutParams(
//                    resources.getDimensionPixelSize(R.dimen.color_circle_size),
//                    resources.getDimensionPixelSize(R.dimen.color_circle_size)
//                ).apply {
//                    marginEnd = resources.getDimensionPixelSize(R.dimen.color_circle_margin)
//                }
//
//                background = ContextCompat.getDrawable(this@ExcelEditorActivity, R.drawable.color_circle_background)
//                backgroundTintList = ContextCompat.getColorStateList(this@ExcelEditorActivity, colorRes)
//
//                setOnClickListener {
//                    // 实现颜色选择逻辑
//                    viewModel.selectedCell.value?.let { selection ->
//                        val color = ContextCompat.getColor(this@ExcelEditorActivity, colorRes)
//                        viewModel.updateCellBackgroundColor(selection.row, selection.col, color)
//                    }
//                }
//            }
//            binding.colorPalette.addView(colorView)
//        }
//    }
    private fun setupSymbolSpinner() {
        // 1. 先创建和设置适配器
        val adapter = ArrayAdapter(this, R.layout.spinner_item, presetSymbolsB)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.spinnerPresets.adapter = adapter

        // 2. 设置下拉框高度
        binding.spinnerPresets.post {
            try {
                val popup = Spinner::class.java.getDeclaredField("mPopup")
                popup.isAccessible = true
                val popupWindow = popup.get(binding.spinnerPresets)

                if (popupWindow is android.widget.ListPopupWindow) {
                    popupWindow.height = resources.getDimensionPixelSize(R.dimen.spinner_dropdown_height)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. 使用post确保View完全初始化后再设置监听器
        binding.spinnerPresets.post {
            binding.spinnerPresets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    println("Spinner onItemSelected triggered: position=$position") // 调试日志

                    if (position > 0) {
                        viewModel.selectedCell.value?.let { selection ->
                            val selectedSymbol = presetSymbols[position]
                            println("Selected symbol: $selectedSymbol") // 调试日志

                            // 更新单元格内容
                            viewModel.updateCellContent(
                                selection.row,
                                selection.col,
                                selectedSymbol
                            )

                            // 直接更新编辑框
                            binding.etCellContent.setText(selectedSymbol)
                            binding.etCellContent.setSelection(selectedSymbol.length)
                        }

                        // 延迟重置，避免干扰
                        binding.spinnerPresets.postDelayed({
                            binding.spinnerPresets.setSelection(0, false)
                        }, 100)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    println("Spinner onNothingSelected triggered") // 调试日志
                }
            }
        }
    }    private fun observeViewModel() {
        // 观察Excel数据
        viewModel.excelData.observe(this) { response ->
            binding.tvTitle.text = response.formName
            binding.tvDescription.text = "${response.formNo} v${response.version}"
            binding.excelTableView.setExcelInfo(response.excelInfo)
        }

        // 观察选中的单元格
        viewModel.selectedCell.observe(this) { selection ->
            selection?.let {
                binding.excelTableView.setSelectedCell(it.row, it.col)

                // 显示Dialog
                showCellEditorDialog()

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

        // 观察单元格更新事件
        viewModel.cellUpdateEvent.observe(this) { event ->
            when (event.type) {
                ExcelViewModel.UpdateType.CONTENT -> {
                    binding.excelTableView.updateCell(event.row, event.col)
                    // 标记单元格为已编辑
                    viewModel.excelData.value?.let { data ->
                        val cell = data.excelInfo.tableData.getOrNull(event.row)?.getOrNull(event.col)
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
        }

        // 观察编辑状态
        viewModel.isEditing.observe(this) { isEditing ->
            bottomSheetBehavior.state = if (isEditing) {
                BottomSheetBehavior.STATE_EXPANDED
            } else {
                BottomSheetBehavior.STATE_HIDDEN
            }

            // 当编辑状态改变时，重置spinner选择
            if (!isEditing) {
                binding.spinnerPresets.setSelection(0)
            }
        }

        // 观察缩放比例
        viewModel.scaleFactor.observe(this) { scale ->
            binding.excelTableView.setScaleFactor(scale)
        }

        // 观察错误信息
        viewModel.errorMessage.observe(this) { message ->
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                .show()
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

    private fun setupOptionsSpinner(options: List<Any>) {
        val optionStrings = mutableListOf("Select option...")
        optionStrings.addAll(options.map { it.toString() })

        val adapter = ArrayAdapter(this, R.layout.spinner_item, optionStrings)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.spinnerPresets.adapter = adapter

        // 设置选项Spinner的监听器
        binding.spinnerPresets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0 && position < optionStrings.size) {
                    viewModel.selectedCell.value?.let { selection ->
                        val newValue = optionStrings[position]
                        viewModel.updateCellContent(
                            selection.row,
                            selection.col,
                            newValue
                        )
                    }
                    // 重置选择到第一个选项
                    parent?.post {
                        binding.spinnerPresets.setSelection(0)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }


    private fun showCellEditorDialog() {
        // 创建Dialog
        cellEditorDialog = Dialog(this, R.style.BottomSheetDialogTheme)
        dialogBinding = DialogCellEditorBinding.inflate(layoutInflater)
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

        // 设置预设颜色内容
        setupDialogColorContent()

        // 设置预设选项内容
        setupDialogPresetContent()

        // 设置编辑框监听
        setupDialogEditTextListener()

        // 显示Dialog
        cellEditorDialog?.show()
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
//                        viewModel.selectedCell.value?.let { selection ->
//                            viewModel.updateCellContent(
//                                selection.row,
//                                selection.col,
//                                symbol
//                            )
//                            dialogBinding?.etCellContent?.setText(symbol)
//                        }
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


}