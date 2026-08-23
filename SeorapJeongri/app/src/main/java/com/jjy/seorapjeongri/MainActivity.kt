package com.jjy.seorapjeongri

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private data class FileItem(val file: File, val category: String, val duplicate: Boolean = false)

class MainActivity : ComponentActivity() {
    private var storageAccess by mutableStateOf(hasStorageAccess())
    private var scanRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DrawerOrganizer(
                    storageAccess = storageAccess,
                    scanRequested = scanRequested,
                    requestStorage = ::requestStorageAccess
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        storageAccess = hasStorageAccess()
    }

    private fun requestStorageAccess() {
        scanRequested = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            startActivity(Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        }
    }

    private fun hasStorageAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
}

@Composable
private fun DrawerOrganizer(
    storageAccess: Boolean,
    scanRequested: Boolean,
    requestStorage: () -> Unit
) {
    var page by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf(setOf("DCIM", "Download", "Pictures")) }

    LaunchedEffect(storageAccess, scanRequested) {
        if (storageAccess && scanRequested) page = 2
    }

    when (page) {
        0 -> HomeScreen { page = 1 }
        1 -> AreaSelection(
            selected = selected,
            onToggle = { name -> selected = if (name in selected) selected - name else selected + name },
            onNext = { if (storageAccess) page = 2 else requestStorage() }
        )
        else -> ScanScreen(selectedFolders = selected, onNext = { page = 3 })
    }
}

@Composable
private fun HomeScreen(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("서랍정리", fontSize = 32.sp)
        Spacer(Modifier.height(24.dp))
        Text("파일을 종류별로 정리해 드립니다.", fontSize = 18.sp)
        Spacer(Modifier.height(48.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("파일 정리 시작", fontSize = 18.sp)
        }
    }
}

@Composable
private fun AreaSelection(selected: Set<String>, onToggle: (String) -> Unit, onNext: () -> Unit) {
    val names = listOf("DCIM", "Download", "Pictures", "Movies", "Music", "Documents")
    Column(
        Modifier.fillMaxSize().systemBarsPadding().navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text("정리할 영역을 선택하세요", fontSize = 24.sp)
        Spacer(Modifier.height(8.dp))
        Text("선택한 폴더와 하위 폴더를 검사합니다.")
        Text("JJY DATA는 자동으로 제외됩니다.")
        Spacer(Modifier.height(18.dp))
        names.forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = name in selected, onCheckedChange = { onToggle(name) })
                Text(name, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onNext,
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("파일 검사 시작", fontSize = 18.sp) }
    }
}

@Composable
private fun ScanScreen(selectedFolders: Set<String>, onNext: () -> Unit) {
    var scanning by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showResults by remember { mutableStateOf(false) }

    LaunchedEffect(selectedFolders) {
        scanning = true
        error = null
        try { result = withContext(Dispatchers.IO) { scanFolders(selectedFolders) } }
        catch (e: Exception) { error = e.message ?: "파일 검사 중 오류가 발생했습니다." }
        finally { scanning = false }
    }

    val duplicateCount = result.count { it.duplicate }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().navigationBarsPadding().padding(20.dp)
    ) {
        Text("파일 검사", fontSize = 28.sp)
        Spacer(Modifier.height(16.dp))
        when {
            scanning -> {
                Text("선택한 폴더의 파일을 검사하고 있습니다…")
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
                Text("파일 수에 따라 시간이 걸릴 수 있습니다.")
            }
            error != null -> {
                Text("검사 오류", fontSize = 22.sp)
                Spacer(Modifier.height(8.dp))
                Text(error!!)
            }
            else -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("검사 완료", fontSize = 22.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("대상 파일: ${result.size}개", fontSize = 18.sp)
                        Text("중복 의심: ${duplicateCount}개", fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showResults = true },
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Text("대상 파일 목록 및 검사 결과 보기", fontSize = 16.sp)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("다음", fontSize = 18.sp)
                }
            }
        }
    }

    if (showResults && !scanning && error == null) {
        AlertDialog(
            onDismissRequest = { showResults = false },
            title = { Text("검사 결과") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text("대상 파일 ${result.size}개 · 중복 의심 ${duplicateCount}개")
                    Spacer(Modifier.height(8.dp))
                    if (result.isEmpty()) {
                        Text("정리 대상 파일이 없습니다.")
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp)) {
                            items(result.take(500)) { item ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                                    Text(item.file.name, fontSize = 15.sp)
                                    Text("${item.category} · ${item.file.parent ?: ""}", fontSize = 11.sp)
                                    if (item.duplicate) Text("중복 의심", fontSize = 11.sp)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showResults = false }) {
                    Text("닫기", fontSize = 16.sp)
                }
            }
        )
    }
}

@Composable
private fun PreviewScreen() {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("정리 미리보기", fontSize = 28.sp)
        Spacer(Modifier.height(16.dp))
        Text("검사 결과를 바탕으로 정리할 파일을 확인합니다.", fontSize = 17.sp)
        Spacer(Modifier.height(24.dp))
        Text("다음 단계에서 체크박스로 정리하지 않을 파일을 선택할 수 있습니다.", fontSize = 14.sp)
    }
}

private fun scanFolders(selectedFolders: Set<String>): List<FileItem> {
    val root = Environment.getExternalStorageDirectory()
    val found = mutableListOf<FileItem>()
    val seen = mutableMapOf<String, Int>()

    fun walk(dir: File) {
        if (!dir.exists() || !dir.isDirectory) return
        if (dir.name.equals("JJY DATA", ignoreCase = true)) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.name.equals("JJY DATA", ignoreCase = true)) continue
            if (child.isDirectory) walk(child)
            else if (child.isFile && child.canRead()) {
                val category = classify(child) ?: continue
                val key = child.name.lowercase(Locale.getDefault()) + "|" + child.length()
                val count = seen.getOrDefault(key, 0)
                seen[key] = count + 1
                found += FileItem(child, category, count > 0)
            }
        }
    }

    selectedFolders.forEach { walk(File(root, it)) }
    return found.sortedWith(compareBy<FileItem> { !it.duplicate }.thenBy { it.category }.thenBy { it.file.name.lowercase() })
}

private fun classify(file: File): String? {
    val name = file.name
    val lower = name.lowercase(Locale.getDefault())
    val ext = lower.substringAfterLast('.', "")
    val base = lower.substringBeforeLast('.', lower)
    if (ext == "mp3") return if (name.contains("MR", true)) "MR" else "MP3"
    if (ext in setOf("jpg","jpeg","gif","png","tif","tiff","bmp","webp","heic","heif")) return "IMAGE"
    if (ext == "pdf") return if (Regex("(b[1-6]|[1-6]b|#[1-6]|[1-6]#|_c)$", RegexOption.IGNORE_CASE).containsMatchIn(base)) "악보" else "PDF"
    if (ext in setOf("hwp","hwpx")) return "한글문서"
    if (ext in setOf("doc","docx","xls","xlsx","ppt","pptx","csv","txt")) return "MS문서"
    if (ext in setOf("zip","7z","rar","tar","gz","bz2","xz")) return "압축파일"
    if (ext in setOf("dwg","dwf","dxf","stp","step","igs","iges","ipt","iam","stl","obj","3ds","3mf","fbx")) return "캐드파일"
    if (ext in setOf("avi","mp4","mkv","mov","wmv","flv","webm","m4v","3gp","mpeg","mpg")) return "영상파일"
    return null
}
