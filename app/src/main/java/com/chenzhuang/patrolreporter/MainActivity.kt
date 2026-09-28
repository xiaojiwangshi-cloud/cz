package com.chenzhuang.patrolreporter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class IssueItem(
    val title: String,
    val description: String,
    val photos: List<String>,
    val time: String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
)

class MainActivity : AppCompatActivity() {

    private val issueList = mutableListOf<IssueItem>()
    private val currentPhotos = mutableListOf<String>()
    
    private var tempPhotoUri: Uri? = null
    private var tempPhotoPath: String? = null

    private lateinit var etReportTitle: EditText
    private lateinit var etDescription: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvCurrentPhotos: TextView

    // 相机权限申请
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                openCameraDirectly()
            } else {
                Toast.makeText(this, "需开启相机权限才能现场拍照", Toast.LENGTH_SHORT).show()
            }
        }

    // 拍照回调
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && tempPhotoPath != null) {
                val file = File(tempPhotoPath!!)
                if (file.exists() && file.length() > 0) {
                    currentPhotos.add(tempPhotoPath!!)
                    updateCurrentPhotosDisplay()
                }
            }
        }

    // 相册选择回调
    private val pickGalleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            uris.forEach { uri ->
                copyUriToFile(uri)?.let { currentPhotos.add(it) }
            }
            updateCurrentPhotosDisplay()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 60)
        }

        // 1. 报表总标题设置
        layout.addView(TextView(this).apply { text = "📋 报表/项目标题:"; textSize = 16f })
        etReportTitle = EditText(this).apply {
            hint = "例如: 石家庄裕华万达广场巡检记录"
            setText("陈壮巡检问题反馈表")
        }
        layout.addView(etReportTitle)

        // 2. 当前问题录入区
        layout.addView(TextView(this).apply { 
            text = "\n📝 当前问题描述:"
            textSize = 16f
        })
        etDescription = EditText(this).apply {
            hint = "详细填写发现的问题、隐患或整改建议..."
            minLines = 3
        }
        layout.addView(etDescription)

        // 3. 照片状态
        tvCurrentPhotos = TextView(this).apply {
            text = "当前问题已添加照片: 0 张"
            textSize = 13f
            setTextColor(0xFF555555.toInt())
        }
        layout.addView(tvCurrentPhotos)

        // 4. 操作按钮组
        val btnCamera = Button(this).apply {
            text = "📷 拍照添加照片"
            setOnClickListener { checkAndLaunchCamera() }
        }
        val btnGallery = Button(this).apply {
            text = "🖼️ 从手机相册导入"
            setOnClickListener { pickGalleryLauncher.launch("image/*") }
        }
        layout.addView(btnCamera)
        layout.addView(btnGallery)

        // 5. 确认录入当前问题
        val btnConfirmIssue = Button(this).apply {
            text = "✅ 确认保存此问题，继续录入下一条"
            setBackgroundColor(0xFF2E7D32.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { saveCurrentIssue() }
        }
        layout.addView(btnConfirmIssue)

        // 6. 统计与导出控制
        tvStatus = TextView(this).apply {
            text = "\n已录入问题总计: 0 个"
            textSize = 15f
        }
        layout.addView(tvStatus)

        val btnPreview = Button(this).apply {
            text = "👀 预览已录入问题清单"
            setOnClickListener { showPreviewDialog() }
        }
        layout.addView(btnPreview)

        val btnExport = Button(this).apply {
            text = "📊 导出为包含实拍图的 Excel 表格"
            setBackgroundColor(0xFF1565C0.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { exportToExcel() }
        }
        layout.addView(btnExport)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun updateCurrentPhotosDisplay() {
        tvCurrentPhotos.text = "当前问题已添加照片: ${currentPhotos.size} 张"
    }

    private fun checkAndLaunchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCameraDirectly()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCameraDirectly() {
        try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
            val file = File(dir, "PATROL_${System.currentTimeMillis()}.jpg")
            tempPhotoPath = file.absolutePath
            tempPhotoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            takePictureLauncher.launch(tempPhotoUri)
        } catch (e: Exception) {
            Toast.makeText(this, "调起相机失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun copyUriToFile(uri: Uri): String? {
        return try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
            val file = File(dir, "GALLERY_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun saveCurrentIssue() {
        val desc = etDescription.text.toString().trim()
        if (desc.isEmpty() && currentPhotos.isEmpty()) {
            Toast.makeText(this, "请至少填写描述或添加一张照片", Toast.LENGTH_SHORT).show()
            return
        }

        issueList.add(IssueItem(
            title = "问题 #${issueList.size + 1}",
            description = desc.ifEmpty { "无文字描述" },
            photos = currentPhotos.toList()
        ))

        // 重置当前输入框与暂存照片
        etDescription.text.clear()
        currentPhotos.clear()
        updateCurrentPhotosDisplay()
        tvStatus.text = "\n已录入问题总计: ${issueList.size} 个"
        Toast.makeText(this, "第 ${issueList.size} 个问题已暂存成功！", Toast.LENGTH_SHORT).show()
    }

    private fun showPreviewDialog() {
        if (issueList.isEmpty()) {
            Toast.makeText(this, "暂无录入内容，请先录入问题", Toast.LENGTH_SHORT).show()
            return
        }

        val previewLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 20)
        }

        issueList.forEachIndexed { index, item ->
            previewLayout.addView(TextView(this).apply {
                text = "${index + 1}. [${item.time}]\n描述: ${item.description}\n现场照片: ${item.photos.size} 张\n"
                textSize = 14f
            })
        }

        val scroll = ScrollView(this).apply {
            addView(previewLayout)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 800)
        }

        AlertDialog.Builder(this)
            .setTitle("已录入问题清单预览 (${issueList.size} 条)")
            .setView(scroll)
            .setPositiveButton("确定", null)
            .setNegativeButton("清空重新录入") { _, _ ->
                issueList.clear()
                tvStatus.text = "\n已录入问题总计: 0 个"
            }
            .show()
    }

    private fun exportToExcel() {
        if (issueList.isEmpty()) {
            // 如果列表为空，但输入框有内容，自动保存当前条目
            val desc = etDescription.text.toString().trim()
            if (desc.isNotEmpty() || currentPhotos.isNotEmpty()) {
                saveCurrentIssue()
            } else {
                Toast.makeText(this, "请先录入至少一个问题", Toast.LENGTH_SHORT).show()
                return
            }
        }

        try {
            val reportTitle = etReportTitle.text.toString().ifEmpty { "巡检问题汇总表" }
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("巡检明细")

            // 计算最多照片数量以动态生成照片列
            val maxPhotos = issueList.maxOfOrNull { it.photos.size }?.coerceAtLeast(1) ?: 1

            // 标题行
            val titleRow = sheet.createRow(0).apply { heightInPoints = 30f }
            val titleCell = titleRow.createCell(0)
            titleCell.setCellValue(reportTitle)

            // 表头行
            val headerRow = sheet.createRow(1).apply { heightInPoints = 25f }
            val headers = mutableListOf("序号", "记录时间", "问题描述")
            for (i in 1..maxPhotos) {
                headers.add("现场照片 $i")
            }
            headers.forEachIndexed { i, title ->
                headerRow.createCell(i).setCellValue(title)
            }

            val drawing = sheet.createDrawingPatriarch()
            val helper = workbook.creationHelper

            // 逐行填入数据和真实照片
            issueList.forEachIndexed { index, item ->
                val rowIdx = index + 2
                val row = sheet.createRow(rowIdx).apply { heightInPoints = 85f } // 设置足够行高承载照片

                row.createCell(0).setCellValue((index + 1).toDouble())
                row.createCell(1).setCellValue(item.time)
                row.createCell(2).setCellValue(item.description)

                // 嵌入该问题的所有照片
                item.photos.forEachIndexed { photoIndex, path ->
                    val colIdx = 3 + photoIndex
                    val photoFile = File(path)
                    if (photoFile.exists()) {
                        val imageBytes = compressImageForExcel(path)
                        val pictureIdx = workbook.addPicture(imageBytes, XSSFWorkbook.PICTURE_TYPE_JPEG)
                        val anchor = helper.createClientAnchor().apply {
                            setCol1(colIdx)
                            setRow1(rowIdx)
                            setCol2(colIdx + 1)
                            setRow2(rowIdx + 1)
                            dx1 = 15; dy1 = 15; dx2 = -15; dy2 = -15
                        }
                        drawing.createPicture(anchor, pictureIdx)
                    }
                }
            }

            // 列宽设置
            sheet.setColumnWidth(0, 8 * 256)
            sheet.setColumnWidth(1, 18 * 256)
            sheet.setColumnWidth(2, 38 * 256)
            for (i in 0 until maxPhotos) {
                sheet.setColumnWidth(3 + i, 22 * 256)
            }

            val exportDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
            val outFile = File(exportDir, "${reportTitle}_${System.currentTimeMillis()}.xlsx")
            FileOutputStream(outFile).use { workbook.write(it) }
            workbook.close()

            // 调起分享发送
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", outFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享导出 Excel 表格"))
        } catch (e: Exception) {
            Toast.makeText(this, "导出 Excel 失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun compressImageForExcel(path: String): ByteArray {
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return ByteArray(0)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        return stream.toByteArray()
    }
}
