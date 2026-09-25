package net.lanbridge.android;
import java.io.*;
import java.util.*;

/** Only complete downloads under a random cache directory are listed/shared. */
public final class RelayCache {
    public static File create(File cache,String name)throws IOException {
        RelayDav.path(name,false);
        if(name.contains("/"))throw new IOException("文件名无效");
        File directory=new File(cache,"relay-shared/"+UUID.randomUUID());
        if(!directory.mkdirs())throw new IOException("无法建立缓存目录");
        return new File(directory,name);
    }
    public static List<File> list(File cache)throws IOException {
        File root=new File(cache,"relay-shared").getCanonicalFile();
        List<File> result=new ArrayList<>();File[] folders=root.listFiles();
        if(folders!=null)for(File folder:folders){File[] files=folder.listFiles();if(files!=null)for(File f:files){File real=f.getCanonicalFile();if(real.isFile()&&real.getPath().startsWith(root.getPath()+File.separator))result.add(real);}}
        result.sort(Comparator.comparingLong(File::lastModified).reversed());return result;
    }
    public static void remove(File cache,File file)throws IOException {
        File root=new File(cache,"relay-shared").getCanonicalFile(),real=file.getCanonicalFile();
        if(!real.getPath().startsWith(root.getPath()+File.separator)||!real.isFile())throw new IOException("缓存文件已不存在");
        if(!real.delete())throw new IOException("无法删除缓存文件");
        real.getParentFile().delete();
    }
}
