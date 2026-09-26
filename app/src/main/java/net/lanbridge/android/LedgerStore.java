package net.lanbridge.android;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.math.BigDecimal;
import java.util.*;

/** Durable immutable operations, independent of Android for interoperability tests. */
public final class LedgerStore {
    public static final String AI_PROJECT="00000000000000000000000000000001";
    final File root,ops,blobs;
    private final TreeMap<String,JSONObject> operations=new TreeMap<>();
    public LedgerStore(File root)throws Exception{
        this.root=root;ops=new File(root,"ops");blobs=new File(root,"blobs");ops.mkdirs();blobs.mkdirs();
        File[] files=ops.listFiles((dir,name)->name.endsWith(".json"));if(files!=null)for(File file:files){JSONObject op=read(file);validate(op);if(!file.getName().equals(op.getString("op_id")+".json"))throw new IOException("操作文件名无效");operations.put(op.getString("op_id"),op);}
        validateGraph();
    }
    public static Set<String> keys(JSONObject object){Set<String> keys=new TreeSet<>();Iterator<String> iterator=object.keys();while(iterator.hasNext())keys.add(iterator.next());return keys;}
    public static String id(){return UUID.randomUUID().toString().replace("-","");}
    public static JSONObject read(File file)throws Exception{return new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));}
    private static boolean identifier(String value){return value.matches("[0-9a-f]{32}");}
    static void validate(JSONObject op)throws Exception{
        if(!(op.opt("schema") instanceof Number)||op.getInt("schema")!=1||!identifier(op.getString("op_id"))||!identifier(op.getString("entity_id"))||!Arrays.asList("entry","project","network_profile").contains(op.getString("entity_type"))||!(op.opt("deleted") instanceof Boolean)||!(op.opt("created_at") instanceof Number)||!Double.isFinite(op.getDouble("created_at"))||op.getDouble("created_at")<0||op.toString().getBytes(StandardCharsets.UTF_8).length>1024*1024)throw new IOException("不支持的同步操作");
        JSONArray parents=op.getJSONArray("parents");if(parents.length()>10000)throw new IOException("父版本过多");Set<String> seen=new HashSet<>();for(int i=0;i<parents.length();i++){String p=parents.getString(i);if(!identifier(p)||p.equals(op.getString("op_id"))||!seen.add(p))throw new IOException("操作父版本无效");}
        JSONObject changes=op.getJSONObject("changes");String type=op.getString("entity_type");Set<String> allowed=new HashSet<>(Arrays.asList((type.equals("entry")?"title,date,amount,amount_minor,currency,entry_type,status,category,notes,project_id,created_at":type.equals("project")?"name,settlement_mode,created_at,archived":"name,target,port,timeout_ms,attempts").split(",")));
        if(changes.has("amount")&&!changes.has("amount_minor")||changes.has("amount_minor")&&!changes.has("amount"))throw new IOException("金额字段不完整");
        for(String key:keys(changes)){
            Object value=changes.get(key);
            if(key.startsWith("attachment:")&&type.equals("entry")){if(!identifier(key.substring(11)))throw new IOException("附件标识无效");if(value!=JSONObject.NULL)validateAttachment(changes.getJSONObject(key),op.getString("entity_id"),key.substring(11));continue;}
            if(!allowed.contains(key))throw new IOException("不支持的同步字段："+key);
            if(Arrays.asList("title","name","category","notes","target").contains(key)){int limit=key.equals("title")?200:key.equals("name")?100:key.equals("category")?80:key.equals("notes")?5000:2048;if(!(value instanceof String)||((String)value).contains("\0")||((String)value).length()>limit||(!key.equals("notes")&&((String)value).trim().isEmpty()))throw new IOException("文本字段无效："+key);if(key.equals("target")){java.net.URI uri=java.net.URI.create(((String)value).contains("://")?(String)value:"https://"+value);if(!Arrays.asList("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IOException("诊断地址不应包含凭据或参数");}}
            else if(key.equals("date")){if(!(value instanceof String)||!((String)value).matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IOException("日期无效");java.time.LocalDate.parse((String)value);}
            else if(key.equals("amount")){if(!(value instanceof String)||!((String)value).matches("[0-9]{1,9}\\.[0-9]{2}"))throw new IOException("金额无效");}
            else if(key.equals("project_id")){if(!(value instanceof String)||!identifier((String)value))throw new IOException("项目 ID 无效");}
            else if(key.equals("archived")){if(!(value instanceof Boolean))throw new IOException("归档状态无效");}
            else if(key.equals("created_at")){if(!(value instanceof Number)||!Double.isFinite(((Number)value).doubleValue())||((Number)value).doubleValue()<0)throw new IOException("时间无效");}
            else if(Arrays.asList("amount_minor","port","timeout_ms","attempts").contains(key)){if(key.equals("port")&&value==JSONObject.NULL)continue;long max=key.equals("amount_minor")?99999999999L:key.equals("port")?65535:key.equals("timeout_ms")?120000:20;if(!(value instanceof Integer||value instanceof Long)||((Number)value).longValue()<0||((Number)value).longValue()>max)throw new IOException("数值无效："+key);}
            else {String options=key.equals("currency")?"CNY,USD,EUR,HKD":key.equals("entry_type")?"expense,income":key.equals("status")?"waiting,reimbursed,non_reimbursable,not_applicable":"general,half";if(!(value instanceof String)||!Arrays.asList(options.split(",")).contains(value))throw new IOException("选项无效："+key);}
        }
        if(changes.has("amount")&&new BigDecimal(changes.getString("amount")).movePointRight(2).longValueExact()!=changes.getLong("amount_minor"))throw new IOException("金额与分值不符");
        if(type.equals("entry")&&parents.length()==0&&!op.getBoolean("deleted")){if(!keys(changes).containsAll(allowed))throw new IOException("新记账记录缺少字段");if(changes.optString("entry_type").equals("income")&&changes.getLong("amount_minor")<=0)throw new IOException("收入金额必须大于零");}
    }
    static void validateAttachment(JSONObject a,String entity,String aid)throws Exception{
        Object size=a.opt("size");if(!identifier(aid)||!aid.equals(a.getString("id"))||!a.getString("sha256").matches("[0-9a-f]{64}")||!(size instanceof Integer||size instanceof Long)||a.getLong("size")<=0||a.getLong("size")>50L*1024*1024||!(a.opt("original_name") instanceof String)||a.getString("original_name").length()>1000||!Arrays.asList("invoice","receipt","payment","other").contains(a.optString("kind"))||!a.getString("relative_path").matches("attachments/"+entity+"/"+aid+"\\.(pdf|png|jpg|jpeg|webp|ofd)"))throw new IOException("附件元数据无效");
    }
    private Set<String> completed=new HashSet<>();
    private void validateGraph()throws Exception{
        Map<String,Integer> degrees=new HashMap<>(),allDegrees=new HashMap<>();Map<String,List<String>> children=new HashMap<>();
        for(JSONObject op:operations.values()){
            String id=op.getString("op_id");JSONArray parents=op.getJSONArray("parents");int known=0;
            for(int i=0;i<parents.length();i++){String p=parents.getString(i);JSONObject parent=operations.get(p);if(parent!=null){known++;if(!parent.getString("entity_id").equals(op.getString("entity_id"))||!parent.getString("entity_type").equals(op.getString("entity_type")))throw new IOException("操作父版本跨记录");}children.computeIfAbsent(p,k->new ArrayList<>()).add(id);}
            degrees.put(id,known);allDegrees.put(id,parents.length());
        }
        if(traverse(degrees,children).size()!=operations.size())throw new IOException("操作父版本循环");completed=traverse(allDegrees,children);
    }
    private Set<String> traverse(Map<String,Integer> degrees,Map<String,List<String>> children){Deque<String> ready=new ArrayDeque<>();for(String id:degrees.keySet())if(degrees.get(id)==0)ready.add(id);Set<String> result=new HashSet<>();while(!ready.isEmpty()){String id=ready.remove();result.add(id);for(String child:children.getOrDefault(id,Collections.emptyList())){int n=degrees.get(child)-1;degrees.put(child,n);if(n==0)ready.add(child);}}return result;}
    private boolean complete(String id,Set<String> visiting,Map<String,Boolean> memo){return completed.contains(id);}
    public synchronized int pendingParents(){return operations.size()-completed.size();}
    public static void write(File target,byte[] bytes)throws Exception{
        target.getParentFile().mkdirs();File temp=new File(target.getParentFile(),target.getName()+"."+id()+".tmp");
        try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);out.getFD().sync();}
        try{Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{temp.delete();}
    }
    public synchronized void ingest(JSONObject op)throws Exception{
        validate(op);String id=op.getString("op_id");JSONObject existing=operations.get(id);
        if(existing!=null){if(!equivalent(existing,op))throw new IOException("不可变操作内容冲突");return;}
        operations.put(id,new JSONObject(op.toString()));try{validateGraph();write(new File(ops,id+".json"),op.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception e){operations.remove(id);validateGraph();throw e;}
    }
    public static boolean equivalent(Object a,Object b){
        if(a instanceof JSONObject&&b instanceof JSONObject){JSONObject x=(JSONObject)a,y=(JSONObject)b;if(!LedgerStore.keys(x).equals(LedgerStore.keys(y)))return false;for(String k:LedgerStore.keys(x))if(!equivalent(x.opt(k),y.opt(k)))return false;return true;}
        if(a instanceof JSONArray&&b instanceof JSONArray){JSONArray x=(JSONArray)a,y=(JSONArray)b;if(x.length()!=y.length())return false;for(int i=0;i<x.length();i++)if(!equivalent(x.opt(i),y.opt(i)))return false;return true;}
        if(a instanceof Number&&b instanceof Number)return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()))==0;
        return Objects.equals(a,b);
    }
    public synchronized JSONArray heads(String type,String entity)throws Exception{
        TreeSet<String> heads=new TreeSet<>();Map<String,Boolean> memo=new HashMap<>();for(JSONObject op:operations.values())if(type.equals(op.getString("entity_type"))&&entity.equals(op.getString("entity_id"))&&complete(op.getString("op_id"),new HashSet<>(),memo))heads.add(op.getString("op_id"));
        Set<String> parents=new HashSet<>();for(String id:heads){JSONArray p=operations.get(id).getJSONArray("parents");for(int i=0;i<p.length();i++)parents.add(p.getString(i));}heads.removeAll(parents);return new JSONArray(heads);
    }
    public synchronized JSONObject patch(String type,String entity,JSONObject changes,boolean deleted)throws Exception{
        return patch(type,entity,changes,deleted,heads(type,entity));
    }
    public synchronized JSONObject patch(String type,String entity,JSONObject changes,boolean deleted,JSONArray parents)throws Exception {
        if(entity(type,entity).optBoolean("_deleted"))throw new IOException("记录已删除，请新建记录");
        JSONObject op=new JSONObject().put("schema",1).put("op_id",id()).put("entity_type",type).put("entity_id",entity).put("parents",parents).put("changes",changes).put("deleted",deleted).put("created_at",System.currentTimeMillis()/1000.0);ingest(op);return op;
    }
    public synchronized JSONObject entity(String type,String entity)throws Exception{
        List<JSONObject> source=new ArrayList<>();Map<String,Boolean> memo=new HashMap<>();boolean deleted=false;
        for(JSONObject op:operations.values())if(type.equals(op.getString("entity_type"))&&entity.equals(op.getString("entity_id"))&&complete(op.getString("op_id"),new HashSet<>(),memo)){source.add(op);deleted|=op.optBoolean("deleted");}
        JSONObject value=new JSONObject().put("id",entity),conflicts=new JSONObject();Set<String> keys=new TreeSet<>();for(JSONObject op:source)keys.addAll(LedgerStore.keys(op.getJSONObject("changes")));
        for(String key:keys){List<JSONObject> candidates=new ArrayList<>();for(JSONObject op:source)if(op.getJSONObject("changes").has(key))candidates.add(op);
            Set<String> ancestors=new HashSet<>();Deque<String> todo=new ArrayDeque<>();for(JSONObject candidate:candidates){JSONArray p=candidate.getJSONArray("parents");for(int i=0;i<p.length();i++)todo.add(p.getString(i));}while(!todo.isEmpty()){String id=todo.remove();if(!ancestors.add(id))continue;JSONObject parent=operations.get(id);if(parent!=null){JSONArray p=parent.getJSONArray("parents");for(int i=0;i<p.length();i++)todo.add(p.getString(i));}}List<JSONObject> maxima=new ArrayList<>();for(JSONObject candidate:candidates)if(!ancestors.contains(candidate.getString("op_id")))maxima.add(candidate);
            maxima.sort(Comparator.comparing(o->o.optString("op_id")));if(maxima.isEmpty())continue;
            Object winner=maxima.get(maxima.size()-1).getJSONObject("changes").get(key);boolean different=false;JSONArray alternatives=new JSONArray();
            for(JSONObject op:maxima){Object v=op.getJSONObject("changes").get(key);alternatives.put(new JSONObject().put("op_id",op.getString("op_id")).put("value",v));if(!equivalent(v,winner))different=true;if(key.startsWith("attachment:")&&v==JSONObject.NULL)winner=JSONObject.NULL;}
            value.put(key,winner);if(different)conflicts.put(key,alternatives);
        }
        if(value.has("amount")){value.put("amount_minor",new BigDecimal(value.getString("amount")).movePointRight(2).longValueExact());conflicts.remove("amount_minor");}if(type.equals("entry")){if(value.optString("entry_type").equals("income"))value.put("status","not_applicable");else if(value.optString("status").equals("not_applicable"))value.put("status","waiting");}
        return value.put("_deleted",deleted).put("_conflicts",conflicts).put("_heads",heads(type,entity));
    }
    public synchronized JSONArray list(String type)throws Exception{
        TreeSet<String> ids=new TreeSet<>();for(JSONObject op:operations.values())if(type.equals(op.getString("entity_type")))ids.add(op.getString("entity_id"));if(type.equals("project"))ids.add(AI_PROJECT);
        JSONArray items=new JSONArray();for(String id:ids){JSONObject entity=entity(type,id);if(type.equals("project")&&id.equals(AI_PROJECT)){if(!entity.has("created_at"))entity.put("created_at",0);if(!entity.has("archived"))entity.put("archived",false);if(!entity.has("name"))entity.put("name","AI报销");if(!entity.has("settlement_mode"))entity.put("settlement_mode","half");}if(!entity.optBoolean("_deleted")&&(entity.getJSONArray("_heads").length()>0||(type.equals("project")&&id.equals(AI_PROJECT))))items.put(entity);}return items;
    }
    public synchronized List<JSONObject> operations()throws Exception{List<JSONObject> result=new ArrayList<>();for(JSONObject op:operations.values())result.add(new JSONObject(op.toString()));return result;}
    public static String hash(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:digest.digest())s.append(String.format(Locale.ROOT,"%02x",b));return s.toString();}
    static void verifyContent(File file,String extension)throws Exception{
        byte[] header=new byte[16];int count;try(InputStream in=new FileInputStream(file)){count=in.read(header);}String ascii=new String(header,StandardCharsets.ISO_8859_1);boolean ok=extension.equals("pdf")?ascii.startsWith("%PDF-"):extension.equals("png")?count>=8&&(header[0]&255)==137&&ascii.substring(1,4).equals("PNG"):extension.equals("jpg")||extension.equals("jpeg")?count>=3&&(header[0]&255)==255&&(header[1]&255)==216&&(header[2]&255)==255:extension.equals("webp")?ascii.startsWith("RIFF")&&ascii.substring(8,12).equals("WEBP"):false;
        if(extension.equals("ofd"))try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(file)){ok=zip.getEntry("OFD.xml")!=null;}
        if(!ok)throw new IOException("附件内容与扩展名不匹配");
    }
    public JSONObject attach(String entity,File file,String name,String kind)throws Exception{
        String ext=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";if(!Arrays.asList("pdf","png","jpg","jpeg","webp","ofd").contains(ext))throw new IOException("支持 PDF、PNG、JPG、WebP、OFD 附件");if(file.length()==0||file.length()>50L*1024*1024)throw new IOException("附件上限 50 MB");
        verifyContent(file,ext);String hash=hash(file),aid=id();File dest=new File(blobs,hash);if(!dest.exists()){File temp=File.createTempFile("blob-",".tmp",blobs);try{Files.copy(file.toPath(),temp.toPath(),StandardCopyOption.REPLACE_EXISTING);try(FileOutputStream out=new FileOutputStream(temp,true)){out.getFD().sync();}Files.move(temp.toPath(),dest.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{temp.delete();}}
        return new JSONObject().put("id",aid).put("original_name",name).put("kind",kind).put("size",file.length()).put("sha256",hash).put("relative_path","attachments/"+entity+"/"+aid+"."+ext).put("created_at",System.currentTimeMillis()/1000.0);
    }
}
