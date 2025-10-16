package com.xctech.excelpj.view

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.util.LruCache
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.xctech.excelpj.data.ExcelCell
import com.xctech.excelpj.data.ExcelInfo
import com.xctech.excelpj.data.ExcelMerge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

class ExcelTableView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "ExcelTableView"
        private const val MAX_CACHE_SIZE = 20 * 1024 * 1024 // 20MB 内存缓存大小
        private const val DEFAULT_CELL_WIDTH = 120f
        private const val DEFAULT_CELL_HEIGHT = 60f
        private const val DEFAULT_SCALE_MIN = 0.5f
        private const val DEFAULT_SCALE_MAX = 3.0f
        private const val DEFAULT_MAX_RETRY_COUNT = 3 // 默认最大重试次数
    }

    // 回调接口
    interface OnCellClickListener {
        fun onCellClick(row: Int, col: Int)
    }

    // 图片加载状态回调
    interface OnImageLoadListener {
        fun onImageLoadStarted(row: Int, col: Int)
        fun onImageLoadSuccess(row: Int, col: Int)
        fun onImageLoadFailed(row: Int, col: Int, error: Exception?)
    }

    // Sheet状态数据类
    data class SheetState(
        val scaleFactor: Float = 1f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f
    )

    // 配置类
    data class Config(
        val showEditedCellBorder: Boolean = false,
        val editedCellBorderColor: Int = Color.parseColor("#10B981"), // 绿色
        val imageScaleType: ScaleType = ScaleType.FIT_CENTER,
        val placeholderDrawableRes: Int? = null,
        val errorDrawableRes: Int? = null,
        val imageLoadingColor: Int = Color.parseColor("#E0E0E0"),
        val maxZoom: Float = DEFAULT_SCALE_MAX,
        val minZoom: Float = DEFAULT_SCALE_MIN,
        val imageCacheEnabled: Boolean = true,
        val imageCornerRadius: Float = 0f,
        val imageLoadingText: String = "Loading...",
        val imageErrorText: String = "Error",
        val maxRetryCount: Int = DEFAULT_MAX_RETRY_COUNT,
        val isFocus: Boolean = false
    )

    enum class ScaleType {
        FIT_CENTER, CENTER_CROP, CENTER_INSIDE
    }

    // 构造器模式的Builder类
    class Builder {
        private var showEditedCellBorder = false
        private var editedCellBorderColor = Color.parseColor("#10B981") // 绿色
        private var imageScaleType = ScaleType.FIT_CENTER
        private var placeholderDrawableRes: Int? = null
        private var errorDrawableRes: Int? = null
        private var imageLoadingColor = Color.parseColor("#E0E0E0")
        private var maxZoom = DEFAULT_SCALE_MAX
        private var minZoom = DEFAULT_SCALE_MIN
        private var imageCacheEnabled = true
        private var imageCornerRadius = 0f
        private var imageLoadingText = "Loading..."
        private var imageErrorText = "Error"
        private var maxRetryCount = DEFAULT_MAX_RETRY_COUNT
        private var isFocus = false

        fun showEditedCellBorder(show: Boolean) = apply { this.showEditedCellBorder = show }
        fun editedCellBorderColor(color: Int) = apply { this.editedCellBorderColor = color }
        fun imageScaleType(type: ScaleType) = apply { this.imageScaleType = type }
        fun placeholderDrawable(resId: Int) = apply { this.placeholderDrawableRes = resId }
        fun errorDrawable(resId: Int) = apply { this.errorDrawableRes = resId }
        fun imageLoadingColor(color: Int) = apply { this.imageLoadingColor = color }
        fun maxZoom(zoom: Float) = apply { this.maxZoom = zoom }
        fun minZoom(zoom: Float) = apply { this.minZoom = zoom }
        fun imageCacheEnabled(enabled: Boolean) = apply { this.imageCacheEnabled = enabled }
        fun imageCornerRadius(radius: Float) = apply { this.imageCornerRadius = radius }
        fun imageLoadingText(text: String) = apply { this.imageLoadingText = text }
        fun imageErrorText(text: String) = apply { this.imageErrorText = text }
        fun maxRetryCount(count: Int) = apply { this.maxRetryCount = count }
        fun isFocus(focus: Boolean) = apply { this.isFocus = focus }

        fun build(): Config {
            return Config(
                showEditedCellBorder,
                editedCellBorderColor,
                imageScaleType,
                placeholderDrawableRes,
                errorDrawableRes,
                imageLoadingColor,
                maxZoom,
                minZoom,
                imageCacheEnabled,
                imageCornerRadius,
                imageLoadingText,
                imageErrorText,
                maxRetryCount,
                isFocus
            )
        }
    }

    private var cellClickListener: OnCellClickListener? = null
    private var imageLoadListener: OnImageLoadListener? = null
    private var excelInfo: ExcelInfo? = null
    private var scaleFactor = 1f
    private var formId: Int = -1 // 用于标识不同的表单
    // 图片缓存
    private val imageCache = LruCache<String, Bitmap>(MAX_CACHE_SIZE)
    private val imageLoadingStates = ConcurrentHashMap<String, Boolean>()
    private val retryCountMap = ConcurrentHashMap<String, Int>() // 重试次数跟踪
    private val mainHandler = Handler(Looper.getMainLooper())

    // 图片加载队列管理
    private val visibleCells = HashSet<String>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // 自定义背景色映射
    private val customBackgroundColors = mutableMapOf<String, Int>()

    // 编辑过的单元格记录
    private val editedCells = mutableMapOf<String, ExcelCell>()

    // Sheet状态保存（用于保存每个sheet的缩放和滚动位置 使用formId + sheetName作为key
    private val sheetStates = mutableMapOf<String, SheetState>()

    // 配置
    private var config = Config()

    // 绘制相关
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
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    // 用于图片圆角的Path和矩形
    private val roundRectPath = Path()
    private val rectF = RectF()

    // 尺寸
    private var cellWidth = DEFAULT_CELL_WIDTH
    private var cellHeight = DEFAULT_CELL_HEIGHT
    private var offsetX = 0f
    private var offsetY = 0f

    // 选中状态
    private var selectedRow = -1
    private var selectedCol = -1

    // 视口跟踪
    private var viewportLeft = 0
    private var viewportTop = 0
    private var viewportRight = 0
    private var viewportBottom = 0
    private var isScrolling = false

    //记录当前滚动位置放置数据更新后位置变化了
    private var scrollStartX = 0
    private var scrollStartY = 0
    private val scrollEndRunnable = Runnable {
        isScrolling = false
        updateVisibleCells()
        invalidate()
    }

    // 手势检测
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    // 单元格类型颜色
    private val cellTypeColors = mapOf(
        1 to Color.WHITE,           // 文本
        2 to Color.parseColor("#E3F2FD"), // 平均值
        3 to Color.parseColor("#F3E5F5"), // 选项
        4 to Color.parseColor("#E8F5E9"), // 范围
        5 to Color.parseColor("#FFF3E0"), // 图片
        6 to Color.parseColor("#E1F5FE"), // 签名
        7 to Color.parseColor("#F5F5F5"), // 置灰
        8 to Color.parseColor("#FFFDE7"),  // 搜索项
        9 to Color.parseColor("#FFF3E0")  // 只读图片
    )

    init {
        // 初始化时启用硬件加速
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    // 应用配置
    fun applyConfig(config: Config) {
        this.config = config
        scaleFactor = 1f
        invalidate()
    }

    fun setOnCellClickListener(listener: OnCellClickListener) {
        this.cellClickListener = listener
    }

    fun setOnImageLoadListener(listener: OnImageLoadListener) {
        this.imageLoadListener = listener
    }

    // 设置表单ID，用于区分不同的表单状态
    fun setFormId(id: Int) {
        this.formId = id
    }


    // 生成Sheet状态的key
    private fun generateSheetKey(info: ExcelInfo): String {
        // 同时使用formId、sheetIndex和sheetName，确保唯一性
        return "${formId}_${info.sheetIndex}_${info.sheetName}"
    }

    // 为了向后兼容的重载方法
    private fun generateSheetKey(sheetName: String, sheetIndex: Int = -1): String {
        return if (sheetIndex >= 0) {
            "${formId}_${sheetIndex}_${sheetName}"
        } else {
            "${formId}_${sheetName}"  // 旧格式，仅用于兼容
        }
    }

    // 设置Excel信息并保存当前状态 preserveViewState是否重置视图状态
    fun setExcelInfo(info: ExcelInfo, saveCurrentState: Boolean = true, preserveViewState: Boolean = false) {
        // 先保存当前状态
        if (saveCurrentState && excelInfo != null && formId != -1) {
            saveCurrentSheetState()
        }

        // 更新excelInfo
        this.excelInfo = info

        // 视图状态处理
        if (preserveViewState) {
            // 保持当前状态不变
            Log.d("ExcelTableView", "Preserving current view state for sheet[${info.sheetIndex}]: ${info.sheetName}")
        } else {
            // 尝试恢复该sheet自己的状态
            val key = generateSheetKey(info)
            val savedState = sheetStates[key]

            // 强制重置：永远使用保存的状态或默认值
            if (savedState != null) {
                // 使用保存的状态
                Log.d("ExcelTableView", "Applying saved state for sheet[${info.sheetIndex}]: ${info.sheetName} " +
                        "scale: ${savedState.scaleFactor}")
                scaleFactor = savedState.scaleFactor
                offsetX = savedState.offsetX
                offsetY = savedState.offsetY
            } else {
                // 没有保存状态，使用默认值
                Log.d("ExcelTableView", "No saved state for sheet[${info.sheetIndex}]: ${info.sheetName}, " +
                        "resetting to defaults")
                scaleFactor = 1f
                offsetX = 0f
                offsetY = 0f
            }
        }

        // 清除状态
        selectedRow = -1
        selectedCol = -1
        clearImageCache()

        // 更新视图
        invalidate()
    }

    fun dumpSheetStates() {
        Log.d("ExcelTableView", "---- Current Sheet States ----")
        sheetStates.forEach { (key, state) ->
            Log.d("ExcelTableView", "Sheet: $key - scale: ${state.scaleFactor}, " +
                    "offsetX: ${state.offsetX}, offsetY: ${state.offsetY}")
        }
        Log.d("ExcelTableView", "----------------------------")
    }
    // 保存当前sheet状态
    fun saveCurrentSheetState() {
        excelInfo?.let { info ->
            if (formId != -1) {
                val key = generateSheetKey(info)
                val state = SheetState(scaleFactor, offsetX, offsetY)
                sheetStates[key] = state

                Log.d("ExcelTableView", "Saved state for sheet[${info.sheetIndex}]: ${info.sheetName} - " +
                        "scale: $scaleFactor, offsetX: $offsetX, offsetY: $offsetY")

                // 调试输出
                dumpSheetStates()
            }
        }
    }

    // 获取保存的sheet状态
    fun getSavedSheetState(sheetIndex: Int): SheetState? {
        excelInfo?.let { info ->
            val key = generateSheetKey(info)
            return sheetStates[key]
        }
        return null

    }

    // 清空所有保存的sheet状态
    fun clearAllSheetStates() {
        sheetStates.clear()
        Log.d("ExcelTableView", "Cleared all sheet states")
    }

    // 在formId变化时清除所有相关状态
    fun clearSheetStatesForForm(formId: Int) {
        val keysToRemove = sheetStates.keys.filter { it.startsWith("${formId}_") }
        keysToRemove.forEach { key ->
            sheetStates.remove(key)
        }
        Log.d("ExcelTableView", "Cleared all states for form $formId")
    }
    fun setScaleFactor(scale: Float) {
        scaleFactor = scale.coerceIn(config.minZoom, config.maxZoom)
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


    // 清除图片缓存
    fun clearImageCache() {
        imageCache.evictAll()
        imageLoadingStates.clear()
        retryCountMap.clear() // 清除重试计数
        invalidate()
    }

    // 更新单元格内容
    fun updateCell(row: Int, col: Int) {
        // 如果是图片单元格，清除缓存
        excelInfo?.let { info ->
            val cell = info.tableData.getOrNull(row)?.getOrNull(col)
            if (cell?.cellType == 5 || cell?.cellType == 9) {
                clearImageForCell(row, col)
            }
        }

        // 触发重绘
        invalidate()
    }

    // 清除特定单元格的图片缓存
    private fun clearImageForCell(row: Int, col: Int) {
        val cacheKey = "$row,$col"
        imageCache.remove(cacheKey)
        imageLoadingStates.remove(cacheKey)
    }

    // 加载单元格图片
    private fun loadCellImage(row: Int, col: Int, cell: ExcelCell) {
        val cacheKey = "$row,$col"

        // 检查重试次数
        val retryCount = retryCountMap.getOrDefault(cacheKey, 0)
        if (retryCount >= config.maxRetryCount) {
            // 已达到最大重试次数，不再尝试加载
            return
        }

        // 如果已经加载中或者没有值，不重复加载
        if (imageLoadingStates[cacheKey] == true || cell.value.isEmpty()) {
            return
        }

        // 如果已经有缓存，直接使用
        val cachedBitmap = imageCache.get(cacheKey)
        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            return
        }

        // 标记为加载中
        imageLoadingStates[cacheKey] = true
        imageLoadListener?.onImageLoadStarted(row, col)

        // 使用弱引用持有Context，避免内存泄漏
        val contextRef = WeakReference(context)

        // 使用协程进行异步加载
        coroutineScope.launch {
            try {
                val context = contextRef.get() ?: return@launch

                // 判断图片地址类型
                when {
                    cell.value.startsWith("http://") || cell.value.startsWith("https://") -> {
                        // 网络图片
                        loadNetworkImage(context, cacheKey, cell.value, row, col)
                    }
                    cell.value.startsWith("file://") || cell.value.startsWith("/") -> {
                        // 本地文件
                        loadLocalImage(context, cacheKey, cell.value, row, col)
                    }
                    cell.value.startsWith("content://") -> {
                        // Content URI
                        loadContentUriImage(context, cacheKey, cell.value, row, col)
                    }
                    else -> {
                        // 尝试作为资源ID
                        loadResourceImage(context, cacheKey, cell.value, row, col)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    // 增加重试计数
                    retryCountMap[cacheKey] = retryCount + 1

                    imageLoadingStates[cacheKey] = false
                    imageLoadListener?.onImageLoadFailed(row, col, e)
                    invalidateCell(row, col)
                }
            }
        }
    }

    // 加载网络图片
    private suspend fun loadNetworkImage(context: Context, cacheKey: String, url: String, row: Int, col: Int) {
        withContext(Dispatchers.IO) {
            try {
                Glide.with(context)
                    .asBitmap()
                    .load(url)
                    .placeholder(config.placeholderDrawableRes?.let { ContextCompat.getDrawable(context, it) })
                    .error(config.errorDrawableRes?.let { ContextCompat.getDrawable(context, it) })
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            imageCache.put(cacheKey, resource)
                            imageLoadingStates[cacheKey] = false
                            // 成功加载时重置重试计数
                            retryCountMap.remove(cacheKey)

                            imageLoadListener?.onImageLoadSuccess(row, col)
                            invalidateCell(row, col)
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            // Do nothing
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            // 增加重试计数
                            val currentRetryCount = retryCountMap.getOrDefault(cacheKey, 0)
                            retryCountMap[cacheKey] = currentRetryCount + 1

                            imageLoadingStates[cacheKey] = false
                            imageLoadListener?.onImageLoadFailed(row, col, null)
                            invalidateCell(row, col)
                        }
                    })
            } catch (e: Exception) {
                mainHandler.post {
                    // 增加重试计数
                    val currentRetryCount = retryCountMap.getOrDefault(cacheKey, 0)
                    retryCountMap[cacheKey] = currentRetryCount + 1

                    imageLoadingStates[cacheKey] = false
                    imageLoadListener?.onImageLoadFailed(row, col, e)
                    invalidateCell(row, col)
                }
            }
        }
    }
    // 加载本地文件图片
    private suspend fun loadLocalImage(context: Context, cacheKey: String, path: String, row: Int, col: Int) {
        withContext(Dispatchers.IO) {
            try {
                val file = if (path.startsWith("file://")) {
                    File(path.substring(7))
                } else {
                    File(path)
                }

                if (!file.exists()) {
                    throw IllegalArgumentException("File doesn't exist: $path")
                }

                Glide.with(context)
                    .asBitmap()
                    .load(file)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            imageCache.put(cacheKey, resource)
                            imageLoadingStates[cacheKey] = false
                            // 成功加载时重置重试计数
                            retryCountMap.remove(cacheKey)

                            imageLoadListener?.onImageLoadSuccess(row, col)
                            invalidateCell(row, col)
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            // Do nothing
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            // 增加重试计数
                            val currentRetryCount = retryCountMap.getOrDefault(cacheKey, 0)
                            retryCountMap[cacheKey] = currentRetryCount + 1

                            imageLoadingStates[cacheKey] = false
                            imageLoadListener?.onImageLoadFailed(row, col, null)
                            invalidateCell(row, col)
                        }
                    })
            } catch (e: Exception) {
                mainHandler.post {
                    // 增加重试计数
                    val currentRetryCount = retryCountMap.getOrDefault(cacheKey, 0)
                    retryCountMap[cacheKey] = currentRetryCount + 1

                    imageLoadingStates[cacheKey] = false
                    imageLoadListener?.onImageLoadFailed(row, col, e)
                    invalidateCell(row, col)
                }
            }
        }
    }
    // 加载Content URI图片
    private suspend fun loadContentUriImage(context: Context, cacheKey: String, uri: String, row: Int, col: Int) {
        withContext(Dispatchers.IO) {
            try {
                val contentUri = Uri.parse(uri)

                Glide.with(context)
                    .asBitmap()
                    .load(contentUri)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            imageCache.put(cacheKey, resource)
                            imageLoadingStates[cacheKey] = false
                            // 成功加载时重置重试计数
                            retryCountMap.remove(cacheKey)

                            imageLoadListener?.onImageLoadSuccess(row, col)
                            invalidateCell(row, col)
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            // Do nothing
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            // 增加重试计数
                            val currentRetryCount = retryCountMap.getOrDefault(cacheKey, 0)
                            retryCountMap[cacheKey] = currentRetryCount + 1

                            imageLoadingStates[cacheKey] = false
                            imageLoadListener?.onImageLoadFailed(row, col, null)
                            invalidateCell(row, col)
                        }
                    })
            } catch (e: Exception) {
                mainHandler.post {
                    // 增加重试计数
                    val currentRetryCount = retryCountMap.getOrDefault(cacheKey, 0)
                    retryCountMap[cacheKey] = currentRetryCount + 1

                    imageLoadingStates[cacheKey] = false
                    imageLoadListener?.onImageLoadFailed(row, col, e)
                    invalidateCell(row, col)
                }
            }
        }
    }

    // 尝试加载资源图片
    private suspend fun loadResourceImage(context: Context, cacheKey: String, resIdStr: String, row: Int, col: Int) {
        withContext(Dispatchers.IO) {
            try {
                // 尝试将字符串转换为资源ID
                val resId = try {
                    val resources = context.resources
                    val packageName = context.packageName
                    resources.getIdentifier(resIdStr, "drawable", packageName)
                } catch (e: Exception) {
                    0
                }

                if (resId != 0) {
                    Glide.with(context)
                        .asBitmap()
                        .load(resId)
                        .into(object : CustomTarget<Bitmap>() {
                            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                imageCache.put(cacheKey, resource)
                                imageLoadingStates[cacheKey] = false
                                // 成功加载时重置重试计数
                                retryCountMap.remove(cacheKey)

                                imageLoadListener?.onImageLoadSuccess(row, col)
                                invalidateCell(row, col)
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {
                                // Do nothing
                            }

                            override fun onLoadFailed(errorDrawable: Drawable?) {
                                // 增加重试计数
                                val currentRetryCount = retryCountMap.getOrDefault(cacheKey, 0)
                                retryCountMap[cacheKey] = currentRetryCount + 1

                                imageLoadingStates[cacheKey] = false
                                imageLoadListener?.onImageLoadFailed(row, col, null)
                                invalidateCell(row, col)
                            }
                        })
                } else {
                    throw IllegalArgumentException("Invalid image path or resource ID: $resIdStr")
                }
            } catch (e: Exception) {
                mainHandler.post {
                    // 增加重试计数
                    val currentRetryCount = retryCountMap.getOrDefault(cacheKey, 0)
                    retryCountMap[cacheKey] = currentRetryCount + 1

                    imageLoadingStates[cacheKey] = false
                    imageLoadListener?.onImageLoadFailed(row, col, e)
                    invalidateCell(row, col)
                }
            }
        }
    }

    // 重绘特定单元格
    private fun invalidateCell(row: Int, col: Int) {
        mainHandler.post {
            excelInfo?.let { info ->
                val cell = info.tableData.getOrNull(row)?.getOrNull(col) ?: return@post
                val rect = getCellRect(row, col, cell, info)

                // 扩大一点无效区域，确保完全覆盖
                val left = (rect.left + offsetX) * scaleFactor
                val top = (rect.top + offsetY) * scaleFactor
                val right = (rect.right + offsetX) * scaleFactor
                val bottom = (rect.bottom + offsetY) * scaleFactor

                invalidate(left.toInt() - 2, top.toInt() - 2, right.toInt() + 2, bottom.toInt() + 2)
            }
        }
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
                backgroundColor = cellTypeColors[cell.cellType]
            }
        }

        // 如果还没有背景色，使用单元格类型颜色
        if (backgroundColor == null) {
            backgroundColor = cellTypeColors[cell.cellType] ?: Color.WHITE
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
        when (cell.cellType) {
            1, 2, 3, 4, 8 -> drawTextCell(canvas, cellRect, cell)
            5, 9 -> drawImageCell(canvas, cellRect, cell, row, col)
            6 -> drawSignatureCell(canvas, cellRect, cell)
            7 -> drawDisabledCell(canvas, cellRect)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        excelInfo?.let { info ->
            canvas.save()
            canvas.scale(scaleFactor, scaleFactor)
            canvas.translate(offsetX, offsetY)

            // 计算可见范围
            updateViewport()

            // 绘制合并单元格背景
            drawMergedCells(canvas, info)

            // 绘制所有单元格
            drawCells(canvas, info)

            // 绘制选中框
            drawSelection(canvas)

            canvas.restore()

            // 如果不在滚动中，更新可见单元格并加载可见的图片
            if (!isScrolling) {
                updateVisibleCells()
            }
        }
    }

    // 更新视口区域
    private fun updateViewport() {
        // 计算当前视口范围（转换为单元格索引）
        viewportLeft = max(0, (-offsetX / cellWidth).toInt() - 1)
        viewportTop = max(0, (-offsetY / cellHeight).toInt() - 1)
        viewportRight = min(excelInfo?.maxCols ?: 0,
            ((width / scaleFactor - offsetX) / cellWidth).toInt() + 2)
        viewportBottom = min(excelInfo?.rowCount ?: 0,
            ((height / scaleFactor - offsetY) / cellHeight).toInt() + 2)
    }

    // 更新可见单元格并加载图片
    private fun updateVisibleCells() {
        excelInfo?.let { info ->
            val newVisibleCells = HashSet<String>()

            for (rowIndex in viewportTop until viewportBottom) {
                val row = info.tableData.getOrNull(rowIndex) ?: continue
                for (colIndex in viewportLeft until viewportRight) {
                    val cell = row.getOrNull(colIndex) ?: continue
                    if (!shouldSkipCell(rowIndex, colIndex, cell, info)) {
                        val key = "$rowIndex,$colIndex"
                        newVisibleCells.add(key)

                        // 如果是图片单元格并且之前不在可见区域，加载图片
                        if ((cell.cellType == 5 || cell.cellType == 9) &&
                            !visibleCells.contains(key) &&
                            cell.value.isNotEmpty()) {

                            // 检查重试次数
                            val retryCount = retryCountMap.getOrDefault(key, 0)
                            if (retryCount < config.maxRetryCount) {
                                loadCellImage(rowIndex, colIndex, cell)
                            }
                        }
                    }
                }
            }

            // 更新可见单元格集合
            visibleCells.clear()
            visibleCells.addAll(newVisibleCells)
        }
    }
    private fun drawMergedCells(canvas: Canvas, info: ExcelInfo) {
        info.mergedCells.forEach { merge ->
            val left = merge.minCol * cellWidth
            val top = merge.minRow * cellHeight
            val right = (merge.maxCol + 1) * cellWidth
            val bottom = (merge.maxRow + 1) * cellHeight

            // 检查是否在视口内
            if (right >= viewportLeft * cellWidth &&
                left <= viewportRight * cellWidth &&
                bottom >= viewportTop * cellHeight &&
                top <= viewportBottom * cellHeight) {
                canvas.drawRect(left, top, right, bottom, mergedCellPaint)
                canvas.drawRect(left, top, right, bottom, gridPaint)
            }
        }
    }

    private fun drawCells(canvas: Canvas, info: ExcelInfo) {
        // 使用视口范围绘制可见单元格
        for (rowIndex in viewportTop until viewportBottom) {
            val row = info.tableData.getOrNull(rowIndex) ?: continue
            for (colIndex in viewportLeft until viewportRight) {
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
                2 -> "AVG: ${cell.value}"
                3 -> cell.option.firstOrNull()?.toString() ?: cell.value
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

    // 绘制图片单元格
    private fun drawImageCell(canvas: Canvas, rect: RectF, cell: ExcelCell, row: Int, col: Int) {
        val cacheKey = "$row,$col"
        val cachedBitmap = imageCache.get(cacheKey)

        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            // 绘制缓存的图片
            drawBitmapInCell(canvas, rect, cachedBitmap)
        } else if (imageLoadingStates[cacheKey] == true) {
            // 绘制加载中状态
            drawImageLoadingState(canvas, rect)
        } else if (cell.value.isEmpty()) {
            // 空值，绘制图标占位符
            drawImagePlaceholder(canvas, rect)
        } else {
            // 检查重试次数
            val retryCount = retryCountMap.getOrDefault(cacheKey, 0)
            if (retryCount >= config.maxRetryCount) {
                // 已达到最大重试次数，绘制错误状态
                drawImageErrorState(canvas, rect, retryCount)
            } else {
                // 未加载，绘制占位符并触发加载
                drawImagePlaceholder(canvas, rect)
                if (!isScrolling) {
                    loadCellImage(row, col, cell)
                }
            }
        }
    }


    // 绘制图片错误状态
    private fun drawImageErrorState(canvas: Canvas, rect: RectF, retryCount: Int) {
        // 绘制错误背景
        cellPaint.color = Color.parseColor("#FFEBEE") // 淡红色背景
        canvas.drawRect(rect, cellPaint)

        // 绘制错误文本
        textPaint.color = Color.RED
        textPaint.textSize = 12f * resources.displayMetrics.density

        canvas.drawText(
            "${config.imageErrorText} (${retryCount}/${config.maxRetryCount})",
            rect.centerX(),
            rect.centerY(),
            textPaint
        )

        // 恢复文本画笔设置
        textPaint.color = Color.BLACK
        textPaint.textSize = 14f * resources.displayMetrics.density
    }



    // 绘制位图到单元格
    private fun drawBitmapInCell(canvas: Canvas, rect: RectF, bitmap: Bitmap) {
        // 计算图片绘制区域，留出边距
        val padding = min(rect.width(), rect.height()) * 0.05f
        val imageRect = RectF(
            rect.left + padding,
            rect.top + padding,
            rect.right - padding,
            rect.bottom - padding
        )

        // 根据缩放类型调整图片绘制区域
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()
        val targetRect = when (config.imageScaleType) {
            ScaleType.FIT_CENTER -> {
                val scale = min(
                    imageRect.width() / bitmapWidth,
                    imageRect.height() / bitmapHeight
                )
                val scaledWidth = bitmapWidth * scale
                val scaledHeight = bitmapHeight * scale
                RectF(
                    imageRect.centerX() - scaledWidth / 2,
                    imageRect.centerY() - scaledHeight / 2,
                    imageRect.centerX() + scaledWidth / 2,
                    imageRect.centerY() + scaledHeight / 2
                )
            }
            ScaleType.CENTER_CROP -> {
                val scale = max(
                    imageRect.width() / bitmapWidth,
                    imageRect.height() / bitmapHeight
                )
                val scaledWidth = bitmapWidth * scale
                val scaledHeight = bitmapHeight * scale
                RectF(
                    imageRect.centerX() - scaledWidth / 2,
                    imageRect.centerY() - scaledHeight / 2,
                    imageRect.centerX() + scaledWidth / 2,
                    imageRect.centerY() + scaledHeight / 2
                )
            }
            ScaleType.CENTER_INSIDE -> {
                if (bitmapWidth <= imageRect.width() && bitmapHeight <= imageRect.height()) {
                    // 如果图片比目标区域小，直接居中显示
                    RectF(
                        imageRect.centerX() - bitmapWidth / 2,
                        imageRect.centerY() - bitmapHeight / 2,
                        imageRect.centerX() + bitmapWidth / 2,
                        imageRect.centerY() + bitmapHeight / 2
                    )
                } else {
                    // 否则缩放显示
                    val scale = min(
                        imageRect.width() / bitmapWidth,
                        imageRect.height() / bitmapHeight
                    )
                    val scaledWidth = bitmapWidth * scale
                    val scaledHeight = bitmapHeight * scale
                    RectF(
                        imageRect.centerX() - scaledWidth / 2,
                        imageRect.centerY() - scaledHeight / 2,
                        imageRect.centerX() + scaledWidth / 2,
                        imageRect.centerY() + scaledHeight / 2
                    )
                }
            }
        }

        // 绘制图片（支持圆角）
        if (config.imageCornerRadius > 0) {
            // 设置圆角路径
            roundRectPath.reset()
            rectF.set(targetRect)
            roundRectPath.addRoundRect(rectF, config.imageCornerRadius, config.imageCornerRadius, Path.Direction.CW)

            // 保存画布状态并裁剪
            canvas.save()
            canvas.clipPath(roundRectPath)
            canvas.drawBitmap(bitmap, null, targetRect, imagePaint)
            canvas.restore()
        } else {
            // 直接绘制图片
            canvas.drawBitmap(bitmap, null, targetRect, imagePaint)
        }
    }

    // 绘制图片加载中状态
    private fun drawImageLoadingState(canvas: Canvas, rect: RectF) {
        // 绘制加载中背景
        cellPaint.color = config.imageLoadingColor
        canvas.drawRect(rect, cellPaint)

        // 绘制加载中文本
        textPaint.textSize = 12f * resources.displayMetrics.density
        canvas.drawText(
            config.imageLoadingText,
            rect.centerX(),
            rect.centerY(),
            textPaint
        )
        textPaint.textSize = 14f * resources.displayMetrics.density
    }

    // 绘制图片占位符
    private fun drawImagePlaceholder(canvas: Canvas, rect: RectF) {
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

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isScrolling = true
                removeCallbacks(scrollEndRunnable)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 延迟标记滚动结束，避免频繁更新
                postDelayed(scrollEndRunnable, 100)
            }
        }

        return true
    }

    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(config.minZoom, config.maxZoom)
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
                                selectedRow = mainCellPosition.first
                                selectedCol = mainCellPosition.second
                                cellClickListener?.onCellClick(mainCellPosition.first, mainCellPosition.second)
                            }
                        } else {
                            // 正常单元格或主单元格直接触发点击
                            selectedRow = row
                            selectedCol = col
                            cellClickListener?.onCellClick(row, col)
                        }
                        invalidate()
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
        updateViewport()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        // 释放资源
        clearImageCache()
        removeCallbacks(scrollEndRunnable)
    }

    // 设置单元格尺寸
    fun setCellSize(width: Float, height: Float) {
        cellWidth = width
        cellHeight = height
        constrainOffsets()
        invalidate()
    }

    // 获取当前缩放因子
    fun getCurrentScaleFactor(): Float = scaleFactor

    // 获取当前X偏移
    fun getCurrentOffsetX(): Float = offsetX

    // 获取当前Y偏移
    fun getCurrentOffsetY(): Float = offsetY

    //获取是否编辑时请求焦点
    fun getRequestFocusOnEdit(): Boolean = config.isFocus

    //获取表单ID
    fun getFormId(): Int = formId

    // 应用视图状态
    fun applyViewState(scaleFactor: Float, offsetX: Float, offsetY: Float) {
        this.scaleFactor = scaleFactor
        this.offsetX = offsetX
        this.offsetY = offsetY
        constrainOffsets()
        invalidate()
    }

    // 获取当前的ExcelInfo
    fun getExcelInfo(): ExcelInfo? {
        return excelInfo
    }

    // 添加一个临时保存当前视图状态的方法
    fun saveCurrentViewState(): ViewState {
        return ViewState(
            scaleFactor = scaleFactor,
            offsetX = offsetX,
            offsetY = offsetY
        )
    }

    // 数据结构用于保存视图状态
    data class ViewState(
        val scaleFactor: Float,
        val offsetX: Float,
        val offsetY: Float
    )

    fun applyDefaultState() {
        scaleFactor = 1f
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }


}