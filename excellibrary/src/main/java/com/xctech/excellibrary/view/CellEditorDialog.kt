package com.xctech.excellibrary.view

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import com.xctech.excellibrary.R
import com.xctech.excellibrary.databinding.DialogCellEditorBinding

class CellEditorDialog private constructor(
    context: Context,
    private var backgroundColors: List<Pair<String, String>>,
    private var presetSymbols: List<String>
) : Dialog(context, R.style.BottomSheetDialogTheme) {

    private val binding: DialogCellEditorBinding = DialogCellEditorBinding.inflate(layoutInflater)
    private var onContentChangeListener: ((String) -> Unit)? = null
    private var onColorSelectedListener: ((String) -> Unit)? = null
    private var onSymbolSelectedListener: ((String) -> Unit)? = null
    private var currentSelectedColor: String = ""

    init {

    }

    /**
     *
     * 设置根布局的背景Drawable
     */
    fun setRootBackgroundDrawable(drawable: Drawable?): CellEditorDialog {
        binding.root.background = drawable
        return this
    }

    // Builder模式构造器
    class Builder(private val context: Context) {
        private var backgroundColors: List<Pair<String, String>> = listOf(
            "" to "White",
            "#FFEBEE" to "Red",
            "#E3F2FD" to "Blue",
            "#E8F5E9" to "Green",
            "#FFF9C4" to "Yellow",
            "#F3E5F5" to "Purple",
            "#FCE4EC" to "Pink"
        )

        private var presetSymbols: List<String> = listOf(
            "♥", "❀", "★", "✓", "✗"
        )

        fun setBackgroundColorList(colors: List<Pair<String, String>>): Builder {
            this.backgroundColors = colors
            return this
        }

        fun setPresetSymbols(symbols: List<String>): Builder {
            this.presetSymbols = symbols
            return this
        }

        fun addBackgroundColor(colorString: String, colorName: String): Builder {
            this.backgroundColors = this.backgroundColors + (colorString to colorName)
            return this
        }

        fun addPresetSymbol(symbol: String): Builder {
            this.presetSymbols = this.presetSymbols + symbol
            return this
        }

        fun build(): CellEditorDialog {
            return CellEditorDialog(context, backgroundColors, presetSymbols)
        }
    }

    init {
        setContentView(binding.root)

        // 设置Dialog属性
        val window = window
        window?.attributes = window?.attributes?.apply {
            width = (context.resources.displayMetrics.widthPixels * 0.6).toInt()
            height = (context.resources.displayMetrics.heightPixels * 0.4).toInt()
        }
        window?.setGravity(Gravity.CENTER)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        setupViews()
    }

    private fun setupViews() {
        // 设置颜色选择器
        setupColorPalette()

        // 设置预设符号内容
        setupPresetContent()

        // 设置编辑框监听
        setupEditTextListener()

    }

    private fun setupColorPalette() {
        binding.colorPalette.removeAllViews()

        backgroundColors.forEachIndexed { index, (colorString, colorName) ->
            val container = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    context.resources.getDimensionPixelSize(R.dimen.color_circle_size),
                    context.resources.getDimensionPixelSize(R.dimen.color_circle_size)
                ).apply {
                    marginEnd = context.resources.getDimensionPixelSize(R.dimen.color_circle_margin)
                }
            }

            // 内部颜色视图
            val colorView = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(2, 2, 2, 2) // 为边框留出空间
                }

                // 设置圆角背景
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 18f * context.resources.displayMetrics.density
                    setColor(parseColorSafely(colorString))
                }
            }

            container.addView(colorView)

            // 点击事件
            container.setOnClickListener {
                currentSelectedColor = colorString
                onColorSelectedListener?.invoke(colorString)
                updateColorSelection(container, binding.colorPalette)
            }

            binding.colorPalette.addView(container)
        }
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
                selectedView.foreground = ContextCompat.getDrawable(context, R.drawable.color_item_selected_border)
            }
        }
    }

    // 根据当前选中的颜色更新颜色选择器的选中状态
    fun updateColorSelectionForCurrentColor() {
        binding.colorPalette.let { palette ->
            for (i in 0 until backgroundColors.size) {
                val (colorString, _) = backgroundColors[i]
                if (colorString == currentSelectedColor) {
                    val colorView = palette.getChildAt(i)
                    if (colorView is View) {
                        updateColorSelection(colorView, palette)
                        break
                    }
                }
            }
        }
    }

    private fun setupPresetContent() {
        binding.presetContentContainer.removeAllViews()

        presetSymbols.forEach { symbol ->
            val textView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    context.resources.getDimensionPixelSize(R.dimen.button_height)
                ).apply {
                    marginEnd = context.resources.getDimensionPixelSize(R.dimen.spacing_small)
                }

                text = symbol
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.rounded_background)
                setPadding(
                    context.resources.getDimensionPixelSize(R.dimen.spacing_small),
                    0,
                    context.resources.getDimensionPixelSize(R.dimen.spacing_small),
                    0
                )

                setOnClickListener {
                    // 在编辑框末尾追加内容而不是覆盖
                    val currentText = binding.etCellContent.text?.toString() ?: ""
                    val selectionStart = binding.etCellContent.selectionStart.coerceAtLeast(0)

                    // 在光标位置插入符号
                    val newText = StringBuilder(currentText).apply {
                        insert(selectionStart, symbol)
                    }.toString()

                    binding.etCellContent.setText(newText)

                    // 将光标移动到插入符号之后的位置
                    val newCursorPosition = selectionStart + symbol.length
                    binding.etCellContent.setSelection(newCursorPosition)

                    // 触发符号选择监听
                    onSymbolSelectedListener?.invoke(symbol)

                    // 触发内容变化监听
                    onContentChangeListener?.invoke(newText)
                }
            }
            binding.presetContentContainer.addView(textView)
        }
    }

    private fun setupEditTextListener() {
        binding.etCellContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                onContentChangeListener?.invoke(s.toString())
            }
        })
    }

    fun setOnContentChangeListener(listener: (String) -> Unit) {
        this.onContentChangeListener = listener
    }

    fun setOnColorSelectedListener(listener: (String) -> Unit) {
        this.onColorSelectedListener = listener
    }

    fun setOnSymbolSelectedListener(listener: (String) -> Unit) {
        this.onSymbolSelectedListener = listener
    }

    fun setCellContent(content: String) {
        binding.etCellContent.setText(content)
        binding.etCellContent.setSelection(content.length)
    }

    fun getCellContent(): String {
        return binding.etCellContent.text?.toString() ?: ""
    }

    // 设置当前选中的颜色
    fun setCurrentSelectedColor(color: String) {
        this.currentSelectedColor = color
        // 更新UI选中状态
        updateColorSelectionForCurrentColor()
    }

    // 动态更新颜色列表
    fun updateBackgroundColorList(colors: List<Pair<String, String>>) {
        // 更新颜色列表
        this.backgroundColors = colors.toList()

//        // 检查当前选中的颜色是否在新列表中
//        if (currentSelectedColor.isNotEmpty()) {
//            val colorExists = colors.any { it.first == currentSelectedColor }
//            if (!colorExists) {
//                // 如果当前选中的颜色不在新列表中，清空选中状态或选择第一个颜色
//                currentSelectedColor = colors.firstOrNull()?.first ?: ""
//                onColorSelectedListener?.invoke(currentSelectedColor)
//            }
//        } else {
//            // 如果没有当前选中颜色，选择第一个颜色
//            currentSelectedColor = colors.firstOrNull()?.first ?: ""
//        }

        // 重新设置颜色选择器
        setupColorPalette()
    }

    // 动态更新符号列表
    fun updatePresetSymbols(symbols: List<String>) {
        // 更新符号列表
        this.presetSymbols = symbols.toList()

        // 重新设置预设符号内容
        setupPresetContent()
    }

    // 添加单个颜色
    fun addBackgroundColor(colorString: String, colorName: String) {
        val newColors = backgroundColors.toMutableList()
        newColors.add(colorString to colorName)
        updateBackgroundColorList(newColors)
    }

    // 添加单个符号
    fun addPresetSymbol(symbol: String) {
        val newSymbols = presetSymbols.toMutableList()
        newSymbols.add(symbol)
        updatePresetSymbols(newSymbols)
    }

    // 移除指定颜色
    fun removeBackgroundColor(colorString: String) {
        val newColors = backgroundColors.filterNot { it.first == colorString }
        updateBackgroundColorList(newColors)
    }

    // 移除指定符号
    fun removePresetSymbol(symbol: String) {
        val newSymbols = presetSymbols.filterNot { it == symbol }
        updatePresetSymbols(newSymbols)
    }

    // 获取当前颜色列表
    fun getBackgroundColors(): List<Pair<String, String>> {
        return backgroundColors.toList()
    }

    // 获取当前符号列表
    fun getPresetSymbols(): List<String> {
        return presetSymbols.toList()
    }

    // 清空所有颜色
    fun clearBackgroundColors() {
        updateBackgroundColorList(emptyList())
    }

    // 清空所有符号
    fun clearPresetSymbols() {
        updatePresetSymbols(emptyList())
    }
}