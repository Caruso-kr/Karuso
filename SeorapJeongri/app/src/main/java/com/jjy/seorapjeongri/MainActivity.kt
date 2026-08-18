package com.jjy.seorapjeongri

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var started by remember { mutableStateOf(false) }
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!started) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("서랍정리", fontSize = 32.sp)
                            Spacer(Modifier.height(24.dp))
                            Text("파일을 종류별로 정리해 드립니다.", fontSize = 18.sp)
                            Spacer(Modifier.height(48.dp))
                            Button(onClick = { started = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("파일 정리 시작")
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                                Text("정리 기록 보기")
                            }
                        }
                    } else {
                        AreaSelection()
                    }
                }
            }
        }
    }
}

@Composable
fun AreaSelection() {
    val names = listOf("DCIM", "Download", "Pictures", "Movies", "Music", "Documents")
    val checked = remember { mutableStateMapOf<String, Boolean>().apply { names.forEach { put(it, it in listOf("DCIM", "Download", "Pictures")) } } }
    var showNext by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text("정리할 영역을 선택하세요", fontSize = 24.sp)
        Spacer(Modifier.height(8.dp))
        Text("선택한 폴더와 하위 폴더를 검사합니다. JJY DATA는 자동 제외됩니다.")
        Spacer(Modifier.height(20.dp))
        names.forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked[name] == true, onCheckedChange = { checked[name] = it })
                Text(name, fontSize = 18.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = false, onCheckedChange = {})
            Text("기타 폴더 직접 선택", fontSize = 18.sp)
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { showNext = true },
            enabled = checked.values.any { it },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("다음", fontSize = 18.sp) }
    }

    if (showNext) {
        AlertDialog(
            onDismissRequest = { showNext = false },
            title = { Text("파일 검사 준비") },
            text = { Text("선택한 영역의 파일을 검사합니다. 다음 단계에서 실제 파일 검사 기능을 연결합니다.") },
            confirmButton = {
                TextButton(onClick = { showNext = false }) { Text("확인") }
            }
        )
    }
}
