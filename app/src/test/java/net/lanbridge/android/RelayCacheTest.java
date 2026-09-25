package net.lanbridge.android;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class RelayCacheTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void completeDownloadsAreReusableAndPartialStagingStaysHidden()throws Exception {
        File root=temporary.newFolder();
        File a=RelayCache.create(root,"同名.part"),b=RelayCache.create(root,"同名.part");
        Files.write(a.toPath(),new byte[]{1,2});Files.write(b.toPath(),new byte[]{3});
        Files.write(new File(root,"relay-download-test.part").toPath(),new byte[]{4});
        assertNotEquals(a,b);assertEquals(2,RelayCache.list(root).size());
        RelayCache.remove(root,a);assertTrue(b.exists());assertEquals(1,RelayCache.list(root).size());
    }
    @Test public void cannotDeleteOutsideCacheOrConstructTraversal()throws Exception {
        File root=temporary.newFolder(),outside=temporary.newFile();
        try{RelayCache.remove(root,outside);fail();}catch(IOException expected){}
        assertTrue(outside.exists());
        for(String name:new String[]{"../secret","a/b","/absolute",".."}){
            try{RelayCache.create(root,name);fail(name);}catch(IOException expected){}
        }
    }
}
