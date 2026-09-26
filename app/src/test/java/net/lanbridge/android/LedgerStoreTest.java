package net.lanbridge.android;
import org.json.*;
import org.junit.*;
import java.nio.file.*;
import java.io.*;
import static org.junit.Assert.*;

public class LedgerStoreTest {
    private File root;private LedgerStore store;
    @Before public void setup()throws Exception{root=Files.createTempDirectory("ledger-test-").toFile();store=new LedgerStore(root);}
    private JSONObject op(String op,String entity,JSONArray parents,JSONObject changes,boolean deleted)throws Exception{if(parents.length()==0&&!deleted){JSONObject defaults=new JSONObject().put("title","test").put("date","2026-09-26").put("amount","1.00").put("amount_minor",100).put("currency","CNY").put("entry_type","expense").put("status","waiting").put("category","other").put("notes","").put("project_id",LedgerStore.AI_PROJECT).put("created_at",1);for(String key:LedgerStore.keys(changes))defaults.put(key,changes.get(key));changes=defaults;}return new JSONObject().put("schema",1).put("op_id",op).put("entity_type","entry").put("entity_id",entity).put("parents",parents).put("changes",changes).put("deleted",deleted).put("created_at",1.0);}
    private static String id(int n){return String.format("%032x",n);}
    @Test public void disjointEditsMergeAndConflictsRemainUntilResolution()throws Exception{
        String entity=id(1);store.ingest(op(id(2),entity,new JSONArray(),new JSONObject().put("title","original").put("category","other"),false));
        store.ingest(op(id(3),entity,new JSONArray().put(id(2)),new JSONObject().put("title","phone"),false));store.ingest(op(id(4),entity,new JSONArray().put(id(2)),new JSONObject().put("category","travel"),false));
        assertEquals("phone",store.entity("entry",entity).getString("title"));assertEquals("travel",store.entity("entry",entity).getString("category"));
        store.ingest(op(id(5),entity,new JSONArray().put(id(2)),new JSONObject().put("title","pc"),false));JSONObject view=store.entity("entry",entity);assertEquals("pc",view.getString("title"));assertEquals(2,view.getJSONObject("_conflicts").getJSONArray("title").length());
        store.patch("entry",entity,new JSONObject().put("title","resolved"),false);assertEquals(0,store.entity("entry",entity).getJSONObject("_conflicts").length());assertEquals("resolved",new LedgerStore(root).entity("entry",entity).getString("title"));
    }
    @Test public void missingParentsWaitAndDeletesNeverResurrect()throws Exception{
        String entity=id(1);store.ingest(op(id(3),entity,new JSONArray().put(id(2)),new JSONObject().put("title","child"),false));assertEquals(0,store.list("entry").length());store.ingest(op(id(2),entity,new JSONArray(),new JSONObject().put("title","parent"),false));assertEquals("child",store.entity("entry",entity).getString("title"));store.ingest(op(id(4),entity,new JSONArray().put(id(2)),new JSONObject(),true));store.ingest(op(id(8),entity,new JSONArray().put(id(3)),new JSONObject().put("title","late"),false));assertEquals(0,store.list("entry").length());
    }
    @Test public void nullAttachmentWinsAndAmountDerivedFromWinner()throws Exception{
        String entity=id(1),aid=id(99);JSONObject attachment=new JSONObject().put("id",aid).put("sha256",String.format("%064x",1)).put("size",1).put("original_name","receipt.pdf").put("kind","receipt").put("relative_path","attachments/"+entity+"/"+aid+".pdf");store.ingest(op(id(2),entity,new JSONArray(),new JSONObject().put("attachment:"+aid,attachment).put("amount","1.00").put("amount_minor",100),false));store.ingest(op(id(3),entity,new JSONArray().put(id(2)),new JSONObject().put("attachment:"+aid,JSONObject.NULL).put("amount","2.00").put("amount_minor",200),false));store.ingest(op(id(4),entity,new JSONArray().put(id(2)),new JSONObject().put("attachment:"+aid,attachment).put("amount","3.00").put("amount_minor",300),false));JSONObject value=store.entity("entry",entity);assertTrue(value.isNull("attachment:"+aid));assertEquals(300,value.getInt("amount_minor"));
    }
    @Test public void rejectsCrossEntityAndCycles()throws Exception{
        store.ingest(op(id(2),id(1),new JSONArray(),new JSONObject(),false));try{store.ingest(op(id(3),id(4),new JSONArray().put(id(2)),new JSONObject(),false));fail();}catch(IOException expected){}
        store.ingest(op(id(5),id(1),new JSONArray().put(id(6)),new JSONObject(),false));try{store.ingest(op(id(6),id(1),new JSONArray().put(id(5)),new JSONObject(),false));fail();}catch(IOException expected){}
    }
    @Test public void immutableIdsCannotBeOverwritten()throws Exception{JSONObject value=op(id(2),id(1),new JSONArray(),new JSONObject().put("title","first"),false);store.ingest(value);store.ingest(new JSONObject(value.toString()));try{store.ingest(op(id(2),id(1),new JSONArray(),new JSONObject().put("title","different"),false));fail();}catch(IOException expected){}assertEquals("first",store.entity("entry",id(1)).getString("title"));}
    @Test public void longHistorySurvivesWithoutRecursiveGraph()throws Exception{String entity=id(10000);store.ingest(op(id(10001),entity,new JSONArray(),new JSONObject(),false));for(int i=10002;i<11202;i++)store.ingest(op(id(i),entity,new JSONArray().put(id(i-1)),new JSONObject().put("notes","revision "+i),false));assertEquals("revision 11201",store.entity("entry",entity).getString("notes"));assertEquals(0,new LedgerStore(root).pendingParents());}
    @Test public void rejectsMalformedAndNonPositiveIncome()throws Exception{JSONObject income=op(id(2),id(1),new JSONArray(),new JSONObject().put("entry_type","income").put("amount","0.00").put("amount_minor",0),false);try{store.ingest(income);fail();}catch(IOException expected){}JSONObject bad=op(id(3),id(1),new JSONArray(),new JSONObject(),false);bad.getJSONObject("changes").put("unknown_secret","bad");try{store.ingest(bad);fail();}catch(IOException expected){}}
    @Test public void localMutationDoesNotAlterImmutableHistory()throws Exception{JSONObject input=op(id(2),id(1),new JSONArray(),new JSONObject().put("title","immutable"),false);store.ingest(input);input.getJSONObject("changes").put("title","mutated");assertEquals("immutable",store.entity("entry",id(1)).getString("title"));}
    @Test public void staleEditPreservesConcurrentRemoteChange()throws Exception{String entity=id(1);store.ingest(op(id(2),entity,new JSONArray(),new JSONObject(),false));JSONArray captured=store.heads("entry",entity);store.patch("entry",entity,new JSONObject().put("title","remote"),false);store.patch("entry",entity,new JSONObject().put("title","local"),false,captured);assertEquals(2,store.entity("entry",entity).getJSONObject("_conflicts").getJSONArray("title").length());}
}
