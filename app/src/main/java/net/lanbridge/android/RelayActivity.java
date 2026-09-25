package net.lanbridge.android;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.text.InputType;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;
import org.json.JSONArray;

/** File inbox UI. Credentials and network tasks are separate from VPN profiles. */
public final class RelayActivity extends Activity {
    private LinearLayout content,files;
    private TextView status,breadcrumb;
    private LoginStore store;
    private SharedPreferences preferences;
    private String path="",page="files";
    private List<RelayDav.Entry> entries=new ArrayList<>(),plan=new ArrayList<>();
    private long planTime;
    private String planIdentity;
    private RelayDav.Entry pendingDownload;
    private final ArrayList<Uri> incoming=new ArrayList<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService reads=Executors.newSingleThreadExecutor();
    private volatile boolean listing,reloadPending;
    private long lastRevision;
    private String filter=""; private int sort;
    private final Set<String> selected=new HashSet<>();
    private Button deleteSelected;
    private final Runnable refresh=new Runnable(){public void run(){if(status!=null)status.setText(RelayService.status());if(lastRevision!=RelayService.revision){lastRevision=RelayService.revision;if(page.equals("files"))load();if(page.equals("downloads"))downloads();}handler.postDelayed(this,500);}};
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;}
    private TextView text(String label,int size){TextView t=new TextView(this);t.setText(label);t.setTextSize(size);t.setTextColor(Color.rgb(32,46,68));t.setPadding(0,dp(8),0,dp(8));return t;}
    private Button button(LinearLayout parent,String name,Runnable action){Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(Color.rgb(56,108,244));android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(Color.rgb(238,243,255));bg.setCornerRadius(dp(10));b.setBackground(bg);b.setStateListAnimator(null);parent.addView(b,parent.getOrientation()==LinearLayout.HORIZONTAL?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->{try{action.run();}catch(Exception e){notice(e.getMessage());}});return b;}
    private LinearLayout toolbar(){LinearLayout row=new LinearLayout(this);content.addView(row);return row;}
    private void notice(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private boolean alive(){return !isFinishing()&&!isDestroyed();}
    private void ui(Runnable action){runOnUiThread(()->{if(alive())action.run();});}
    @Override public void onCreate(Bundle state){super.onCreate(state);store=new LoginStore(this,"relay-profile","relay-login-v1");preferences=getSharedPreferences("relay",0);if(state!=null){ArrayList<Uri> savedIncoming=state.getParcelableArrayList("incoming");if(savedIncoming!=null)incoming.addAll(savedIncoming);filter=state.getString("filter","");sort=state.getInt("sort",0);path=state.getString("path","");String saved=state.getString("download");if(saved!=null)pendingDownload=new RelayDav.Entry(saved,false,state.getLong("size"),state.getLong("modified"),state.getString("etag",""));}
        LinearLayout shell=column();shell.setBackgroundColor(Color.rgb(243,246,252));shell.setOnApplyWindowInsetsListener((v,i)->{shell.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        ScrollView scroll=new ScrollView(this);content=column();content.setPadding(dp(16),dp(12),dp(16),dp(20));scroll.addView(content);shell.addView(scroll);setContentView(shell);if(state==null)receive(getIntent());showFiles();if(!store.hasPassword())settings();else{load();sendIncoming();}}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);receive(i);path="";entries=new ArrayList<>();selected.clear();showFiles();if(!store.hasPassword())settings();else{load();sendIncoming();}}
    private void receive(Intent intent){
        String action=intent.getAction();
        if(!Intent.ACTION_SEND.equals(action)&&!Intent.ACTION_SEND_MULTIPLE.equals(action)&&!Intent.ACTION_VIEW.equals(action))return;
        int before=incoming.size();
        try{
            if(Intent.ACTION_VIEW.equals(action))accept(intent.getData());
            if(Intent.ACTION_SEND.equals(action))accept(intent.getParcelableExtra(Intent.EXTRA_STREAM));
            if(Intent.ACTION_SEND_MULTIPLE.equals(action)){ArrayList<Uri> values=intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);if(values!=null)for(Uri uri:values)accept(uri);}
            ClipData clip=intent.getClipData();if(clip!=null)for(int i=0;i<clip.getItemCount();i++)accept(clip.getItemAt(i).getUri());
        }catch(RuntimeException e){notice("未能读取分享文件，请从原应用重新分享");}
        intent.setAction(null);
        if(incoming.size()==before)notice("未收到可读取的文件，请使用原应用的文件分享功能");
    }
    private void sendIncoming(){if(incoming.isEmpty()||!store.hasPassword())return;List<Uri> values=new ArrayList<>(incoming);if(upload(values,"")){incoming.clear();showFiles();}}
    private void accept(Uri uri){if(uri!=null&&"content".equals(uri.getScheme())&&!incoming.contains(uri)&&incoming.size()<1000)incoming.add(uri);}
    private void header(String title){content.removeAllViews();button(content,page.equals("files")?"‹ 工具箱":"‹ 中转文件",()->{if(page.equals("files"))finish();else{showFiles();load();}});content.addView(text(title,25));}
    private RelayDav dav()throws Exception {String password=store.password(store.origin(),store.username());if(password.isEmpty())throw new IOException("请先保存 WebDAV 连接");return new RelayDav(store.origin(),store.scope(),store.username(),password);}
    private void showFiles(){page="files";header("文件中转站");LinearLayout options=toolbar();button(options,"连接设置",this::settings);button(options,"上传日志",this::logs);
        if(!incoming.isEmpty())button(content,"上传待传的 "+incoming.size()+" 个文件",this::sendIncoming);
        button(options,"已下载",this::downloads);
        breadcrumb=text(path.isEmpty()?"中转根目录":path,16);content.addView(breadcrumb);
        LinearLayout actions=toolbar();button(actions,"刷新",this::load);if(!path.isEmpty())button(content,"上一级",()->{path=RelayDav.parent(path);entries.clear();selected.clear();showFiles();load();});
        button(actions,"上传文件",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,10);});
        button(actions,"新建文件夹",()->{EditText name=new EditText(this);new AlertDialog.Builder(this).setTitle("文件夹名称").setView(name).setPositiveButton("创建",(d,w)->transfer("新建文件夹",client->{String n=RelayDav.path(name.getText().toString(),false);if(n.contains("/"))throw new IOException("请填写单层目录名");client.ensure(path.isEmpty()?n:path+"/"+n);})).setNegativeButton("取消",null).show();});
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("搜索当前目录");search.setContentDescription("搜索当前目录");search.setText(filter);content.addView(search);
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence x,int a,int c,int f){}public void onTextChanged(CharSequence x,int a,int b,int c){filter=x.toString();render();}public void afterTextChanged(android.text.Editable e){}});
        LinearLayout selection=toolbar();button(selection,"排序",()->new AlertDialog.Builder(this).setTitle("排序").setSingleChoiceItems(new String[]{"最新在前","最旧在前","名称","大小"},sort,(d,w)->{sort=w;render();d.dismiss();}).show());
        button(selection,"全选",()->{for(RelayDav.Entry e:visibleEntries())if(!e.directory)selected.add(e.path);render();});
        deleteSelected=button(selection,"删除所选",()->{List<RelayDav.Entry> chosen=new ArrayList<>();for(RelayDav.Entry e:entries)if(selected.contains(e.path))chosen.add(e);delete(chosen);});
        LinearLayout controls=toolbar();button(controls,"按时间清理",this::preview);button(controls,"取消传输",RelayService::cancel);status=text(RelayService.message,14);content.addView(status);files=column();content.addView(files);render();}
    private List<RelayDav.Entry> visibleEntries(){
        List<RelayDav.Entry> result=new ArrayList<>();for(RelayDav.Entry e:entries)if(e.name.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT)))result.add(e);
        Comparator<RelayDav.Entry> order=sort==2?Comparator.comparing(e->e.name.toLowerCase(Locale.ROOT)):sort==3?Comparator.comparingLong(e->-e.size):sort==1?Comparator.comparingLong(e->e.modified):Comparator.comparingLong(e->-e.modified);
        result.sort(Comparator.comparing((RelayDav.Entry e)->!e.directory).thenComparing(order));return result;
    }
    private String size(long n){return n<1024?n+" B":n<1048576?String.format(Locale.ROOT,"%.1f KB",n/1024.0):String.format(Locale.ROOT,"%.1f MB",n/1048576.0);}
    private void render(){
        if(!page.equals("files")||files==null)return;files.removeAllViews();breadcrumb.setText(path.isEmpty()?"中转根目录":path);
        if(deleteSelected!=null){deleteSelected.setText("删除所选 ("+selected.size()+")");deleteSelected.setEnabled(!selected.isEmpty());}
        List<RelayDav.Entry> visible=visibleEntries();if(visible.isEmpty())files.addView(text(filter.isEmpty()?"当前目录没有文件":"没有匹配的文件",16));
        for(RelayDav.Entry e:visible){LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(0,dp(6),0,dp(6));files.addView(row);
            if(!e.directory){CheckBox check=new CheckBox(this);check.setContentDescription("选择 "+e.name);check.setChecked(selected.contains(e.path));row.addView(check);check.setOnCheckedChangeListener((b,on)->{if(on)selected.add(e.path);else selected.remove(e.path);deleteSelected.setText("删除所选 ("+selected.size()+")");deleteSelected.setEnabled(!selected.isEmpty());});}
            LinearLayout details=column();row.addView(details,new LinearLayout.LayoutParams(0,-2,1));button(details,(e.directory?"文件夹 · ":"")+e.name,()->{if(e.directory){path=e.path;entries=new ArrayList<>();selected.clear();filter="";showFiles();load();}else actions(e);});
            if(!e.directory)details.addView(text(size(e.size)+" · "+(e.modified==0?"时间未知":android.text.format.DateFormat.format("MM/dd HH:mm",e.modified)),13));
        }
    }
    private void load(){if(!store.hasPassword())return;if(listing){reloadPending=true;return;}listing=true;final String requested=path;reads.execute(()->{try(RelayDav client=dav()){List<RelayDav.Entry> value=client.list(requested);ui(()->{if(path.equals(requested)){entries=value;Set<String> present=new HashSet<>();for(RelayDav.Entry e:value)present.add(e.path);selected.retainAll(present);render();}});}catch(Exception e){ui(()->notice(e.getMessage()));}finally{listing=false;if(reloadPending){reloadPending=false;ui(this::load);}}});}
    private EditText field(String label,String value,boolean secret){content.addView(text(label,15));EditText e=new EditText(this);e.setSingleLine(true);e.setContentDescription(label);e.setText(value);if(secret)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);content.addView(e);return e;}
    private void settings(){page="settings";header("连接设置");if(!incoming.isEmpty())content.addView(text("已接收 "+incoming.size()+" 个文件；连接测试成功后自动上传到中转根目录。",14));EditText address=field("飞牛 WebDAV 地址",store.origin(),false),folder=field("远端缓存目录（共享名/文件中转站）",store.scope().equals("lan")?"文件中转站":store.scope(),false),username=field("用户名",store.username(),false),password=field(store.hasPassword()?"密码（留空保留已保存密码）":"密码","",true);
        button(content,"保存并测试连接",()->{if(RelayService.busy){notice("请等待当前传输结束");return;}try{String url=RelayDav.endpoint(address.getText().toString()).toString(),dir=RelayDav.path(folder.getText().toString().trim(),false),user=username.getText().toString().trim(),pass=password.getText().toString();if(pass.isEmpty())pass=store.password(url,user);if(pass.isEmpty()||user.isEmpty())throw new IOException("请输入用户名和密码");store.save(url,user,pass,dir);plan.clear();path="";transfer("测试连接",client->{client.ensure("");ui(this::sendIncoming);});showFiles();}catch(Exception e){notice(e.getMessage());}});
        content.addView(text("下载时由系统选择保存位置；打开或分享使用本机缓存。账号密码只在本机加密保存。",14));}
    private interface Operation {void run(RelayDav client)throws Exception;}
    private boolean transfer(String label,Operation operation){return transfer(label,operation,Collections.emptyList());}
    private boolean transfer(String label,Operation operation,List<Uri> grants){return transfer(label,operation,grants,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
    private boolean transfer(String label,Operation operation,List<Uri> grants,int flags){
        try{
            // Snapshot the destination before queuing; later settings edits cannot redirect a transfer.
            final String url=store.origin(),folder=store.scope(),user=store.username(),password=store.password(url,user);
            if(password.isEmpty())throw new IOException("请先保存 WebDAV 连接");
            RelayService.submit(this,()->{try(RelayDav client=new RelayDav(url,folder,user,password)){RelayService.bind(client);RelayService.message=label;operation.run(client);RelayService.message=label+"：已完成";if(label.startsWith("上传"))log(label+"：已完成");}catch(Exception e){if(label.startsWith("上传"))log(label+"："+e.getMessage());throw e;}},grants,flags);
            return true;
        }catch(Exception e){notice(e.getMessage());return false;}
    }
    private void log(String text){synchronized(RelayActivity.class){try{JSONArray a=new JSONArray(preferences.getString("logs","[]")),b=new JSONArray();b.put(android.text.format.DateFormat.format("MM/dd HH:mm",System.currentTimeMillis())+" · "+text);for(int i=0;i<Math.min(99,a.length());i++)b.put(a.getString(i));preferences.edit().putString("logs",b.toString()).apply();}catch(Exception ignored){}}}
    private void logs(){page="logs";header("上传日志");try{JSONArray a=new JSONArray(preferences.getString("logs","[]"));if(a.length()==0)content.addView(text("暂无上传记录",16));for(int i=0;i<a.length();i++)content.addView(text(a.getString(i),15));}catch(Exception e){notice(e.getMessage());}button(content,"刷新日志",this::logs);}
    private String name(Uri uri)throws Exception {
        String value=null;
        try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){int column=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(column>=0)value=cursor.getString(column);}
        }catch(IllegalArgumentException|UnsupportedOperationException ignored){}
        if(value==null||value.isEmpty()){
            value=uri.getLastPathSegment();
            if(value!=null){int split=Math.max(value.lastIndexOf('/'),value.lastIndexOf(':'));value=value.substring(split+1);}
        }
        if(value==null||value.isEmpty()){
            String extension=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(getContentResolver().getType(uri));
            value="共享文件-"+UUID.randomUUID()+(extension==null?"":"."+extension);
        }
        value=RelayDav.path(value,false);if(value.contains("/"))throw new IOException("文件名无效");return value;
    }
    private boolean upload(List<Uri> uris,String target){return transfer("上传 "+uris.size()+" 个文件",client->{int count=0;List<String> errors=new ArrayList<>();for(Uri uri:uris){RelayService.check();String label="文件";File temporary=null;try{label=name(uri);temporary=File.createTempFile("relay-upload-",".part",getCacheDir());try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temporary)){if(in==null)throw new IOException("无法读取文件");byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){RelayService.check();if(getCacheDir().getUsableSpace()<b.length*2L)throw new IOException("本机缓存空间不足");out.write(b,0,n);}}final String filename=label;client.upload(temporary,target.isEmpty()?label:target+"/"+label,(done,total)->{RelayService.check();RelayService.message="上传 "+filename+" · "+done+" / "+total+" 字节";});count++;log("已上传："+label);}catch(Exception e){errors.add(label+"："+e.getMessage());log(label+"："+e.getMessage());}finally{if(temporary!=null)temporary.delete();}}if(!errors.isEmpty())throw new IOException("已上传 "+count+" 个，"+errors.size()+" 项未完成，请查看上传日志");},uris);}
    private void actions(RelayDav.Entry entry){new AlertDialog.Builder(this).setTitle(entry.name).setItems(new String[]{"打开","下载到…","分享给其他应用","删除远端文件"},(d,which)->{if(which==1){pendingDownload=entry;startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(RelayProvider.mime(entry.name)).putExtra(Intent.EXTRA_TITLE,entry.name),11);}else if(which==3)delete(Collections.singletonList(entry));else download(entry,null,which==2);}).show();}
    private void openCached(File file,boolean share){
        Uri uri=new Uri.Builder().scheme("content").authority(getPackageName()+".relay").appendPath(file.getParentFile().getName()).appendPath(file.getName()).build();
        try{
            Intent intent=new Intent(share?Intent.ACTION_SEND:Intent.ACTION_VIEW).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if(share){intent.setType(RelayProvider.mime(file.getName())).putExtra(Intent.EXTRA_STREAM,uri);}else intent.setDataAndType(uri,RelayProvider.mime(file.getName()));
            intent.setClipData(ClipData.newRawUri(file.getName(),uri));
            Intent chooser=Intent.createChooser(intent,share?"分享文件":"打开文件");
            chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,new ComponentName[]{new ComponentName(this,RelayActivity.class)});
            startActivity(chooser);
        }catch(ActivityNotFoundException e){notice("没有可打开此类型文件的应用；可在已下载中分享或另存");}
    }
    private void download(RelayDav.Entry entry,Uri destination,boolean share){transfer("下载 "+entry.name,client->{
        File file=RelayCache.create(getCacheDir(),entry.name),partial=File.createTempFile("relay-download-",".part",getCacheDir());
        try{client.download(entry,partial,(done,total)->{RelayService.check();if(getCacheDir().getUsableSpace()<131072)throw new IOException("本机缓存空间不足");RelayService.message="下载 "+entry.name+" · "+size(done)+" / "+size(total);});
            if(!partial.renameTo(file))throw new IOException("无法保存下载缓存");
            if(destination!=null){export(file,destination);RelayCache.remove(getCacheDir(),file);}else ui(()->openCached(file,share));
        }finally{partial.delete();if(!file.exists())file.getParentFile().delete();}
    },destination==null?Collections.emptyList():Collections.singletonList(destination),Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}
    private void export(File file,Uri destination)throws Exception {try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(destination,"w")){if(out==null)throw new IOException("无法写入保存位置");byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){RelayService.check();out.write(bytes,0,n);}}}
    private void downloads(){page="downloads";header("已下载");content.addView(text("本机缓存副本，打开或分享不必重新下载。删除缓存不会删除远端文件。",14));
        try{List<File> cached=RelayCache.list(getCacheDir());if(cached.isEmpty())content.addView(text("暂无下载文件",16));for(File file:cached){button(content,file.getName(),()->new AlertDialog.Builder(this).setTitle(file.getName()).setItems(new String[]{"打开","分享给其他应用","删除本机缓存"},(d,w)->{if(w<2)openCached(file,w==1);else new AlertDialog.Builder(this).setTitle("删除本机缓存？").setMessage(file.getName()+"，远端文件保留。").setNegativeButton("取消",null).setPositiveButton("删除",(a,b)->{try{RelayCache.remove(getCacheDir(),file);downloads();}catch(Exception e){notice(e.getMessage());}}).show();}).show());content.addView(text(size(file.length())+" · "+android.text.format.DateFormat.format("MM/dd HH:mm",file.lastModified()),13));}}
        catch(Exception e){notice(e.getMessage());}
    }
    private void delete(List<RelayDav.Entry> chosen){if(chosen.isEmpty())return;
        for(RelayDav.Entry e:chosen)if(e.directory||!RelayDav.strong(e.etag)){notice("包含无法安全删除的文件，请刷新或在飞牛中管理");return;}
        StringBuilder names=new StringBuilder("永久删除远端 "+chosen.size()+" 个文件；本机下载副本保留。\n");for(int i=0;i<Math.min(20,chosen.size());i++)names.append(chosen.get(i).name).append('\n');
        new AlertDialog.Builder(this).setTitle("确认删除").setMessage(names).setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->transfer("删除文件",client->{int removed=0;for(RelayDav.Entry e:chosen){RelayService.check();if(client.deleteUnchanged(e))removed++;}final int count=removed;ui(()->notice("已删除 "+count+" 个，跳过 "+(chosen.size()-count)+" 个已变化文件"));})).show();
    }
    private void preview(){new AlertDialog.Builder(this).setTitle("清理超过多久未修改的文件？").setItems(new String[]{"1 天","1 周","30 天","90 天"},(d,index)->transfer("清理预览",client->{List<RelayDav.Entry> value=client.cleanupPreview(new int[]{1,7,30,90}[index]);plan=value;planTime=System.currentTimeMillis();planIdentity=store.origin()+store.username()+store.scope();ui(()->{StringBuilder names=new StringBuilder("将永久删除 "+value.size()+" 个文件，保留文件夹；缺少时间或强 ETag 的文件不会删除。\n");for(int i=0;i<Math.min(50,value.size());i++)names.append(value.get(i).path).append('\n');new AlertDialog.Builder(this).setTitle("清理预览").setMessage(names).setNegativeButton("取消",null).setPositiveButton("确认清理",(a,b)->{if(value.isEmpty())return;if(System.currentTimeMillis()-planTime>600000||!planIdentity.equals(store.origin()+store.username()+store.scope())){notice("预览已过期，请重新预览");return;}transfer("清理",remote->{int n=0;for(RelayDav.Entry e:value){RelayService.check();if(remote.deleteUnchanged(e))n++;}final int removed=n;ui(()->notice("已清理 "+removed+" 个，跳过 "+(value.size()-removed)+" 个已变化文件"));});}).show();});})).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;if(request==10){List<Uri> values=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)values.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)values.add(data.getData());if(!values.isEmpty())upload(values,path);}else if(request==11&&pendingDownload!=null&&data.getData()!=null){download(pendingDownload,data.getData(),false);pendingDownload=null;}}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("path",path);state.putString("filter",filter);state.putInt("sort",sort);state.putParcelableArrayList("incoming",incoming);if(pendingDownload!=null){state.putString("download",pendingDownload.path);state.putLong("size",pendingDownload.size);state.putLong("modified",pendingDownload.modified);state.putString("etag",pendingDownload.etag);}}
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){reads.shutdown();super.onDestroy();}
    @Override public void onBackPressed(){if(!page.equals("files")){showFiles();load();}else if(!path.isEmpty()){path=RelayDav.parent(path);entries.clear();selected.clear();showFiles();load();}else super.onBackPressed();}
}
