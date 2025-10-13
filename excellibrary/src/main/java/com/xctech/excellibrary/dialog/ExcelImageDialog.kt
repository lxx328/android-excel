package com.xctech.excelpj.dialog

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.FloatRange
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoView
import com.xctech.excellibrary.R
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * 高可用的Excel图片查看对话框组件
 *
 * 支持:
 * - 图片加载与缩放
 * - 图片保存与分享
 * - 错误处理与重试
 * - 自定义UI
 * - 内存优化
 */
class ExcelImageDialog private constructor(
    private val context: Context,
    @StyleRes themeResId: Int
) {
    private var dialog: AlertDialog? = null
    private var photoView: PhotoView? = null
    private var progressBar: ProgressBar? = null
    private var errorView: View? = null
    private var titleTextView: TextView? = null
    private var saveButton: ImageButton? = null
    private var shareButton: ImageButton? = null
    private var closeButton: ImageButton? = null
    private var bottomBar: LinearLayout? = null

    private var imageUri: Uri? = null
    private var imagePath: String? = null
    private var imageResId: Int? = null
    private var title: String? = null
    private var loadingText: String = "加载中..."
    private var errorText: String = "加载失败，点击重试"
    private var enableSave: Boolean = true
    private var enableShare: Boolean = true
    private var backgroundColor: Int = Color.WHITE
    private var cancelable: Boolean = true

    private var photoContainer: FrameLayout? = null


    // 尺寸相关属性
    private var widthRatio: Float = 0.5f  // 默认宽度为屏幕的50%
    private var heightRatio: Float = 0.67f // 默认高度为屏幕的67%（约2/3）
    private var customWidth: Int? = null   // 自定义宽度（像素）
    private var customHeight: Int? = null  // 自定义高度（像素）

    private val executor: Executor = Executors.newSingleThreadExecutor()

    init {
        val builder = AlertDialog.Builder(context, themeResId)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_excel_image, null)

        photoView = view.findViewById(R.id.photo_view)
        progressBar = view.findViewById(R.id.progress_bar)
        errorView = view.findViewById(R.id.error_view)
        titleTextView = view.findViewById(R.id.title_text)
        saveButton = view.findViewById(R.id.save_button)
        shareButton = view.findViewById(R.id.share_button)
        closeButton = view.findViewById(R.id.close_button)
        bottomBar = view.findViewById(R.id.bottom_bar)
        photoContainer = view.findViewById(R.id.photo_container)  // 获取容器引用

        photoContainer?.visibility = View.VISIBLE


        builder.setView(view)
        dialog = builder.create()
        // 设置透明背景以便可以自定义对话框形状
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setupListeners()
    }

    private fun setupListeners() {
        closeButton?.setOnClickListener {
            dismiss()
        }

        saveButton?.setOnClickListener {
            saveImageToGallery()
        }

        shareButton?.setOnClickListener {
            shareImage()
        }

        errorView?.setOnClickListener {
            loadImage()
        }
    }

    /**
     * 加载并显示图片
     */
    private fun loadImage() {
        Log.d(TAG, "loadImage: URI=$imageUri, Path=$imagePath, ResId=$imageResId")

        progressBar?.visibility = View.VISIBLE
        errorView?.visibility = View.GONE
        photoView?.visibility = View.VISIBLE

        try {
            val requestManager = Glide.with(context)
            val requestBuilder = when {
                imageUri != null -> {
                    Log.d(TAG, "Loading from URI: $imageUri")
                    requestManager.load(imageUri)
                }
                imagePath != null -> {
                    Log.d(TAG, "Loading from Path: $imagePath")
                    requestManager.load(imagePath)
                }
                imageResId != null -> {
                    Log.d(TAG, "Loading from Resource: $imageResId")
                    requestManager.load(imageResId)
                }
                else -> {
                    Log.e(TAG, "No image source provided")
                    showError()
                    return
                }
            }

            requestBuilder
                .placeholder(R.drawable.ic_image_placeholder) // 添加一个占位图
                .error(R.drawable.ic_image_error) // 添加错误图
                .timeout(15000) // 增加超时时间
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.e(TAG, "Image load failed", e)
                        showError()
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.d(TAG, "onResourceReady: w=${resource.intrinsicWidth}, h=${resource.intrinsicHeight}")
                        progressBar?.visibility = View.GONE

                        // 设置最佳展示模式
                        photoView?.apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageDrawable(resource)

                            // 初始时稍微缩放以确保填充更多视图空间
                            val containerWidth = (photoView?.parent as? View)?.width ?: 0
                            val containerHeight = (photoView?.parent as? View)?.height ?: 0

                            if (containerWidth > 0 && containerHeight > 0 &&
                                resource.intrinsicWidth > 0 && resource.intrinsicHeight > 0) {
                                // 计算最佳缩放比例
                                val widthRatio = containerWidth.toFloat() / resource.intrinsicWidth
                                val heightRatio = containerHeight.toFloat() / resource.intrinsicHeight
                                val initialScale = Math.min(widthRatio, heightRatio) * 0.9f // 留一点边距

                                // 设置初始缩放
                                // 设置非常大的最大缩放倍数和非常小的最小缩放倍数，相当于不限制
                                maximumScale = 20f  // 非常大的值，允许放大到20倍
                                minimumScale = 0.1f // 非常小的值，允许缩小到原来的1/10

                                // 可选：设置中等缩放级别，用于双击时的缩放效果
                                mediumScale = initialScale
                                scale = initialScale
                            }
                        }
                        return true
                    }
                })
                .into(photoView ?: return)
        } catch (e: Exception) {
            Log.e(TAG, "Exception in loadImage", e)
            showError()
        }
    }

    /**
     * 显示加载错误UI
     */
    private fun showError() {
        progressBar?.visibility = View.GONE
        errorView?.visibility = View.VISIBLE
        val errorTextView = errorView?.findViewById<TextView>(R.id.error_text)
        errorTextView?.text = errorText
        Log.e(TAG, "Showing error: $errorText")
    }

    /**
     * 保存图片到相册
     */
    private fun saveImageToGallery() {
        val drawable = photoView?.drawable
        if (drawable == null) {
            showErrorMessage("没有可保存的图片")
            return
        }

        executor.execute {
            try {
                // 获取当前显示的图片
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    ?: createBitmapFromDrawable(drawable)

                if (bitmap != null) {
                    // 创建临时文件
                    val filename = "Excel_Image_${UUID.randomUUID()}.jpg"
                    val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    val outputFile = File(outputDir, filename)

                    // 保存bitmap到文件
                    FileOutputStream(outputFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }

                    // 添加到媒体库
                    MediaStore.Images.Media.insertImage(
                        context.contentResolver,
                        outputFile.absolutePath,
                        filename,
                        "Excel image"
                    )

                    // 在UI线程显示成功消息
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "图片已保存到相册", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    showErrorMessage("无法保存图片")
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存图片失败: ${e.message}", e)
                showErrorMessage("保存图片失败: ${e.message}")
            }
        }
    }

    /**
     * 从Drawable创建Bitmap
     */
    private fun createBitmapFromDrawable(drawable: Drawable?): Bitmap? {
        drawable ?: return null

        try {
            val bitmap = Bitmap.createBitmap(
                Math.max(drawable.intrinsicWidth, 1),
                Math.max(drawable.intrinsicHeight, 1),
                Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)

            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error creating bitmap from drawable", e)
            return null
        }
    }

    /**
     * 分享图片
     */
    private fun shareImage() {
        val drawable = photoView?.drawable
        if (drawable == null) {
            showErrorMessage("没有可分享的图片")
            return
        }

        executor.execute {
            try {
                // 获取当前显示的图片
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    ?: createBitmapFromDrawable(drawable)

                if (bitmap != null) {
                    // 创建临时文件
                    val filename = "Excel_Image_Share_${UUID.randomUUID()}.jpg"
                    val outputDir = context.cacheDir
                    val outputFile = File(outputDir, filename)

                    // 保存bitmap到文件
                    FileOutputStream(outputFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }

                    // 创建FileProvider URI
                    val fileUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        outputFile
                    )

                    // 创建分享Intent
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        type = "image/jpeg"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    // 在UI线程启动分享
                    (context as? Activity)?.runOnUiThread {
                        context.startActivity(Intent.createChooser(shareIntent, "分享图片"))
                    }
                } else {
                    showErrorMessage("无法分享图片")
                }
            } catch (e: Exception) {
                Log.e(TAG, "分享图片失败: ${e.message}", e)
                showErrorMessage("分享图片失败: ${e.message}")
            }
        }
    }

    /**
     * 在UI线程显示错误消息
     */
    private fun showErrorMessage(message: String) {
        Log.e(TAG, message)
        (context as? Activity)?.runOnUiThread {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示对话框
     */
    fun show() {
        updateUI()
        dialog?.show()

        // 设置对话框尺寸
        dialog?.window?.let { window ->
            val displayMetrics = DisplayMetrics()
            window.windowManager.defaultDisplay.getMetrics(displayMetrics)

            val width = customWidth ?: (displayMetrics.widthPixels * widthRatio).toInt()
            val height = customHeight ?: (displayMetrics.heightPixels * heightRatio).toInt()

            window.setLayout(width, height)
            window.setGravity(android.view.Gravity.CENTER)
        }

        loadImage()
    }

    /**
     * 更新UI元素
     */
    private fun updateUI() {
        // 设置标题
        if (title.isNullOrEmpty()) {
            titleTextView?.visibility = View.GONE
        } else {
            titleTextView?.text = title
            titleTextView?.visibility = View.VISIBLE
        }

        // 确保PhotoView容器可见
        photoContainer?.visibility = View.VISIBLE

        // 设置底部工具栏
//        bottomBar?.visibility = if (enableSave || enableShare) View.VISIBLE else View.GONE

        // 设置按钮可见性
        saveButton?.visibility = if (enableSave) View.VISIBLE else View.GONE
        shareButton?.visibility = if (enableShare) View.VISIBLE else View.GONE

        // 设置背景颜色
        dialog?.window?.decorView?.findViewById<View>(R.id.dialog_root)?.setBackgroundColor(backgroundColor)

        // 设置是否可取消
        dialog?.setCancelable(cancelable)
    }

    /**
     * 关闭对话框
     */
    fun dismiss() {
        dialog?.dismiss()
    }

    /**
     * 释放资源
     */
    fun release() {
        dismiss()
        photoView = null
        progressBar = null
        errorView = null
        titleTextView = null
        saveButton = null
        shareButton = null
        closeButton = null
        bottomBar = null
        dialog = null
    }

    /**
     * 建造者类
     */
    class Builder(private val context: Context) {
        private var themeResId: Int = R.style.ExcelImageDialogTheme
        private var imageUri: Uri? = null
        private var imagePath: String? = null
        private var imageResId: Int? = null
        private var title: String? = null
        private var loadingText: String = "加载中..."
        private var errorText: String = "加载失败，点击重试"
        private var enableSave: Boolean = true
        private var enableShare: Boolean = true
        private var backgroundColor: Int = Color.WHITE
        private var cancelable: Boolean = true
        private var widthRatio: Float = 0.5f  // 默认宽度为屏幕的50%
        private var heightRatio: Float = 0.67f // 默认高度为屏幕的67%（约2/3）
        private var customWidth: Int? = null
        private var customHeight: Int? = null

        /**
         * 设置对话框主题样式
         */
        fun setTheme(@StyleRes themeResId: Int): Builder {
            this.themeResId = themeResId
            return this
        }

        /**
         * 设置图片URI
         */
        fun setImageUri(uri: Uri): Builder {
            this.imageUri = uri
            this.imagePath = null
            this.imageResId = null
            return this
        }

        /**
         * 设置图片路径
         */
        fun setImagePath(path: String): Builder {
            this.imagePath = path
            this.imageUri = null
            this.imageResId = null
            return this
        }

        /**
         * 设置图片资源ID
         */
        fun setImageResource(@DrawableRes resId: Int): Builder {
            this.imageResId = resId
            this.imageUri = null
            this.imagePath = null
            return this
        }

        /**
         * 设置对话框标题
         */
        fun setTitle(title: String?): Builder {
            this.title = title
            return this
        }

        /**
         * 设置加载中文本
         */
        fun setLoadingText(text: String): Builder {
            this.loadingText = text
            return this
        }

        /**
         * 设置错误文本
         */
        fun setErrorText(text: String): Builder {
            this.errorText = text
            return this
        }

        /**
         * 设置是否启用保存按钮
         */
        fun enableSave(enable: Boolean): Builder {
            this.enableSave = enable
            return this
        }

        /**
         * 设置是否启用分享按钮
         */
        fun enableShare(enable: Boolean): Builder {
            this.enableShare = enable
            return this
        }

        /**
         * 设置对话框背景颜色
         */
        fun setBackgroundColor(color: Int): Builder {
            this.backgroundColor = color
            return this
        }

        /**
         * 设置对话框是否可取消
         */
        fun setCancelable(cancelable: Boolean): Builder {
            this.cancelable = cancelable
            return this
        }

        /**
         * 设置对话框宽度占屏幕的比例
         */
        fun setWidthRatio(@FloatRange(from = 0.0, to = 1.0) ratio: Float): Builder {
            this.widthRatio = ratio
            this.customWidth = null
            return this
        }

        /**
         * 设置对话框高度占屏幕的比例
         */
        fun setHeightRatio(@FloatRange(from = 0.0, to = 1.0) ratio: Float): Builder {
            this.heightRatio = ratio
            this.customHeight = null
            return this
        }

        /**
         * 设置对话框固定宽度（像素）
         */
        fun setWidth(width: Int): Builder {
            this.customWidth = width
            return this
        }

        /**
         * 设置对话框固定高度（像素）
         */
        fun setHeight(height: Int): Builder {
            this.customHeight = height
            return this
        }

        /**
         * 创建对话框实例
         */
        fun build(): ExcelImageDialog {
            val dialog = ExcelImageDialog(context, themeResId)

            dialog.imageUri = this.imageUri
            dialog.imagePath = this.imagePath
            dialog.imageResId = this.imageResId
            dialog.title = this.title
            dialog.loadingText = this.loadingText
            dialog.errorText = this.errorText
            dialog.enableSave = this.enableSave
            dialog.enableShare = this.enableShare
            dialog.backgroundColor = this.backgroundColor
            dialog.cancelable = this.cancelable
            dialog.widthRatio = this.widthRatio
            dialog.heightRatio = this.heightRatio
            dialog.customWidth = this.customWidth
            dialog.customHeight = this.customHeight

            return dialog
        }

        /**
         * 创建并显示对话框
         */
        fun show(): ExcelImageDialog {
            val dialog = build()
            dialog.show()
            return dialog
        }
    }

    companion object {
        private const val TAG = "ExcelImageDialog"
    }
}