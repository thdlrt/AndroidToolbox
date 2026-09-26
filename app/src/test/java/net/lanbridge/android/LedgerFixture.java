package net.lanbridge.android;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.json.*;
/** Usage: java ... LedgerFixture INPUT.json OUTPUT.json; input {operations:[...],patches:[...]} */
public final class LedgerFixture {
    public static void main(String[] args)throws Exception{
        JSONObject input=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));LedgerStore store=new LedgerStore(Files.createTempDirectory("ledger-fixture-").toFile());JSONArray ops=input.getJSONArray("operations");for(int i=0;i<ops.length();i++)store.ingest(ops.getJSONObject(i));JSONArray patches=input.optJSONArray("patches");if(patches!=null)for(int i=0;i<patches.length();i++){JSONObject p=patches.getJSONObject(i);store.patch(p.getString("entity_type"),p.getString("entity_id"),p.getJSONObject("changes"),p.optBoolean("deleted"));}JSONObject output=new JSONObject().put("operations",new JSONArray(store.operations())).put("entries",store.list("entry")).put("projects",store.list("project")).put("network_profiles",store.list("network_profile"));Files.write(Paths.get(args[1]),output.toString(2).getBytes(StandardCharsets.UTF_8));
    }
}
