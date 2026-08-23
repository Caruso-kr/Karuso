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
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private data class Item(val file: File, val category: String, val folder: String, val duplicate: Boolean)
private data class Result(val source: String, val destination: String, val ok: Boolean)
private enum class Page { HOME, AREA, SCAN, PREVIEW, MOVE, DONE }

class MainActivity : ComponentActivity() {
    private var access by mutableStateOf(hasAccess())
    private var requested by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme { App(access, requested, ::requestAccess) } } }
    override fun onResume() { super.onResume(); access = hasAccess() }
    private fun requestAccess() { requested = true; if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) }
    private fun hasAccess() = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
}

@Composable private fun App(access: Boolean, requested: Boolean, requestAccess: () -> Unit) {
    var page by remember { mutableStateOf(Page.HOME) }
    var all by remember { mutableStateOf(false) }
    var folders by remember { mutableStateOf(setOf<String>()) }
    var items by remember { mutableStateOf<List<Item>>(emptyList()) }
    var chosen by remember { mutableStateOf<List<Item>>(emptyList()) }
    var results by remember { mutableStateOf<List<Result>>(emptyList()) }
    LaunchedEffect(access, requested) { if (access && requested && page == Page.AREA) page = Page.SCAN }
    when (page) {
        Page.HOME -> Home { page = Page.AREA }
        Page.AREA -> Area(folders, all, { all = it; if (it) folders = emptySet() }, { f -> all = false; folders = if (f in folders) folders - f else folders + f }, { if (access) page = Page.SCAN else requestAccess() })
        Page.SCAN -> Scan(folders, all) { items = it; page = Page.PREVIEW }
        Page.PREVIEW -> Preview(items, { page = Page.AREA }) { chosen = it; page = Page.MOVE }
        Page.MOVE -> Move(chosen) { results = it; page = Page.DONE }
        Page.DONE -> Done(results) { page = Page.AREA }
    }
}

@Composable private fun Home(start: () -> Unit) { Column(Modifier.fillMaxSize().systemBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("서랍정리", fontSize = 34.sp); Spacer(Modifier.height(18.dp)); Text("휴대폰 파일을 종류별로 정리합니다.", fontSize = 18.sp); Spacer(Modifier.height(44.dp)); Button(start, Modifier.fillMaxWidth().height(58.dp)) { Text("파일 정리 시작", fontSize = 18.sp) } } }

@Composable private fun Area(selected: Set<String>, all: Boolean, setAll: (Boolean)->Unit, toggle: (String)->Unit, next: ()->Unit) {
    val root = remember { Environment.getExternalStorageDirectory() }
    val names = remember { root.listFiles()?.filter { it.isDirectory && !it.name.equals("JJY DATA",true) && !it.name.equals("Android",true) }?.map { it.name }?.sortedBy { it.lowercase(Locale.getDefault()) } ?: emptyList() }
    Column(Modifier.fillMaxSize().systemBarsPadding().navigationBarsPadding().padding(20.dp)) {
        Text("정리 영역 선택", fontSize=28.sp); Spacer(Modifier.height(6.dp)); Text("주요 내부 저장소 폴더를 개별 선택할 수 있습니다."); Text("JJY DATA는 자동 제외됩니다.",fontSize=13.sp); Spacer(Modifier.height(10.dp))
        Row(verticalAlignment=Alignment.CenterVertically){ Checkbox(checked=all,onCheckedChange=setAll); Text("전체 내부 저장소",fontSize=18.sp) }; HorizontalDivider()
        LazyColumn(Modifier.weight(1f)){ items(names){ n-> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){ Checkbox(checked=!all&&n in selected,onCheckedChange={toggle(n)}); Text(n,fontSize=17.sp) } } }
        Button(onClick=next,enabled=all||selected.isNotEmpty(),modifier=Modifier.fillMaxWidth().height(58.dp)){ Text("검사 시작",fontSize=18.sp) }
    }
}

@Composable private fun Scan(selected:Set<String>,all:Boolean,done:(List<Item>)->Unit){
    var count by remember{mutableIntStateOf(0)}; var status by remember{mutableStateOf("파일을 검사하고 있습니다…")}
    LaunchedEffect(Unit){ val r=withContext(Dispatchers.IO){scan(selected,all){n,s->count=n;status=s}}; done(r) }
    Column(Modifier.fillMaxSize().systemBarsPadding().navigationBarsPadding().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){ CircularProgressIndicator(); Spacer(Modifier.height(20.dp)); Text("파일 검사 중",fontSize=28.sp); Spacer(Modifier.height(10.dp)); Text(status); Spacer(Modifier.height(8.dp)); Text("검사한 파일: $count개") }
}

@Composable private fun Preview(items:List<Item>,back:()->Unit,execute:(List<Item>)->Unit){
    val checked=remember(items){mutableStateMapOf<String,Boolean>().apply{items.forEach{put(it.file.absolutePath,true)}}}; var popup by remember{mutableStateOf(false)}; var dupOnly by remember{mutableStateOf(false)}; val count=checked.values.count{it}; val dup=items.count{it.duplicate}
    Column(Modifier.fillMaxSize().systemBarsPadding().navigationBarsPadding().padding(20.dp)){ Text("정리 미리보기",fontSize=28.sp); Spacer(Modifier.height(10.dp)); Text("대상 ${items.size}개 · 선택 ${count}개 · 중복 의심 ${dup}개"); Spacer(Modifier.height(18.dp)); Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Text("파일 목록에서 체크를 해제하면 해당 파일은 정리하지 않습니다."); Spacer(Modifier.height(6.dp)); Text("중복 의심은 폴더와 관계없이 파일명+크기가 같은 파일입니다.",fontSize=12.sp)}}; Spacer(Modifier.height(14.dp)); OutlinedButton({popup=true},Modifier.fillMaxWidth().height(56.dp)){Text("대상 파일 목록 / 체크박스")}; Spacer(Modifier.weight(1f)); Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(back,Modifier.weight(1f).height(56.dp)){Text("다시 선택")}; Button({execute(items.filter{checked[it.file.absolutePath]==true})},enabled=count>0,modifier=Modifier.weight(1f).height(56.dp)){Text("정리 실행")}}
    }
    if(popup)AlertDialog(onDismissRequest={popup=false},title={Text("정리 대상 파일")},text={Column(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Checkbox(dupOnly,{dupOnly=it});Text("중복 의심만 보기")};Text("선택 ${checked.values.count{it}}개 / 전체 ${items.size}개",fontSize=12.sp);Spacer(Modifier.height(6.dp));LazyColumn(Modifier.fillMaxWidth().heightIn(max=460.dp)){items(if(dupOnly)items.filter{it.duplicate}else items){x->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Checkbox(checked[x.file.absolutePath]==true,{checked[x.file.absolutePath]=it});Column(Modifier.weight(1f).padding(vertical=6.dp)){Text(x.file.name,fontSize=14.sp);Text("${x.category} → JJY DATA/${x.category}${if(x.folder.isBlank())"" else "/${x.folder}"}",fontSize=10.sp);if(x.duplicate)Text("중복 의심",fontSize=10.sp)}};HorizontalDivider()}}}},confirmButton={TextButton({popup=false}){Text("닫기")}})
}

@Composable private fun Move(items:List<Item>,done:(List<Result>)->Unit){
    val pause=remember{AtomicBoolean(false)};var paused by remember{mutableStateOf(false)};var current by remember{mutableIntStateOf(0)};var status by remember{mutableStateOf("정리를 시작합니다…")}
    LaunchedEffect(items){val out=mutableListOf<Result>();withContext(Dispatchers.IO){items.forEachIndexed{i,x->while(pause.get())delay(120);val d=unique(dest(x));val ok=try{d.parentFile?.mkdirs();if(x.file.renameTo(d))true else{x.file.copyTo(d,false);x.file.delete()}}catch(_:Exception){false};out+=Result(x.file.absolutePath,d.absolutePath,ok);withContext(Dispatchers.Main){current=i+1;status=if(ok)"정리 중: ${x.file.name}" else "실패: ${x.file.name}"}}};done(out)}
    Column(Modifier.fillMaxSize().systemBarsPadding().navigationBarsPadding().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text("정리 실행 중",fontSize=28.sp);Spacer(Modifier.height(18.dp));LinearProgressIndicator(progress={if(items.isEmpty())1f else current.toFloat()/items.size},Modifier.fillMaxWidth());Spacer(Modifier.height(12.dp));Text("$current / ${items.size}");Spacer(Modifier.height(8.dp));Text(status,fontSize=14.sp);Spacer(Modifier.height(22.dp));if(current<items.size)Button({val p=!pause.get();pause.set(p);paused=p},Modifier.fillMaxWidth().height(56.dp)){Text(if(paused)"계속하기"else"일시중지")}}
}

@Composable private fun Done(results:List<Result>,restart:()->Unit){var popup by remember{mutableStateOf(false)};val ok=results.count{it.ok};Column(Modifier.fillMaxSize().systemBarsPadding().navigationBarsPadding().padding(20.dp)){Text("정리 완료",fontSize=30.sp);Spacer(Modifier.height(14.dp));Card(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp)){Text("처리 파일: ${results.size}개",fontSize=18.sp);Text("완료: ${ok}개");Text("실패: ${results.size-ok}개")}};Spacer(Modifier.height(16.dp));OutlinedButton({popup=true},Modifier.fillMaxWidth().height(56.dp)){Text("정리 내역 보기")};Spacer(Modifier.height(10.dp));Button(restart,Modifier.fillMaxWidth().height(56.dp)){Text("처음으로")}};if(popup)AlertDialog(onDismissRequest={popup=false},title={Text("정리 내역")},text={LazyColumn(Modifier.fillMaxWidth().heightIn(max=480.dp)){items(results){r->Column(Modifier.fillMaxWidth().padding(vertical=7.dp)){Text(if(r.ok)"완료"else"실패");Text(File(r.source).name,fontSize=14.sp);Text(r.destination,fontSize=10.sp)};HorizontalDivider()}}},confirmButton={TextButton({popup=false}){Text("닫기")}})}

private fun scan(selected:Set<String>,all:Boolean,progress:(Int,String)->Unit):List<Item>{val root=Environment.getExternalStorageDirectory();val roots=if(all)root.listFiles()?.filter{it.isDirectory&&!it.name.equals("JJY DATA",true)&&!it.name.equals("Android",true)}?:emptyList()else selected.map{File(root,it)};val files=mutableListOf<Pair<File,String>>();fun walk(d:File,rel:String){if(!d.isDirectory||d.name.equals("JJY DATA",true))return;for(f in d.listFiles()?:emptyArray()){if(f.name.equals("JJY DATA",true))continue;if(f.isDirectory)walk(f,if(rel.isBlank())f.name else "$rel/${f.name}")else if(f.isFile&&f.canRead()){if(classify(f)!=null)files+=f to rel;progress(files.size,"검사 중: ${f.name}")}}};roots.forEach{walk(it,"")};val counts=files.groupingBy{it.first.name.lowercase(Locale.getDefault())+"|"+it.first.length()}.eachCount();return files.map{val f=it.first;Item(f,classify(f)!!,it.second,(counts[f.name.lowercase(Locale.getDefault())+"|"+f.length()]?:0)>1)}}

private fun classify(f:File):String?{val n=f.name;val l=n.lowercase(Locale.getDefault());val e=l.substringAfterLast('.','');val b=l.substringBeforeLast('.',l);if(e=="mp3")return if(n.contains("MR",true))"MR"else"MP3";if(e in setOf("jpg","jpeg","gif","png","tif","tiff","bmp","webp","heic","heif","avif"))return"IMAGE";if(e=="pdf")return if(Regex("(b[1-6]|[1-6]b|#[1-6]|[1-6]#|_c)$",RegexOption.IGNORE_CASE).containsMatchIn(b))"악보"else"PDF";if(e in setOf("hwp","hwpx"))return"한글문서";if(e in setOf("doc","docx","xls","xlsx","ppt","pptx","csv","rtf","odt","ods","odp"))return"MS문서";if(e in setOf("zip","7z","rar","tar","gz","bz2","xz","tgz"))return"압축파일";if(e in setOf("dwg","dwf","dxf","stp","step","igs","iges","ipt","iam","stl","obj","3ds","3mf","fbx","skp"))return"캐드파일";if(e in setOf("avi","mp4","mkv","mov","wmv","flv","webm","m4v","3gp","mpeg","mpg","mts","m2ts","ts"))return"영상파일";return null}
private fun dest(x:Item)=File(File(Environment.getExternalStorageDirectory(),"JJY DATA"),if(x.folder.isBlank())"${x.category}/${x.file.name}" else "${x.category}/${x.folder}/${x.file.name}")
private fun unique(f:File):File{if(!f.exists())return f;val p=f.parentFile?:return f;val n=f.name;val d=n.lastIndexOf('.');val b=if(d>0)n.substring(0,d)else n;val e=if(d>0)n.substring(d)else"";var i=1;var c:File;do{c=File(p,"$b ($i)$e");i++}while(c.exists());return c}
