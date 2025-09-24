markdown
# Excel Editor Android 应用

一个功能丰富的Android Excel编辑器应用，支持查看、编辑和操作Excel表格数据。

## 项目概述

Excel Editor是一个Android应用程序，允许用户查看和编辑Excel格式的表格数据。该应用支持多种单元格类型，包括文本、选项、图片、签名等，并提供丰富的编辑功能，如单元格内容编辑、背景颜色设置、缩放控制等。

## 功能特性

### 核心功能
- **Excel表格展示**：以表格形式展示Excel数据，支持合并单元格显示
- **单元格编辑**：点击任意单元格进行内容编辑
- **多种单元格类型支持**：
    - 文本类型（类型1）
    - 平均值类型（类型2）
    - 选项类型（类型3）
    - 范围类型（类型4）
    - 图片类型（类型5）
    - 签名类型（类型6）
    - 置灰类型（类型7）
    - 搜索项类型（类型8）
- **视觉定制**：
    - 背景颜色设置（支持7种预设颜色）
    - 预设符号插入
    - 缩放控制（放大/缩小）
- **用户界面**：
    - 底部编辑栏
    - 响应式设计
    - 直观的操作界面

### 技术特性
- **MVVM架构**：采用Model-View-ViewModel架构模式
- **数据绑定**：使用Android Data Binding库
- **异步处理**：使用Kotlin Coroutines进行异步操作
- **依赖注入**：通过ViewModel实现依赖管理
- **手势支持**：支持缩放手势操作

## 技术栈

- **语言**：Kotlin
- **最低SDK版本**：API 26 (Android 8.0)
- **目标SDK版本**：API 34 (Android 14)
- **架构模式**：MVVM (Model-View-ViewModel)
- **主要库**：
    - AndroidX Core KTX
    - AndroidX AppCompat
    - Material Design Components
    - AndroidX RecyclerView
    - AndroidX Lifecycle (ViewModel, LiveData)
    - Apache POI (用于Excel处理)

## 项目结构
app/
├── src/
│ ├── main/
│ │ ├── java/com/xctech/excelpj/
│ │ │ ├── Repository/ # 数据仓库层
│ │ │ ├── data/ # 数据模型类
│ │ │ ├── view/ # 视图层（Activity, View）
│ │ │ └── viewmodel/ # ViewModel层
│ │ ├── res/ # 资源文件
│ │ └── assets/ # 静态资源文件
│ └── build.gradle.kts # 模块构建配置
├── build.gradle.kts # 项目构建配置
└── settings.gradle.kts # 项目设置

## 核心组件

### 1. ExcelEditorActivity
主活动页面，负责：
- 展示Excel表格内容
- 处理用户交互
- 管理底部编辑栏
- 控制缩放功能

### 2. ExcelTableView
自定义视图组件，负责：
- 绘制Excel表格
- 处理单元格点击事件
- 管理单元格渲染
- 支持手势缩放

### 3. ExcelViewModel
ViewModel组件，负责：
- 管理UI相关的数据
- 处理业务逻辑
- 协调数据获取和更新

### 4. 数据模型
- [ExcelResponse](file://e:\Local_project\android_pj\excelPj\app\src\main\java\com\xctech\excelpj\data\ExcelResponse.kt#L15-L17)：Excel表单响应数据
- [ExcelInfo](file://e:\Local_project\android_pj\excelPj\app\src\main\java\com\xctech\excelpj\data\ExcelResponse.kt#L28-L28)：Excel信息数据
- [ExcelCell](file://e:\Local_project\android_pj\excelPj\app\src\main\java\com\xctech\excelpj\data\ExcelResponse.kt#L55-L56)：单元格数据
- [ExcelMerge](file://e:\Local_project\android_pj\excelPj\app\src\main\java\com\xctech\excelpj\data\ExcelResponse.kt#L40-L41)：合并单元格信息

## 安装与运行

### 环境要求
- Android Studio Arctic Fox或更高版本
- Kotlin 1.5+
- JDK 11

### 构建步骤
1. 克隆项目到本地
2. 使用Android Studio打开项目
3. 等待Gradle同步完成
4. 构建并运行应用

### 依赖配置
项目使用阿里云Maven仓库作为依赖源，配置在[settings.gradle.kts](file://e:\Local_project\android_pj\excelPj\settings.gradle.kts)中。

## 使用说明

1. 启动应用后，将显示Excel表格界面
2. 点击任意单元格可进入编辑模式
3. 在底部编辑栏中可以：
    - 修改单元格内容
    - 选择预设颜色为单元格着色
    - 插入预设符号
    - 使用选项下拉框（针对选项类型单元格）
4. 使用右上角的缩放按钮调整表格显示比例
5. 编辑完成后，内容会自动保存

## 开发指南

### 添加新功能
1. 在ViewModel中添加相应的业务逻辑
2. 在Activity中处理UI交互
3. 如需要，扩展数据模型类

### 扩展单元格类型
1. 在[ExcelCell](file://e:\Local_project\android_pj\excelPj\app\src\main\java\com\xctech\excelpj\data\ExcelResponse.kt#L55-L56)数据类中添加新的属性
2. 在[ExcelTableView](file://e:\Local_project\android_pj\excelPj\app\src\main\java\com\xctech\excelpj\view\ExcelTableView.kt#L15-L366)中实现新的渲染逻辑
3. 在[ExcelEditorActivity](file://e:\Local_project\android_pj\excelPj\app\src\main\java\com\xctech\excelpj\view\ExcelEditorActivity.kt#L19-L308)中添加相应的编辑控件

## 未来改进方向

1. 实现网络数据加载功能
2. 添加Excel文件导入/导出功能
3. 增加更多单元格类型支持
4. 实现离线数据存储
5. 添加用户设置功能
6. 支持表格公式计算

## 许可证

本项目仅供学习和参考使用。

## 联系方式

如有问题或建议，请联系项目维护者。