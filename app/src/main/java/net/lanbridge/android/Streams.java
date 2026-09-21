package net.lanbridge.android;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class Streams {
    private Streams() {}
    static String text(InputStream in,int max)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=in.read(buffer))!=-1){if(out.size()+count>max)throw new IOException("响应内容过大");out.write(buffer,0,count);}
        return new String(out.toByteArray(),StandardCharsets.UTF_8);
    }
}
