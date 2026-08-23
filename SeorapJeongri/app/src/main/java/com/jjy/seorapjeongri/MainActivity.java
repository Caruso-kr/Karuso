package com.jjy.seorapjeongri;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PURPLE = Color.rgb(108, 79, 181);
    private static final int BG = Color.rgb(255, 249, 255);
    private static final int TEXT = Color.rgb(35, 30, 40);
    private static final int PICK_FOLDER = 701;

    private FrameLayout root;
    private final ArrayList<File> selectedRoots = new ArrayList<>();
    private boolean allStorage = false;
    private final ArrayList<Item> items = new ArrayList<>();
    private final ArrayList<Result> results = new ArrayList<>();
    private ExecutorService executor;
    private volatile boolean paused = false;

    private static class Item {
        File file; String category; String relativeFolder; boolean duplicate; boolean selected = true;
        Item(File f, String c, String r) { file=f; category=c; relativeFolder=r; }
    }
    private static class Result { String source, destination; boolean ok; Result(String s,String d,boolean o){source=s;destination=d;ok=o;} }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        executor = Executors.newSingleThreadExecutor();
        showHome();
    }
    @Override protected void onDestroy(){ super.onDestroy(); if(executor!=null) executor.shutdownNow(); }

    private void base(){
        root=new FrameLayout(this); root.setBackgroundColor(BG); setContentView(root);
    }
    private TextView title(String s,int size){ TextView t=new TextView(this); t.setText(s); t.setTextColor(TEXT); t.setTextSize(size); t.setGravity(Gravity.CENTER_VERTICAL); t.setTypeface(null,1); return t; }
    private TextView body(String s,int size){ TextView t=new TextView(this); t.setText(s); t.setTextColor(TEXT); t.setTextSize(size); return t; }
    private Button button(String s){ Button b=new Button(this); b.setText(s); b.setTextSize(17); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setBackgroundColor(PURPLE); return b; }
    private Space gap(int h){ Space s=new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(1,h)); return s; }
    private LinearLayout column(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(24,20,24,18); return l; }
    private void put(ViewGroup p,View v,int w,int h){ p.addView(v,new LinearLayout.LayoutParams(w,h)); }

    private void showHome(){
        base(); LinearLayout l=column(); l.setGravity(Gravity.CENTER_HORIZONTAL); root.addView(l,new FrameLayout.LayoutParams(-1,-1));
        Space top=new Space(this); l.addView(top,new LinearLayout.LayoutParams(1,0,1));
        TextView t=title("서랍정리",36); t.setGravity(Gravity.CENTER); put(l,t,-1,60);
        TextView sub=body("휴대폰 파일을 종류별로 깔끔하게 정리합니다.",18); sub.setGravity(Gravity.CENTER); put(l,sub,-1,50);
        Button start=button("파일 정리 시작"); put(l,start,-1,62); start.setOnClickListener(v->showArea());
        Space bottom=new Space(this); l.addView(bottom,new LinearLayout.LayoutParams(1,0,1));
    }

    private void showArea(){
        base(); LinearLayout l=column(); root.addView(l,new FrameLayout.LayoutParams(-1,-1));
        put(l,title("정리할 영역을 선택하세요",29),-1,58);
        put(l,body("선택한 폴더와 하위 폴더를 검사합니다.",17),-1,34);
        put(l,body("JJY DATA는 자동 제외됩니다.",14),-1,32);

        CheckBox all=new CheckBox(this); all.setText("전체 내부 저장소"); all.setTextSize(18); all.setTextColor(TEXT); all.setChecked(allStorage); put(l,all,-1,54);
        String[] standard={"DCIM","Download","Pictures","Movies","Music","Documents"};
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        ArrayList<CheckBox> boxes=new ArrayList<>();
        File rootDir=Environment.getExternalStorageDirectory();
        for(String name:standard){ File f=new File(rootDir,name); if(f.isDirectory()){ CheckBox c=new CheckBox(this); c.setText(name); c.setTextSize(18); c.setTextColor(TEXT); c.setChecked(selectedRoots.contains(f)); boxes.add(c); list.addView(c,new LinearLayout.LayoutParams(-1,50)); c.setOnCheckedChangeListener((v,checked)->{ if(checked){ if(!selectedRoots.contains(f))selectedRoots.add(f);}else selectedRoots.remove(f); }); }}
        CheckBox other=new CheckBox(this); other.setText("기타 폴더 직접 선택"); other.setTextSize(18); other.setTextColor(TEXT); list.addView(other,new LinearLayout.LayoutParams(-1,58));
        other.setOnClickListener(v->{ if(other.isChecked()) pickFolder(); });
        ScrollView sv=new ScrollView(this); sv.addView(list); l.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        all.setOnCheckedChangeListener((v,checked)->{ allStorage=checked; if(checked){selectedRoots.clear(); for(CheckBox c:boxes)c.setEnabled(false);}else for(CheckBox c:boxes)c.setEnabled(true); });
        Button next=button("검사 시작"); put(l,next,-1,60); next.setOnClickListener(v->{ if(allStorage||!selectedRoots.isEmpty()) requestAccessThenScan(); else Toast.makeText(this,"정리할 영역을 하나 이상 선택하세요.",Toast.LENGTH_SHORT).show(); });
    }

    private void pickFolder(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); startActivityForResult(i,PICK_FOLDER); }
    @Override protected void onActivityResult(int r,int c,Intent d){ super.onActivityResult(r,c,d); if(r==PICK_FOLDER&&c==RESULT_OK&&d!=null){ File f=treeUriToFile(d.getData()); if(f!=null&&f.isDirectory()&&!f.getName().equalsIgnoreCase("JJY DATA")){ if(!selectedRoots.contains(f))selectedRoots.add(f); Toast.makeText(this,f.getName()+" 폴더를 추가했습니다.",Toast.LENGTH_SHORT).show(); } } }
    private File treeUriToFile(Uri uri){ try{ String id=DocumentsContract.getTreeDocumentId(uri); String[] p=id.split(":",2); if(p.length!=2||!p[0].equalsIgnoreCase("primary")) return null; String rel=p[1]; return rel.isEmpty()?Environment.getExternalStorageDirectory():new File(Environment.getExternalStorageDirectory(),rel); }catch(Exception e){return null;} }

    private void requestAccessThenScan(){
        if(Build.VERSION.SDK_INT>=30&&!Environment.isExternalStorageManager()){ startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName()))); Toast.makeText(this,"'모든 파일에 대한 접근 허용'을 켠 뒤 앱으로 돌아오면 검사가 시작됩니다.",Toast.LENGTH_LONG).show(); return; }
        startScan();
    }
    @Override protected void onResume(){ super.onResume(); if(root!=null && !items.isEmpty()) return; }

    private void startScan(){
        base(); LinearLayout l=column(); l.setGravity(Gravity.CENTER_HORIZONTAL); root.addView(l,new FrameLayout.LayoutParams(-1,-1));
        put(l,title("파일 검사",30),-1,60); put(l,body("선택한 영역의 파일을 검사하고 있습니다.",17),-1,50);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); put(l,bar,-1,18); TextView count=body("검사 준비 중…",16); count.setGravity(Gravity.CENTER); put(l,count,-1,46);
        TextView note=body("검사가 끝나면 바로 정리 미리보기로 이동합니다.",14); note.setGravity(Gravity.CENTER); put(l,note,-1,45);
        executor.submit(()->{
            ArrayList<Item> found=scanFiles(f->runOnUiThread(()->count.setText("검사 중: "+f)));
            runOnUiThread(()->{ items.clear(); items.addAll(found); showPreview(); });
        });
    }

    private interface Progress { void onFile(String name); }
    private ArrayList<Item> scanFiles(Progress progress){
        ArrayList<Item> out=new ArrayList<>(); ArrayList<File> roots=new ArrayList<>();
        File main=Environment.getExternalStorageDirectory();
        if(allStorage){ File[] fs=main.listFiles(); if(fs!=null)for(File f:fs)if(f.isDirectory()&&!f.getName().equalsIgnoreCase("JJY DATA")&&!f.getName().equalsIgnoreCase("Android"))roots.add(f); }
        else roots.addAll(selectedRoots);
        HashMap<String,Integer> dup=new HashMap<>();
        for(File r:roots) walk(r,"",out,dup,progress);
        HashMap<String,Integer> count=new HashMap<>(); for(Item x:out){String k=x.file.getName().toLowerCase(Locale.ROOT)+"|"+x.file.length();count.put(k,count.getOrDefault(k,0)+1);} for(Item x:out)x.duplicate=count.get(x.file.getName().toLowerCase(Locale.ROOT)+"|"+x.file.length())>1;
        return out;
    }
    private void walk(File dir,String rel,ArrayList<Item> out,HashMap<String,Integer> unused,Progress p){
        if(dir==null||!dir.isDirectory()||dir.getName().equalsIgnoreCase("JJY DATA"))return; File[] fs=dir.listFiles(); if(fs==null)return;
        for(File f:fs){ if(f.getName().equalsIgnoreCase("JJY DATA"))continue; if(f.isDirectory()){String nr=rel.isEmpty()?f.getName():rel+"/"+f.getName();walk(f,nr,out,unused,p);} else if(f.isFile()&&f.canRead()){String c=classify(f);if(c!=null){out.add(new Item(f,c,rel));p.onFile(f.getName());}} }
    }
    private String classify(File f){ String n=f.getName(), l=n.toLowerCase(Locale.ROOT), e=ext(l), b=baseName(l);
        if(e.equals("mp3"))return n.toUpperCase(Locale.ROOT).contains("MR")?"MR":"MP3";
        if(set("jpg","jpeg","gif","png","tif","tiff","bmp","webp","heic","heif","avif").contains(e))return"IMAGE";
        if(e.equals("pdf"))return b.matches(".*(b[1-6]|[1-6]b|#[1-6]|[1-6]#|_c)$")?"악보":"PDF";
        if(set("hwp","hwpx").contains(e))return"한글문서";
        if(set("doc","docx","xls","xlsx","ppt","pptx","csv","rtf","odt","ods","odp").contains(e))return"MS문서";
        if(set("zip","7z","rar","tar","gz","bz2","xz","tgz").contains(e))return"압축파일";
        if(set("dwg","dwf","dxf","stp","step","igs","iges","ipt","iam","stl","obj","3ds","3mf","fbx","skp").contains(e))return"캐드파일";
        if(set("avi","mp4","mkv","mov","wmv","flv","webm","m4v","3gp","mpeg","mpg","mts","m2ts","ts").contains(e))return"영상파일";
        return null;
    }
    private String ext(String s){int i=s.lastIndexOf('.');return i>=0?s.substring(i+1):"";} private String baseName(String s){int i=s.lastIndexOf('.');return i>0?s.substring(0,i):s;}
    private HashSet<String> set(String...a){return new HashSet<>(Arrays.asList(a));}

    private void showPreview(){
        base(); LinearLayout l=column(); root.addView(l,new FrameLayout.LayoutParams(-1,-1));
        put(l,title("정리 미리보기",30),-1,60); put(l,body("검사 완료: "+items.size()+"개 파일",18),-1,42);
        int d=0;for(Item x:items)if(x.duplicate)d++;
        put(l,body("중복 의심: "+d+"개  ·  체크를 해제한 파일은 정리하지 않습니다.",15),-1,55);
        Button result=button("검사 결과 및 대상 파일 보기");put(l,result,-1,58);result.setOnClickListener(v->showItemDialog());
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(18,14,18,14);card.setBackgroundColor(Color.WHITE);
        TextView a=body("정리 규칙",16);a.setTypeface(null,1);card.addView(a,new LinearLayout.LayoutParams(-1,34));
        card.addView(body("MP3 / MR / 이미지 / PDF / 악보 / 한글 / MS문서 / 압축 / CAD·3D / 영상",14));
        card.addView(body("같은 이름의 원본 폴더는 같은 목적지 폴더로 합쳐집니다.",14));
        l.addView(card,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,8,0,0);
        Button back=button("다시 선택");Button go=button("정리 실행");row.addView(back,new LinearLayout.LayoutParams(0,58,1));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(0,58,1);gp.leftMargin=8;row.addView(go,gp);l.addView(row);
        back.setOnClickListener(v->showArea());go.setOnClickListener(v->{ArrayList<Item> chosen=new ArrayList<>();for(Item x:items)if(x.selected)chosen.add(x);if(chosen.isEmpty())Toast.makeText(this,"정리할 파일을 하나 이상 선택하세요.",Toast.LENGTH_SHORT).show();else startMove(chosen);});
    }

    private void showItemDialog(){
        final AlertDialog dialog=new AlertDialog.Builder(this).setTitle("정리 대상 파일").create();
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);wrap.setPadding(10,0,10,0);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);CheckBox dup=new CheckBox(this);dup.setText("중복 의심만 보기");TextView stat=body("",13);top.addView(dup,new LinearLayout.LayoutParams(0,50,1));top.addView(stat,new LinearLayout.LayoutParams(-2,50));wrap.addView(top);
        ListView list=new ListView(this);wrap.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        CheckBoxAdapter adapter=new CheckBoxAdapter(dup);list.setAdapter(adapter);dup.setOnCheckedChangeListener((v,c)->adapter.notifyDataSetChanged());
        stat.setText(selectedCount()+" / "+items.size()+" 선택");
        Button close=new Button(this);close.setText("닫기");close.setAllCaps(false);close.setOnClickListener(v->dialog.dismiss());wrap.addView(close,new LinearLayout.LayoutParams(-1,54));
        dialog.setView(wrap);dialog.setOnShowListener(x->{WindowSize(dialog);});dialog.show();WindowSize(dialog);
    }
    private void WindowSize(AlertDialog d){if(d.getWindow()!=null){d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94),(int)(getResources().getDisplayMetrics().heightPixels*.78));}}
    private int selectedCount(){int n=0;for(Item x:items)if(x.selected)n++;return n;}
    private class CheckBoxAdapter extends BaseAdapter{
        final CheckBox filter;CheckBoxAdapter(CheckBox f){filter=f;}
        ArrayList<Item> visible(){ArrayList<Item> v=new ArrayList<>();for(Item x:items)if(!filter.isChecked()||x.duplicate)v.add(x);return v;}
        public int getCount(){return visible().size();}public Object getItem(int p){return visible().get(p);}public long getItemId(int p){return p;}
        public View getView(int p,View cv,ViewGroup parent){Item x=visible().get(p);LinearLayout r=new LinearLayout(MainActivity.this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(4,5,4,5);CheckBox c=new CheckBox(MainActivity.this);c.setText(x.file.getName());c.setTextSize(14);c.setTextColor(TEXT);c.setChecked(x.selected);r.addView(c,new LinearLayout.LayoutParams(-1,42));TextView info=body(x.category+"  →  JJY DATA/"+x.category+(x.relativeFolder.isEmpty()?"":"/"+x.relativeFolder),11);r.addView(info,new LinearLayout.LayoutParams(-1,34));if(x.duplicate){TextView dd=body("중복 의심",11);dd.setTextColor(Color.rgb(190,50,50));r.addView(dd,new LinearLayout.LayoutParams(-1,26));}c.setOnCheckedChangeListener((b,checked)->{x.selected=checked;});return r;}
    }

    private void startMove(ArrayList<Item> chosen){
        base();LinearLayout l=column();l.setGravity(Gravity.CENTER_HORIZONTAL);root.addView(l,new FrameLayout.LayoutParams(-1,-1));put(l,title("정리 실행",30),-1,60);TextView st=body("준비 중…",16);st.setGravity(Gravity.CENTER);put(l,st,-1,50);ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);put(l,bar,-1,18);TextView num=body("0 / "+chosen.size(),15);num.setGravity(Gravity.CENTER);put(l,num,-1,40);Button pause=button("일시중지");put(l,pause,-1,58);paused=false;
        pause.setOnClickListener(v->{paused=!paused;pause.setText(paused?"계속하기":"일시중지");});
        executor.submit(()->{ArrayList<Result> out=new ArrayList<>();for(int i=0;i<chosen.size();i++){while(paused&&!Thread.currentThread().isInterrupted()){try{Thread.sleep(120);}catch(Exception e){Thread.currentThread().interrupt();break;}}Item x=chosen.get(i);File dest=destination(x);boolean ok=moveFile(x.file,dest);out.add(new Result(x.file.getAbsolutePath(),dest.getAbsolutePath(),ok));int n=i+1;runOnUiThread(()->{bar.setProgress((int)(n*100f/chosen.size()));num.setText(n+" / "+chosen.size());st.setText(ok?"정리 중: "+x.file.getName():"실패: "+x.file.getName());});}results.clear();results.addAll(out);runOnUiThread(this::showDone);});
    }
    private File destination(Item x){File base=new File(Environment.getExternalStorageDirectory(),"JJY DATA"+File.separator+x.category);if(!x.relativeFolder.isEmpty())base=new File(base,x.relativeFolder);return unique(new File(base,x.file.getName()));}
    private File unique(File f){if(!f.exists())return f;File p=f.getParentFile();String n=f.getName();int dot=n.lastIndexOf('.');String b=dot>0?n.substring(0,dot):n,e=dot>0?n.substring(dot):"";int i=1;File c;do{c=new File(p,b+" ("+i+")"+e);i++;}while(c.exists());return c;}
    private boolean moveFile(File src,File dst){try{if(dst.getParentFile()!=null)dst.getParentFile().mkdirs();if(src.renameTo(dst))return true;copy(src,dst);return src.delete();}catch(Exception e){return false;}}
    private void copy(File s,File d)throws Exception{FileInputStream in=new FileInputStream(s);FileOutputStream out=new FileOutputStream(d);byte[] buf=new byte[1024*1024];int n;try{while((n=in.read(buf))>0)out.write(buf,0,n);}finally{in.close();out.close();}}

    private void showDone(){
        base();LinearLayout l=column();root.addView(l,new FrameLayout.LayoutParams(-1,-1));put(l,title("정리 완료",30),-1,60);int ok=0;for(Result r:results)if(r.ok)ok++;put(l,body("처리 파일: "+results.size()+"개",19),-1,44);put(l,body("완료: "+ok+"개   ·   실패: "+(results.size()-ok)+"개",17),-1,44);Button detail=button("정리 내역 보기");put(l,detail,-1,58);detail.setOnClickListener(v->showResultDialog());Space sp=new Space(this);l.addView(sp,new LinearLayout.LayoutParams(1,0,1));Button again=button("처음으로");put(l,again,-1,58);again.setOnClickListener(v->showHome());
    }
    private void showResultDialog(){AlertDialog d=new AlertDialog.Builder(this).setTitle("정리 내역").setView(resultListView()).setPositiveButton("닫기",null).create();d.setOnShowListener(x->WindowSize(d));d.show();WindowSize(d);}
    private View resultListView(){ListView v=new ListView(this);ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_2,android.R.id.text1){@Override public View getView(int p,View c,ViewGroup parent){View r=super.getView(p,c,parent);TextView t=r.findViewById(android.R.id.text1);TextView s=r.findViewById(android.R.id.text2);Result x=results.get(p);t.setText((x.ok?"완료":"실패")+"  "+new File(x.source).getName());s.setText(x.destination);return r;}};v.setAdapter(a);return v;}
}
