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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private data class FileItem(
    val file: File,
    val category: String,
    val destinationFolder: String,
    val duplicate: Boolean = false
)

private data class MoveResult(val source: String, val destination: String, val status: String)

class MainActivity : ComponentActivity() {
    private var storageAccess by mutableStateOf(hasStorageAccess())
    private var permissionRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DrawerOrganizer(storageAccess, permissionRequested, ::requestStorageAccess)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        storageAccess = hasStorageAccess()
    }

    private fun requestStorageAccess() {
        permissionRequested = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        }
    }

    private fun hasStorageAccess(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
}

@Composable
private fun DrawerOrganizer(storageAccess: Boolean, permissionRequested: Boolean, requestStorage: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var allFiles by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var moveResult by remember { mutableStateOf<List<MoveResult>>(emptyList()) }

    LaunchedEffect(storageAccess, permissionRequested) {
        if (storageAccess && permissionRequested) page = 1
    }

    when (page) {
        0 -> HomeScreen { page = 1 }
        1 -> AreaSelection(
            selected = selected,
            allFiles = allFiles,
            onAllFiles = { allFiles = it; if (it) selected = emptySet() },
            onToggle = { name -> allFiles = false; selected = if (name in selected) selected - name else selected + name },
            onNext = {
                if (storageAccess) page = 2 else requestStorage()
            }
        )
        2 -> ScanScreen(
            selected = selected,
            allFiles = allFiles,
            onComplete = { scanResult = it; page = 3 }
        )
        3 -> PreviewScreen(
            items = scanResult,
            onBack = { page = 1 },
            onExecute = { chosen -> scanResult = chosen; page = 4 }
        )
        else -> ExecuteScreen(
            items = scanResult,
            onDone = { moveResult = it; page = 5 }
        )
        5 -> ResultScreen(moveResult)
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
        Text("휴대폰의 파일을 종류별로 정리합니다.", fontSize = 18.sp)
        Spacer(Modifier.height(48.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("파일 정리 시작", fontSize = 18.sp)
        }
    }
}

@Composable
private fun AreaSelection(
    selected: Set<String>,
    allFiles: Boolean,
    onAllFiles: (Boolean) -> Unit,
    onToggle: (String) -> Unit,
    onNext: () -> Unit
) {
    val root = Environment.getExternalStorageDirectory()
    val folders = remember {
        root.listFiles()?.filter { it.isDirectory && !it.name.equals("JJY DATA", true) && !it.name.equals("Android", true) }
            ?.sortedBy { it.name.lowercase(Locale.getDefault()) }?.map { it.name } ?: emptyList()
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text("정리 영역 선택", fontSize = 27.sp)
        Spacer(Modifier.height(6.dp))
        Text("검사할 휴대폰 내부 저장소 영역을 선택하세요.")
        Text("JJY DATA는 항상 자동 제외됩니다.", fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = allFiles, onCheckedChange = onAllFiles)
            Text("모든 파일 정리", fontSize = 18.sp)
        }
        HorizontalDivider()

        LazyColumn(Modifier.weight(1f)) {
            items(folders) { name ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = !allFiles && name in selected, onCheckedChange = { onToggle(name) })
                    Text(name, fontSize = 17.sp)
                }
            }
        }

        val canNext = allFiles || selected.isNotEmpty()
        Button(onClick = onNext, enabled = canNext, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("검사 및 미리보기", fontSize = 18.sp)
        }
    }
}

@Composable
private fun ScanScreen(selected: Set<String>, allFiles: Boolean, onComplete: (List<FileItem>) -> Unit) {
    var scanning by remember { mutableStateOf(true) }
    var count by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val result = withContext(Dispatchers.IO) { scanFolders(selected, allFiles) }
            count = result.size
            delay(250)
            onComplete(result)
        } catch (e: Exception) {
            error = e.message ?: "파일 검사 중 오류가 발생했습니다."
            scanning = false
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("파일 검사 중", fontSize = 28.sp)
        Spacer(Modifier.height(20.dp))
        if (error == null) {
            CircularProgressIndicator()
            Spacer(Modifier.height(20.dp))
            Text("휴대폰 파일을 검사하고 있습니다.")
            if (!scanning) Text("검사 파일: $count개")
        } else {
            Text(error!!)
        }
    }
}

@Composable
private fun PreviewScreen(items: List<FileItem>, onBack: () -> Unit, onExecute: (List<FileItem>) -> Unit) {
    val checked = remember(items) { mutableStateMapOf<String, Boolean>().apply { items.forEach { put(it.file.absolutePath, true) } } }
    var showList by remember { mutableStateOf(false) }
    val selectedCount = checked.count { it.value }
    val duplicateCount = items.count { it.duplicate }

    Column(Modifier.fillMaxSize().systemBarsPadding().navigationBarsPadding().padding(20.dp)) {
        Text("정리 미리보기", fontSize = 28.sp)
        Spacer(Modifier.height(10.dp))
        Text("정리 대상: ${items.size}개 · 선택: ${selectedCount}개 · 중복 의심: ${duplicateCount}개")
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = { showList = true }, modifier = Modifier.fillMaxWidth()) {
            Text("대상 파일 목록 보기")
        }
        Spacer(Modifier.height(10.dp))
        Text("중복 파일도 기본 선택되어 있습니다. 정리하지 않을 파일은 목록에서 체크를 해제하세요.", fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(items.take(1000)) { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = checked[item.file.absolutePath] == true,
                        onCheckedChange = { checked[item.file.absolutePath] = it }
                    )
                    Column(Modifier.weight(1f).padding(vertical = 5.dp)) {
                        Text(item.file.name, fontSize = 15.sp)
                        Text("→ JJY DATA/${item.category}${if (item.destinationFolder.isNotEmpty()) "/${item.destinationFolder}" else ""}", fontSize = 11.sp)
                        if (item.duplicate) Text("중복 의심", fontSize = 11.sp)
                    }
                }
                HorizontalDivider()
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(54.dp)) { Text("뒤로") }
            Button(
                onClick = { onExecute(items.filter { checked[it.file.absolutePath] == true }) },
                enabled = selectedCount > 0,
                modifier = Modifier.weight(1f).height(54.dp)
            ) { Text("정리 실행") }
        }
    }

    if (showList) {
        AlertDialog(
            onDismissRequest = { showList = false },
            title = { Text("대상 파일 목록") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    items(items.take(1000)) { item ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = checked[item.file.absolutePath] == true, onCheckedChange = { checked[item.file.absolutePath] = it })
                            Column(Modifier.weight(1f).padding(vertical = 5.dp)) {
                                Text(item.file.name, fontSize = 14.sp)
                                Text("${item.category} · ${item.file.parent ?: ""}", fontSize = 10.sp)
                                if (item.duplicate) Text("중복 의심", fontSize = 10.sp)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showList = false }) { Text("닫기") } }
        )
    }
}

@Composable
private fun ExecuteScreen(items: List<FileItem>, onDone: (List<MoveResult>) -> Unit) {
    val scope = rememberCoroutineScope()
    val paused = remember { AtomicBoolean(false) }
    var isPaused by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(true) }
    var current by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("정리를 시작합니다.") }
    var results by remember { mutableStateOf<List<MoveResult>>(emptyList()) }

    LaunchedEffect(Unit) {
        val output = mutableListOf<MoveResult>()
        withContext(Dispatchers.IO) {
            items.forEachIndexed { index, item ->
                while (paused.get()) delay(150)
                val dest = buildDestination(item.file, item.category, item.destinationFolder)
                dest.parentFile?.mkdirs()
                val finalDest = uniqueDestination(dest)
                val ok = try {
                    if (item.file.renameTo(finalDest)) true
                    else {
                        item.file.copyTo(finalDest, overwrite = false)
                        item.file.delete()
                    }
                } catch (_: Exception) { false }
                output += MoveResult(item.file.absolutePath, finalDest.absolutePath, if (ok) "완료" else "실패")
                withContext(Dispatchers.Main) {
                    current = index + 1
                    status = if (ok) "정리 중: ${item.file.name}" else "실패: ${item.file.name}"
                }
            }
        }
        results = output
        running = false
        onDone(output)
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("정리 실행 중", fontSize = 28.sp)
        Spacer(Modifier.height(18.dp))
        LinearProgressIndicator(progress = { if (items.isEmpty()) 1f else current.toFloat() / items.size }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text("$current / ${items.size}")
        Spacer(Modifier.height(10.dp))
        Text(status, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        if (running) {
            Button(onClick = { val next = !paused.get(); paused.set(next); isPaused = next }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text(if (isPaused) "정리 계속하기" else "일시중지")
            }
        }
    }
}

@Composable
private fun ResultScreen(results: List<MoveResult>) {
    var show by remember { mutableStateOf(false) }
    val success = results.count { it.status == "완료" }
    val failed = results.size - success
    Column(Modifier.fillMaxSize().systemBarsPadding().navigationBarsPadding().padding(20.dp)) {
        Text("정리 완료", fontSize = 28.sp)
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("처리 파일: ${results.size}개", fontSize = 18.sp)
                Text("완료: ${success}개")
                Text("실패: ${failed}개")
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { show = true }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("정리 내역 보기") }
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text("정리 내역") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    items(results.take(1000)) { r ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(r.status, fontSize = 13.sp)
                            Text(File(r.source).name, fontSize = 14.sp)
                            Text(r.destination, fontSize = 10.sp)
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { show = false }) { Text("닫기") } }
        )
    }
}

private fun scanFolders(selected: Set<String>, allFiles: Boolean): List<FileItem> {
    val root = Environment.getExternalStorageDirectory()
    val found = mutableListOf<FileItem>()
    val seen = mutableMapOf<String, Int>()

    fun walk(dir: File) {
        if (!dir.exists() || !dir.isDirectory || dir.name.equals("JJY DATA", true)) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.name.equals("JJY DATA", true)) continue
            if (child.isDirectory) walk(child)
            else if (child.isFile && child.canRead()) {
                val category = classify(child) ?: continue
                val key = child.name.lowercase(Locale.getDefault()) + "|" + child.length()
                val count = seen.getOrDefault(key, 0)
                seen[key] = count + 1
                val relativeParent = if (child.parentFile?.absolutePath == dir.absolutePath && dir.absolutePath == root.absolutePath) "" else child.parentFile?.name ?: ""
                found += FileItem(child, category, relativeParent, count > 0)
            }
        }
    }

    if (allFiles) walk(root) else selected.forEach { walk(File(root, it)) }
    return found.sortedWith(compareBy<FileItem> { !it.duplicate }.thenBy { it.category }.thenBy { it.file.name.lowercase() })
}

private fun buildDestination(source: File, category: String, folder: String): File {
    val root = File(Environment.getExternalStorageDirectory(), "JJY DATA")
    return if (folder.isBlank()) File(root, "$category/${source.name}") else File(root, "$category/$folder/${source.name}")
}

private fun uniqueDestination(file: File): File {
    if (!file.exists()) return file
    val base = file.nameWithoutExtension
    val ext = if (file.extension.isBlank()) "" else ".${file.extension}"
    var n = 1
    var candidate: File
    do { candidate = File(file.parentFile, "$base ($n)$ext"); n++ } while (candidate.exists())
    return candidate
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
    if (ext in setOf("doc","docx","xls","xlsx","ppt","pptx")) return "MS문서"
    if (ext in setOf("zip","7z","rar","tar","gz","bz2","xz")) return "압축파일"
    if (ext in setOf("dwg","dwf","dxf","stp","step","igs","iges","ipt","iam","stl","obj","3ds","3mf","fbx")) return "캐드파일"
    if (ext in setOf("avi","mp4","mkv","mov","wmv","flv","webm","m4v","3gp","mpeg","mpg","m2ts","mts")) return "영상파일"
    return null
}
