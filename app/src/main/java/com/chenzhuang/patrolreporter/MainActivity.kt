package com.chenzhuang.patrolreporter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val photoPaths = mutableListOf<String>()
    private var tempPhotoUri: Uri? = null
    private var tempPhotoPath: String? = null

    // 相机拍照回调
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempPhotoPath != null) {
            photoPaths.add(tempPhotoPath!!)
            Toast.makeText(this, "照片添加成功，当前共 ${photoPaths.size} 张", Toast.LENGTH_SHORT).show()
        }
    }

    // 相册多选回调
    private val pickGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri ->
            copyUriToFile(uri)?.let { photoPaths.add(it) }
        }
        Toast.makeText(this, "相册导入完成，当前共 ${photoPaths.size} 张", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 纯代码布局，无需额外配置复杂 xml 视图
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        val etDesc = EditText(this).apply {
            hint = "请输入巡检发现的问题描述..."
            minLines = 4
        }

        val btnCamera = Button(this).apply {
            text = "📷 拍照添加照片"
            setOnClickListener { launchCamera() }
        }

        val btnGallery = Button(this).apply {
            text = "🖼️ 从相册选择照片"
            setOnClickListener { pickGalleryLauncher.launch("image/*") }
        }

        val btnExport = Button(this).apply {
            text = "📊 导出为 Excel 表格"
            setOnClickListener {
                exportToExcel(etDesc.text.toString())
            }
        }

        layout.addView(etDesc)
        layout.addView(btnCamera)
        layout.addView(btnGallery)
        layout.addView(btnExport)
        setContentView(layout)
    }

    private fun launchCamera() {
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File.createTempFile("PATROL_${System.currentTimeMillis()}_", ".jpg", dir)
        tempPhotoPath = file.absolutePath
        tempPhotoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        takePictureLauncher.launch(tempPhotoUri)
    }

    private fun copyUriToFile(uri: Uri): String? {
        return try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val file = File.createTempFile("GALLERY_${System.currentTimeMillis()}_", ".jpg", dir)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun exportToExcel(description: String) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("巡检问题记录")
            val row0 = sheet.createRow(0)
            row0.createCell(0).setCellValue("记录时间")
            row0.createCell(1).setCellValue("问题描述")
            row0.createCell(2).setCellValue("照片数量")

            val row1 = sheet.createRow(1)
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            row1.createCell(0).setCellValue(time)
            row1.createCell(1).setCellValue(description)
            row1.createCell(2).setCellValue("${photoPaths.size} 张照片已关联")

            val exportDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val outFile = File(exportDir, "陈壮巡检问题_${System.currentTimeMillis()}.xlsx")
            FileOutputStream(outFile).use { workbook.write(it) }
            workbook.close()

            // 调起分享
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", outFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享巡检 Excel 表格"))
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
