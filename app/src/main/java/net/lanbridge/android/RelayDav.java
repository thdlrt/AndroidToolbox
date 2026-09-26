package net.lanbridge.android;

import okhttp3.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Scoped WebDAV inbox. No redirects, recursive directory deletion or overwrite. */
public final class RelayDav implements AutoCloseable {
    public static final class Entry {
        public final String path,name,etag; public final boolean directory; public final long size,modified;
        Entry(String p,boolean d,long s,long m,String e){path=p;name=p.substring(p.lastIndexOf('/')+1);directory=d;size=s;modified=m;etag=e;}
    }
    public interface Progress { void update(long count,long total) throws IOException; }
    private final OkHttpClient client;
    private final HttpUrl root;
    private final String auth;
    private volatile boolean cancelled;
    public RelayDav(String address,String folder,String username,String password) throws IOException {
        HttpUrl base=endpoint(address);path(folder,false);
        HttpUrl.Builder b=base.newBuilder();for(String part:folder.split("/"))b.addPathSegment(part);b.addPathSegment("");root=b.build();
        auth=Credentials.basic(username,password,StandardCharsets.UTF_8);
        client=new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(15,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).writeTimeout(60,TimeUnit.SECONDS).build();
    }
    public static HttpUrl endpoint(String value)throws IOException {
        HttpUrl u=HttpUrl.parse(value.trim());
        if(u==null||!u.username().isEmpty()||!u.password().isEmpty()||u.query()!=null||u.fragment()!=null)throw new IOException("请填写完整 HTTP/HTTPS WebDAV 地址，账号密码单独填写");
        return u.newBuilder().encodedPath(u.encodedPath().replaceAll("/+$","")+"/").build();
    }
    public static String path(String value,boolean empty)throws IOException {
        if(value==null||(!empty&&value.isEmpty())||value.length()>2000)throw new IOException("路径无效");
        if(value.isEmpty())return value;
        for(String p:value.split("/",-1))if(p.isEmpty()||p.equals(".")||p.equals("..")||p.matches(".*[\\\\:*?\"<>|\\p{Cntrl}].*")||p.endsWith(".")||p.endsWith(" ")||p.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?"))throw new IOException("路径或文件名不受支持");
        return value;
    }
    public static boolean strong(String tag){return tag!=null&&tag.startsWith("\"")&&tag.endsWith("\"")&&!tag.contains("\r")&&!tag.contains("\n");}
    HttpUrl url(String p)throws IOException {path(p,true);HttpUrl.Builder b=root.newBuilder();if(!p.isEmpty())for(String s:p.split("/"))b.addPathSegment(s);return b.build();}
    private void checkCancelled()throws IOException {if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("操作已取消");}
    private Response request(String method,HttpUrl url,RequestBody body,String... headers)throws IOException {
        checkCancelled();Request.Builder b=new Request.Builder().url(url).header("Authorization",auth).method(method,body);
        for(int i=0;i<headers.length;i+=2)b.header(headers[i],headers[i+1]);return client.newCall(b.build()).execute();
    }
    private static void checked(Response r,int... expected)throws IOException {for(int code:expected)if(r.code()==code)return;throw new IOException("WebDAV 请求失败（HTTP "+r.code()+"）");}
    private static String text(Element e,String tag){NodeList nodes=e.getElementsByTagNameNS("DAV:",tag);return nodes.getLength()==0?"":nodes.item(0).getTextContent();}
    List<Entry> rows(String p,int depth)throws Exception {
        HttpUrl target=url(p);if(depth==1&&!target.encodedPath().endsWith("/"))target=target.newBuilder().addPathSegment("").build();
        RequestBody body=RequestBody.create("<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/><d:getetag/></d:prop></d:propfind>",MediaType.get("application/xml"));
        Response response=request("PROPFIND",target,body,"Depth",""+depth);
        if(Arrays.asList(301,302,307,308).contains(response.code())&&!target.encodedPath().endsWith("/")&&target.toString().concat("/").equals(String.valueOf(target.resolve(response.header("Location",""))))){response.close();target=target.newBuilder().addPathSegment("").build();response=request("PROPFIND",target,body,"Depth",""+depth);}
        byte[] xml;
        try(Response r=response){if(r.code()==404)return null;checked(r,207);try(InputStream in=r.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){checkCancelled();out.write(buf,0,n);if(out.size()>8*1024*1024)throw new IOException("目录过大");}xml=out.toByteArray();}}
        String raw=new String(xml,StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);if(raw.contains("<!DOCTYPE")||raw.contains("<!ENTITY"))throw new IOException("不支持的 XML 声明");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setExpandEntityReferences(false);
        javax.xml.parsers.DocumentBuilder parser=f.newDocumentBuilder();
        parser.setEntityResolver((publicId,systemId)->new org.xml.sax.InputSource(new StringReader("")));
        Document document=parser.parse(new ByteArrayInputStream(xml));
        if(document.getDoctype()!=null)throw new IOException("不支持的 XML 声明");
        NodeList responses=document.getElementsByTagNameNS("DAV:","response");
        List<Entry> result=new ArrayList<>();boolean self=false;
        for(int i=0;i<responses.getLength();i++){
            Element e=(Element)responses.item(i);HttpUrl href=target.resolve(text(e,"href"));
            if(href==null||!href.scheme().equals(root.scheme())||!href.host().equals(root.host())||href.port()!=root.port()||href.query()!=null||href.fragment()!=null)continue;
            String prefix=URI.create(root.toString()).getPath(),decoded=URI.create(href.toString()).getPath();if(!decoded.startsWith(prefix))continue;
            String name=decoded.substring(prefix.length()).replaceAll("/+$","");path(name,true);
            if(!name.equals(p)&&(depth==0||!parent(name).equals(p)))continue;
            boolean directory=false;long size=0,modified=0;String etag="";boolean valid=false;
            NodeList props=e.getElementsByTagNameNS("DAV:","propstat");
            for(int j=0;j<props.getLength();j++){Element prop=(Element)props.item(j);if(!text(prop,"status").contains(" 200 "))continue;valid=true;
                directory|=prop.getElementsByTagNameNS("DAV:","collection").getLength()>0;
                String length=text(prop,"getcontentlength");if(!length.isEmpty())size=Long.parseLong(length);
                String tag=text(prop,"getetag");if(!tag.isEmpty())etag=tag;
                try{modified=ZonedDateTime.parse(text(prop,"getlastmodified"),DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();}catch(Exception ignored){}
            }
            if(!valid||size<0)throw new IOException("文件属性不完整，停止读取");
            if(name.equals(p))self=true;result.add(new Entry(name,directory,size,modified,etag));
        }
        if(!self)throw new IOException("服务器未返回目录自身属性");return result;
    }
    public static String parent(String p){int i=p.lastIndexOf('/');return i<0?"":p.substring(0,i);}
    public Entry stat(String p)throws Exception {List<Entry> entries=rows(p,0);return entries==null?null:entries.get(0);}
    public List<Entry> list(String p)throws Exception {
        List<Entry> result=rows(p,1);if(result==null)throw new IOException("远端目录不存在");
        result.removeIf(e->e.path.equals(p)||e.name.startsWith(".relay-upload-"));
        result.sort(Comparator.comparing((Entry e)->!e.directory).thenComparing((Entry e)->-e.modified).thenComparing(e->e.name));return result;
    }
    public void ensure(String p)throws Exception {
        Entry e=stat(p);if(e!=null){if(!e.directory)throw new IOException("同名文件已存在");return;}
        try(Response r=request("MKCOL",url(p),RequestBody.create(new byte[0],null))){if(r.code()==409)throw new IOException("父目录不存在，请填写共享名/文件中转站");checked(r,201,405);}
        e=stat(p);if(e==null||!e.directory)throw new IOException("中转目录不可访问，请检查共享目录路径");
    }
    /** Atomically create an immutable ledger object; never overwrite an existing key. */
    public boolean putImmutable(File file,String destination)throws Exception {
        path(destination,false);
        try(Response r=request("PUT",url(destination),RequestBody.create(file,MediaType.get("application/octet-stream")),"If-None-Match","*")){
            if(r.code()==412)return false;checked(r,201,204);return true;
        }
    }
    public void upload(File file,String destination,Progress progress)throws Exception {
        path(destination,false);ensure(parent(destination));if(stat(destination)!=null)throw new IOException("远端已有同名文件，未覆盖");
        String temporary=(parent(destination).isEmpty()?"":parent(destination)+"/")+".relay-upload-"+UUID.randomUUID()+".part";
        RequestBody content=new RequestBody(){public MediaType contentType(){return MediaType.get("application/octet-stream");}public long contentLength(){return file.length();}public void writeTo(okio.BufferedSink sink)throws IOException{try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;long done=0;while((n=in.read(b))!=-1){checkCancelled();sink.write(b,0,n);progress.update(done+=n,file.length());}}}};
        try{
            try(Response r=request("PUT",url(temporary),content,"If-None-Match","*")){checked(r,201,204);}
            int code;try(Response r=request("MOVE",url(temporary),null,"Destination",url(destination).toString(),"Overwrite","F")){code=r.code();if(code!=502)checked(r,201,204);}
            if(code==502)try(Response r=request("MOVE",url(temporary),null,"Destination",url(destination).encodedPath(),"Overwrite","F")){checked(r,201,204);}
            Entry e=stat(destination);if(e==null||e.size!=file.length())throw new IOException("远端文件校验失败");
        }finally{if(!cancelled)try(Response ignored=request("DELETE",url(temporary),null)){}catch(Exception ignored){}}
    }
    public void download(Entry entry,File output,Progress progress)throws Exception {
        if(entry.directory)throw new IOException("请打开文件夹后选择文件");
        try(Response r=request("GET",url(entry.path),null,"Accept-Encoding","identity","If-Match",strong(entry.etag)?entry.etag:"*")){
            checked(r,200);long count=0;try(InputStream in=r.body().byteStream();OutputStream out=new FileOutputStream(output)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){checkCancelled();count+=n;if(count>entry.size)throw new IOException("远端文件大小变化");out.write(b,0,n);progress.update(count,entry.size);}}
            Entry now=stat(entry.path);if(count!=entry.size||now==null||now.size!=entry.size||!now.etag.equals(entry.etag)||now.modified!=entry.modified)throw new IOException("下载不完整或远端文件已变化");
        }catch(Exception e){output.delete();throw e;}
    }
    public List<Entry> cleanupPreview(int days)throws Exception {
        if(days<1||days>3650)throw new IOException("清理时间无效");List<Entry> files=new ArrayList<>();Deque<String> queue=new ArrayDeque<>();Set<String> seen=new HashSet<>();queue.add("");int count=0;
        while(!queue.isEmpty()){String p=queue.remove();if(!seen.add(p))throw new IOException("目录循环");for(Entry e:list(p)){if(++count>20000)throw new IOException("目录过大，请缩小范围");if(e.directory)queue.add(e.path);else if(strong(e.etag)&&e.modified>0&&e.modified<System.currentTimeMillis()-days*86400000L)files.add(e);}}
        return files;
    }
    public boolean deleteUnchanged(Entry expected)throws Exception {
        if(expected.directory||!strong(expected.etag))return false;Entry now=stat(expected.path);
        if(now==null||now.directory||now.modified!=expected.modified||now.size!=expected.size||!now.etag.equals(expected.etag))return false;
        try(Response r=request("DELETE",url(expected.path),null,"If-Match",expected.etag)){if(r.code()==404||r.code()==412)return false;checked(r,200,204);return true;}
    }
    public void cancel(){cancelled=true;client.dispatcher().cancelAll();}
    @Override public void close(){client.dispatcher().cancelAll();client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
}
