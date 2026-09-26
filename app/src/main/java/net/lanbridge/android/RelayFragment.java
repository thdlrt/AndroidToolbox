package net.lanbridge.android;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;
import org.json.JSONArray;

/** File inbox UI. Credentials and network tasks are separate from VPN profiles. */
public final class RelayFragment extends ToolFragment {
    private LinearLayout content,files,chrome,footer,selectionBar,transferBar;
    private ScrollView scroll;
    private TextView status,breadcrumb;
    private LoginStore store;
    private SharedPreferences preferences;
    private String path="",page="files";
    private List<RelayDav.Entry> entries=new ArrayList<>(),plan=new ArrayList<>();
    private RelayDav.Entry pendingDownload;
    private final ArrayList<Uri> incoming=new ArrayList<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService reads=Executors.newSingleThreadExecutor();
    private volatile boolean listing,reloadPending;
    private long lastRevision,feedbackUntil;
    private String filter=""; private int sort;
    private final Set<String> selected=new HashSet<>();
    private boolean selecting;
    private String connectionIdentity="",loadError="";
    private TextView selectionCount;
    private View cancelTransfer;
    private final Runnable refresh=new Runnable(){public void run(){
        if(lastRevision!=RelayService.revision){lastRevision=RelayService.revision;feedbackUntil=SystemClock.uptimeMillis()+4000;if(page.equals("files"))load();if(page.equals("downloads"))downloads();}
        if(status!=null){ToolUi.update(status,RelayService.status());transferBar.setVisibility(RelayService.busy||SystemClock.uptimeMillis()<feedbackUntil||RelayService.message.startsWith("操作未完成")?View.VISIBLE:View.GONE);cancelTransfer.setVisibility(RelayService.busy?View.VISIBLE:View.GONE);}
        handler.postDelayed(this,500);
    }};
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout c=new LinearLayout(context());c.setOrientation(LinearLayout.VERTICAL);return c;}
    private TextView text(String label,int size){TextView t=new TextView(context());t.setText(label);t.setTextSize(size);t.setTextColor(Color.rgb(32,46,68));t.setPadding(0,dp(8),0,dp(8));return t;}
    private Button button(LinearLayout parent,String name,Runnable action){Button b=ToolUi.button(context(),name,false,()->{try{action.run();}catch(Exception e){notice(e.getMessage());}});LinearLayout.LayoutParams lp=parent.getOrientation()==LinearLayout.HORIZONTAL?new LinearLayout.LayoutParams(0,dp(48),1):new LinearLayout.LayoutParams(-1,dp(48));lp.topMargin=dp(8);parent.addView(b,lp);return b;}
    private LinearLayout toolbar(){LinearLayout row=new LinearLayout(context());content.addView(row);return row;}
    private void notice(String message){Toast.makeText(context(),message,Toast.LENGTH_LONG).show();}
    private boolean alive(){return isAdded()&&!context().isFinishing()&&!context().isDestroyed()&&!isRemoving();}
    private void ui(Runnable action){runOnUiThread(()->{if(alive())action.run();});}
    @Override protected android.view.View createContent(Bundle state){try{WebDavSettings.migrate(context());}catch(Exception e){notice(e.getMessage());}store=WebDavSettings.store(context());preferences=getSharedPreferences("relay",0);connectionIdentity=WebDavSettings.identity(context());if(state!=null){ArrayList<Uri> savedIncoming=state.getParcelableArrayList("incoming");if(savedIncoming!=null)incoming.addAll(savedIncoming);filter=state.getString("filter","");sort=state.getInt("sort",0);path=state.getString("path","");page=state.getString("page","files");selecting=state.getBoolean("selecting");ArrayList<String> savedSelected=state.getStringArrayList("selected");if(savedSelected!=null)selected.addAll(savedSelected);String saved=state.getString("download");if(saved!=null)pendingDownload=new RelayDav.Entry(saved,false,state.getLong("size"),state.getLong("modified"),state.getString("etag",""));}
        LinearLayout shell=column();shell.setBackgroundColor(ToolUi.BG);chrome=column();chrome.setPadding(dp(20),dp(12),dp(20),dp(8));shell.addView(chrome);
        scroll=new ScrollView(context());scroll.setFillViewport(true);content=column();content.setPadding(dp(20),dp(8),dp(20),dp(20));scroll.addView(content);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));footer=column();footer.setPadding(dp(20),0,dp(20),dp(12));shell.addView(footer);if(state==null)receive(getIntent());String restoredPage=page;showFiles();if(!store.hasPassword()||restoredPage.equals("settings"))settings();else if(restoredPage.equals("downloads"))downloads();else if(restoredPage.equals("logs"))logs();else load();sendIncoming();return shell;}
    void receiveIntent(Intent i){receive(i);path="";filter="";selecting=false;entries=new ArrayList<>();selected.clear();showFiles();if(!store.hasPassword())settings();else{load();sendIncoming();}}
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
    private void header(String title){content.removeAllViews();chrome.removeAllViews();footer.removeAllViews();status=null;selectionBar=null;scroll.scrollTo(0,0);LinearLayout row=new LinearLayout(context());row.setGravity(Gravity.CENTER_VERTICAL);row.addView(ToolUi.iconButton(context(),"back",page.equals("files")?"返回工具箱":"返回中转文件",()->{if(page.equals("files"))closePage();else{showFiles();load();}}));TextView heading=ToolUi.text(context(),title,23,ToolUi.INK);heading.setTypeface(null,android.graphics.Typeface.BOLD);row.addView(heading,new LinearLayout.LayoutParams(0,-2,1));chrome.addView(row);if(page.equals("files"))row.addView(ToolUi.iconButton(context(),"more","更多操作",()->more(row)));}
    private RelayDav dav()throws Exception {String password=store.password(store.origin(),store.username());if(password.isEmpty())throw new IOException("请在设置中配置 WebDAV 连接");return new RelayDav(store.origin(),WebDavSettings.path(context()),store.username(),password);}
    private void showFiles(){page="files";header("文件中转站");
        LinearLayout tabs=new LinearLayout(context());tabs.setGravity(Gravity.CENTER_VERTICAL);Button remote=ToolUi.button(context(),"中转文件",false,()->{});tabs.addView(remote,new LinearLayout.LayoutParams(0,dp(44),1));Button local=ToolUi.button(context(),"已下载",false,this::downloads);ToolUi.ripple(local,Color.TRANSPARENT,12);local.setTextColor(ToolUi.MUTED);tabs.addView(local,new LinearLayout.LayoutParams(0,dp(44),1));chrome.addView(tabs);
        LinearLayout searchRow=new LinearLayout(context());searchRow.setGravity(Gravity.CENTER_VERTICAL);searchRow.setPadding(dp(12),0,dp(4),0);searchRow.setBackground(ToolUi.box(context(),0xffeaf0f7,14));searchRow.addView(ToolUi.icon(context(),"search",ToolUi.MUTED),new LinearLayout.LayoutParams(dp(20),dp(20)));
        EditText search=new EditText(context());search.setSingleLine(true);search.setTextSize(15);search.setBackgroundColor(Color.TRANSPARENT);search.setHint("搜索当前目录");search.setContentDescription("搜索当前目录");search.setText(filter);search.setPadding(dp(10),0,dp(6),0);searchRow.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams searchLp=new LinearLayout.LayoutParams(-1,dp(48));searchLp.topMargin=dp(16);chrome.addView(searchRow,searchLp);
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence x,int a,int c,int f){}public void onTextChanged(CharSequence x,int a,int b,int c){filter=x.toString();render();}public void afterTextChanged(android.text.Editable e){}});
        LinearLayout location=new LinearLayout(context());location.setGravity(Gravity.CENTER_VERTICAL);if(!path.isEmpty())location.addView(ToolUi.iconButton(context(),"back","上一级",this::up));breadcrumb=ToolUi.text(context(),path.isEmpty()?"中转根目录":path,13,ToolUi.MUTED);breadcrumb.setMaxLines(2);breadcrumb.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);location.addView(breadcrumb,new LinearLayout.LayoutParams(0,-2,1));location.addView(ToolUi.iconButton(context(),"refresh","刷新",this::load));chrome.addView(location);
        if(!incoming.isEmpty())button(content,"上传待传的 "+incoming.size()+" 个文件",this::sendIncoming);
        files=column();files.setBackground(ToolUi.box(context(),Color.WHITE,18));content.addView(files);
        LinearLayout primary=new LinearLayout(context());Button upload=ToolUi.button(context(),"上传文件",true,this::pickUpload);primary.addView(upload,new LinearLayout.LayoutParams(-1,dp(50)));footer.addView(primary);
        selectionBar=new LinearLayout(context());selectionBar.setGravity(Gravity.CENTER_VERTICAL);selectionBar.addView(ToolUi.iconButton(context(),"close","取消选择",()->{selecting=false;selected.clear();render();}));selectionCount=ToolUi.text(context(),"",14,ToolUi.INK);selectionBar.addView(selectionCount,new LinearLayout.LayoutParams(0,-2,1));selectionBar.addView(ToolUi.iconButton(context(),"check","全选当前目录文件",()->{for(RelayDav.Entry e:visibleEntries())if(!e.directory)selected.add(e.path);render();}));selectionBar.addView(ToolUi.iconButton(context(),"trash","删除所选",()->{List<RelayDav.Entry> chosen=new ArrayList<>();for(RelayDav.Entry e:entries)if(selected.contains(e.path))chosen.add(e);delete(chosen);}));footer.addView(selectionBar);
        selectionBar.setTag(primary);
        transferBar=new LinearLayout(context());transferBar.setGravity(Gravity.CENTER_VERTICAL);status=ToolUi.text(context(),RelayService.status(),12,ToolUi.MUTED);status.setMaxLines(2);status.setPadding(0,dp(12),0,dp(8));status.setOnClickListener(v->logs());transferBar.addView(status,new LinearLayout.LayoutParams(0,-2,1));cancelTransfer=ToolUi.iconButton(context(),"close","取消传输",RelayService::cancel);transferBar.addView(cancelTransfer);transferBar.setVisibility(View.GONE);footer.addView(transferBar);render();}
    private void pickUpload(){if(!store.hasPassword()){settings();return;}startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true),10);}
    private void up(){path=RelayDav.parent(path);entries.clear();selected.clear();selecting=false;filter="";showFiles();load();}
    private void more(View anchor){PopupMenu menu=new PopupMenu(context(),anchor,Gravity.END);String[] labels={"排序","选择文件","新建文件夹","按时间清理","上传日志","中转目录"};for(int i=0;i<labels.length;i++)menu.getMenu().add(0,i,i,labels[i]);menu.setOnMenuItemClickListener(item->{switch(item.getItemId()){
        case 0:new AlertDialog.Builder(context()).setTitle("排序").setSingleChoiceItems(new String[]{"最新在前","最旧在前","名称","大小"},sort,(d,w)->{sort=w;render();d.dismiss();}).show();break;
        case 1:selecting=true;render();break;case 2:newFolder();break;case 3:preview();break;case 4:logs();break;case 5:settings();break;
    }return true;});menu.show();}
    private void newFolder(){EditText name=new EditText(context());name.setSingleLine(true);name.setHint("文件夹名称");AlertDialog dialog=new AlertDialog.Builder(context()).setTitle("新建文件夹").setView(name).setPositiveButton("创建",null).setNegativeButton("取消",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{try{String n=RelayDav.path(name.getText().toString().trim(),false);if(n.contains("/"))throw new IOException("请填写单层目录名");final String target=path.isEmpty()?n:path+"/"+n;if(transfer("新建文件夹",client->client.ensure(target)))dialog.dismiss();}catch(Exception e){name.setError(e.getMessage());}}));dialog.show();}
    private List<RelayDav.Entry> visibleEntries(){
        List<RelayDav.Entry> result=new ArrayList<>();for(RelayDav.Entry e:entries)if(e.name.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT)))result.add(e);
        Comparator<RelayDav.Entry> order=sort==2?Comparator.comparing(e->e.name.toLowerCase(Locale.ROOT)):sort==3?Comparator.comparingLong(e->-e.size):sort==1?Comparator.comparingLong(e->e.modified):Comparator.comparingLong(e->-e.modified);
        result.sort(Comparator.comparing((RelayDav.Entry e)->!e.directory).thenComparing(order));return result;
    }
    private String size(long n){return n<1024?n+" B":n<1048576?String.format(Locale.ROOT,"%.1f KB",n/1024.0):String.format(Locale.ROOT,"%.1f MB",n/1048576.0);}
    private void render(){
        if(!page.equals("files")||files==null)return;files.removeAllViews();breadcrumb.setText(path.isEmpty()?"中转根目录":path);
        selectionBar.setVisibility(selecting?View.VISIBLE:View.GONE);((View)selectionBar.getTag()).setVisibility(selecting?View.GONE:View.VISIBLE);selectionCount.setText("已选 "+selected.size()+" 项");
        List<RelayDav.Entry> visible=visibleEntries();if(visible.isEmpty()){LinearLayout empty=column();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(24),dp(48),dp(24),dp(48));empty.addView(ToolUi.icon(context(),loadError.isEmpty()?"folder":"cloud",ToolUi.MUTED),new LinearLayout.LayoutParams(dp(44),dp(44)));TextView title=text(!loadError.isEmpty()?"暂时无法读取文件":listing?"正在加载…":!store.hasPassword()?"连接你的 WebDAV":filter.isEmpty()?"还没有中转文件":"没有匹配的文件",17);title.setGravity(Gravity.CENTER);empty.addView(title);TextView detail=text(!loadError.isEmpty()?loadError:listing?"":!store.hasPassword()?"在设置中配置一次即可使用":filter.isEmpty()?"上传文件，便可在其他设备接着使用":"试试其他文件名",13);detail.setTextColor(ToolUi.MUTED);detail.setGravity(Gravity.CENTER);empty.addView(detail);if(!loadError.isEmpty())button(empty,"重试",this::load);else if(!store.hasPassword())button(empty,"配置连接",()->startActivity(new Intent(context(),WebDavActivity.class)));files.addView(empty);}
        for(RelayDav.Entry e:visible){LinearLayout row=new LinearLayout(context());row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(80));row.setPadding(dp(12),dp(10),dp(8),dp(10));ToolUi.ripple(row,selected.contains(e.path)?0xffedf2ff:Color.WHITE,14);files.addView(row);
            if(selecting&&!e.directory){CheckBox check=new CheckBox(context());check.setContentDescription("选择 "+e.name);check.setChecked(selected.contains(e.path));row.addView(check,new LinearLayout.LayoutParams(dp(48),dp(48)));check.setOnCheckedChangeListener((b,on)->{if(on)selected.add(e.path);else selected.remove(e.path);render();});}
            else{String glyph=e.directory?"folder":RelayProvider.mime(e.name).startsWith("image/")?"image":"file";ImageView icon=ToolUi.icon(context(),glyph,e.directory?0xffb47c26:ToolUi.BLUE);icon.setPadding(dp(10),dp(10),dp(10),dp(10));icon.setBackground(ToolUi.box(context(),e.directory?0xfffff4df:0xffedf2ff,12));row.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(48)));}
            LinearLayout details=column();details.setPadding(dp(12),0,dp(4),0);row.addView(details,new LinearLayout.LayoutParams(0,-2,1));TextView name=ToolUi.text(context(),e.name,15,ToolUi.INK);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);details.addView(name);TextView meta=ToolUi.text(context(),e.directory?"文件夹":size(e.size)+" · "+(e.modified==0?"时间未知":android.text.format.DateFormat.format("MM/dd HH:mm",e.modified)),12,ToolUi.MUTED);meta.setPadding(0,dp(6),0,0);details.addView(meta);
            row.setContentDescription((e.directory?"打开文件夹 ":"打开文件 ")+e.name);row.setFocusable(true);row.setOnClickListener(v->{if(e.directory){path=e.path;entries=new ArrayList<>();selected.clear();selecting=false;filter="";showFiles();load();}else if(selecting){if(!selected.add(e.path))selected.remove(e.path);render();}else download(e,null,false);});row.setOnLongClickListener(v->{if(e.directory)return false;selecting=true;selected.add(e.path);render();return true;});
            if(!e.directory&&!selecting)row.addView(ToolUi.iconButton(context(),"more","文件操作 "+e.name,()->actions(e)));
            View divider=new View(context());divider.setBackgroundColor(0xffedf1f6);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.leftMargin=dp(68);lp.rightMargin=dp(16);files.addView(divider,lp);
        }
    }
    private void load(){if(!store.hasPassword()||!alive())return;if(listing){reloadPending=true;return;}listing=true;loadError="";render();final String requested=path,identity=WebDavSettings.identity(context());final RelayDav client;try{client=dav();}catch(Exception e){listing=false;loadError=e.getMessage();render();return;}reads.execute(()->{try(RelayDav remote=client){List<RelayDav.Entry> value=remote.list(requested);ui(()->{if(path.equals(requested)&&identity.equals(WebDavSettings.identity(context()))){entries=value;Set<String> present=new HashSet<>();for(RelayDav.Entry e:value)present.add(e.path);selected.retainAll(present);loadError="";}});}catch(Exception e){ui(()->{if(path.equals(requested)&&identity.equals(WebDavSettings.identity(context()))){loadError=e.getMessage();if(!entries.isEmpty())notice(loadError);}});}finally{ui(()->{listing=false;render();if(reloadPending){reloadPending=false;load();}});}});}
    private EditText field(String label,String value,boolean secret){content.addView(text(label,15));EditText e=new EditText(context());e.setSingleLine(true);e.setContentDescription(label);e.setText(value);if(secret)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);content.addView(e);return e;}
    private void settings(){page="settings";header("中转目录");if(!incoming.isEmpty())content.addView(text("已接收 "+incoming.size()+" 个文件，配置后自动上传。",14));LinearLayout card=column();card.setPadding(dp(20),dp(16),dp(20),dp(20));card.setBackground(ToolUi.box(context(),Color.WHITE,18));content.addView(card);card.addView(ToolUi.icon(context(),"cloud",ToolUi.BLUE),new LinearLayout.LayoutParams(dp(32),dp(32)));card.addView(text(store.hasPassword()?"使用设置中的 WebDAV":"尚未配置 WebDAV",18));TextView hint=text("地址、账号和密码与全局设置共用。",14);hint.setTextColor(ToolUi.MUTED);card.addView(hint);button(card,"管理 WebDAV 连接",()->startActivity(new Intent(context(),WebDavActivity.class)));
        EditText folder=ToolUi.field(context(),content,"远端中转路径",WebDavSettings.path(context()),false);folder.setId(303);folder.setHint("共享名/文件中转站");content.addView(text("相对于 WebDAV 地址，例如 shared/文件中转站。",13));button(content,"保存并打开",()->{try{String dir=RelayDav.path(folder.getText().toString().trim(),false);preferences.edit().putString("remote_path",dir).apply();plan.clear();path="";entries.clear();selected.clear();connectionIdentity=WebDavSettings.identity(context());if(!store.hasPassword()){startActivity(new Intent(context(),WebDavActivity.class));return;}showFiles();transfer("测试连接",client->{client.ensure("");ui(this::sendIncoming);});}catch(Exception e){folder.setError(e.getMessage());}});}
    private interface Operation {void run(RelayDav client)throws Exception;}
    private boolean transfer(String label,Operation operation){return transfer(label,operation,Collections.emptyList());}
    private boolean transfer(String label,Operation operation,List<Uri> grants){return transfer(label,operation,grants,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
    private boolean transfer(String label,Operation operation,List<Uri> grants,int flags){
        try{
            // Snapshot the destination before queuing; later settings edits cannot redirect a transfer.
            final String url=store.origin(),folder=WebDavSettings.path(context()),user=store.username(),password=store.password(url,user);
            if(password.isEmpty())throw new IOException("请先保存 WebDAV 连接");
            RelayService.submit(context(),()->{try(RelayDav client=new RelayDav(url,folder,user,password)){RelayService.bind(client);RelayService.message=label;operation.run(client);RelayService.message=label+"：已完成";if(label.startsWith("上传"))log(label+"：已完成");}catch(Exception e){if(label.startsWith("上传"))log(label+"："+e.getMessage());throw e;}},grants,flags);
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
    private void actions(RelayDav.Entry entry){new AlertDialog.Builder(context()).setTitle(entry.name).setItems(new String[]{"打开","下载到…","分享给其他应用","删除远端文件"},(d,which)->{if(which==1){pendingDownload=entry;startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(RelayProvider.mime(entry.name)).putExtra(Intent.EXTRA_TITLE,entry.name),11);}else if(which==3)delete(Collections.singletonList(entry));else download(entry,null,which==2);}).show();}
    private void openCached(File file,boolean share){
        Uri uri=new Uri.Builder().scheme("content").authority(getPackageName()+".relay").appendPath(file.getParentFile().getName()).appendPath(file.getName()).build();
        try{
            Intent intent=new Intent(share?Intent.ACTION_SEND:Intent.ACTION_VIEW).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if(share){intent.setType(RelayProvider.mime(file.getName())).putExtra(Intent.EXTRA_STREAM,uri);}else intent.setDataAndType(uri,RelayProvider.mime(file.getName()));
            intent.setClipData(ClipData.newRawUri(file.getName(),uri));
            Intent chooser=Intent.createChooser(intent,share?"分享文件":"打开文件");
            chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,new ComponentName[]{new ComponentName(context(),RelayActivity.class)});
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
    private void downloads(){page="downloads";header("已下载");TextView hint=text("本机副本，点击直接打开。删除缓存不会影响远端文件。",13);hint.setTextColor(ToolUi.MUTED);content.addView(hint);
        try{List<File> cached=RelayCache.list(getCacheDir());if(cached.isEmpty())content.addView(text("暂无下载文件",16));for(File file:cached){
            LinearLayout row=new LinearLayout(context());row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(14),dp(8),dp(14));ToolUi.ripple(row,Color.WHITE,16);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);content.addView(row,rp);
            row.addView(ToolUi.icon(context(),RelayProvider.mime(file.getName()).startsWith("image/")?"image":"file",ToolUi.BLUE),new LinearLayout.LayoutParams(dp(28),dp(32)));
            LinearLayout details=column();details.setPadding(dp(14),0,dp(8),0);TextView name=ToolUi.text(context(),file.getName(),15,ToolUi.INK);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);details.addView(name);TextView meta=ToolUi.text(context(),size(file.length())+" · "+android.text.format.DateFormat.format("MM/dd HH:mm",file.lastModified()),12,ToolUi.MUTED);meta.setPadding(0,dp(6),0,0);details.addView(meta);row.addView(details,new LinearLayout.LayoutParams(0,-2,1));row.setContentDescription("打开缓存 "+file.getName());row.setFocusable(true);row.setOnClickListener(v->openCached(file,false));
            row.addView(ToolUi.iconButton(context(),"more","缓存操作 "+file.getName(),()->new AlertDialog.Builder(context()).setTitle(file.getName()).setItems(new String[]{"打开","分享给其他应用","删除本机缓存"},(d,w)->{if(w<2)openCached(file,w==1);else new AlertDialog.Builder(context()).setTitle("删除本机缓存？").setMessage(file.getName()+"，远端文件保留。").setNegativeButton("取消",null).setPositiveButton("删除",(a,b)->{try{RelayCache.remove(getCacheDir(),file);downloads();}catch(Exception e){notice(e.getMessage());}}).show();}).show()));
        }}catch(Exception e){notice(e.getMessage());}
    }
    private void delete(List<RelayDav.Entry> chosen){if(chosen.isEmpty())return;
        for(RelayDav.Entry e:chosen)if(e.directory||!RelayDav.strong(e.etag)){notice("包含无法安全删除的文件，请刷新或在飞牛中管理");return;}
        StringBuilder names=new StringBuilder("永久删除远端 "+chosen.size()+" 个文件；本机下载副本保留。\n");for(int i=0;i<Math.min(20,chosen.size());i++)names.append(chosen.get(i).name).append('\n');
        new AlertDialog.Builder(context()).setTitle("确认删除").setMessage(names).setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->transfer("删除文件",client->{int removed=0;for(RelayDav.Entry e:chosen){RelayService.check();if(client.deleteUnchanged(e))removed++;}final int count=removed;ui(()->notice("已删除 "+count+" 个，跳过 "+(chosen.size()-count)+" 个已变化文件"));})).show();
    }
    private void preview(){new AlertDialog.Builder(context()).setTitle("清理超过多久未修改的文件？").setItems(new String[]{"1 天","1 周","30 天","90 天"},(d,index)->{
        final String identity=WebDavSettings.identity(context());
        transfer("清理预览",client->{List<RelayDav.Entry> value=client.cleanupPreview(new int[]{1,7,30,90}[index]);long created=System.currentTimeMillis();ui(()->{
            StringBuilder names=new StringBuilder("将永久删除 "+value.size()+" 个文件，保留文件夹；缺少时间或强 ETag 的文件不会删除。\n");for(int i=0;i<Math.min(50,value.size());i++)names.append(value.get(i).path).append('\n');
            new AlertDialog.Builder(context()).setTitle("清理预览").setMessage(names).setNegativeButton("取消",null).setPositiveButton("确认清理",(a,b)->{
                if(value.isEmpty())return;if(System.currentTimeMillis()-created>600000||!identity.equals(WebDavSettings.identity(context()))){notice("预览已过期，请重新预览");return;}
                transfer("清理",remote->{int n=0;for(RelayDav.Entry e:value){RelayService.check();if(remote.deleteUnchanged(e))n++;}final int removed=n;ui(()->notice("已清理 "+removed+" 个，跳过 "+(value.size()-removed)+" 个已变化文件"));});
            }).show();
        });});
    }).show();}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;if(request==10){List<Uri> values=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)values.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)values.add(data.getData());if(!values.isEmpty())upload(values,path);}else if(request==11&&pendingDownload!=null&&data.getData()!=null){download(pendingDownload,data.getData(),false);pendingDownload=null;}}
    @Override public void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("path",path);state.putString("page",page);state.putBoolean("selecting",selecting);state.putStringArrayList("selected",new ArrayList<>(selected));state.putString("filter",filter);state.putInt("sort",sort);state.putParcelableArrayList("incoming",incoming);if(pendingDownload!=null){state.putString("download",pendingDownload.path);state.putLong("size",pendingDownload.size);state.putLong("modified",pendingDownload.modified);state.putString("etag",pendingDownload.etag);}}
    @Override protected void onPageResume(){String identity=WebDavSettings.identity(context());if(!identity.equals(connectionIdentity)){connectionIdentity=identity;path="";filter="";selecting=false;entries.clear();selected.clear();plan.clear();if(page.equals("files"))load();else if(page.equals("settings"))settings();}sendIncoming();handler.post(refresh);}
    @Override protected void onPagePause(){handler.removeCallbacks(refresh);}
    @Override public void onDestroy(){reads.shutdown();super.onDestroy();}
    @Override boolean back(){if(!page.equals("files")){showFiles();load();}else if(selecting){selecting=false;selected.clear();render();}else if(!path.isEmpty())up();else return false;return true;}
}
