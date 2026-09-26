package net.lanbridge.android;
import android.app.*;
import android.widget.Toast;
import org.json.*;
import java.math.BigDecimal;
import java.util.*;
/** Same explicit field conflict chooser for entries, projects and diagnostic profiles. */
final class LedgerConflicts {
    static void show(Activity activity,LedgerSync sync,String type,String id,Runnable resolved){try{
        JSONObject latest=sync.store().entity(type,id),fields=latest.getJSONObject("_conflicts");List<String> keys=new ArrayList<>(LedgerStore.keys(fields));
        if(keys.isEmpty()){Toast.makeText(activity,"没有待处理冲突",0).show();return;}
        new AlertDialog.Builder(activity).setTitle("选择冲突字段").setItems(keys.toArray(new String[0]),(dialog,index)->{try{
            String key=keys.get(index);JSONArray choices=fields.getJSONArray(key);String[] labels=new String[choices.length()];for(int i=0;i<labels.length;i++){Object value=choices.getJSONObject(i).get("value");labels[i]=value==JSONObject.NULL?"已移除":value instanceof JSONObject?((JSONObject)value).optString("original_name",value.toString()):String.valueOf(value);}
            new AlertDialog.Builder(activity).setTitle("保留哪个值："+key).setItems(labels,(d,n)->{try{JSONObject changes=new JSONObject().put(key,choices.getJSONObject(n).get("value"));if(key.equals("amount")){String value=String.valueOf(changes.get(key));changes.put("amount_minor",new BigDecimal(value).movePointRight(2).longValueExact());}sync.store().patch(type,id,changes,false,latest.getJSONArray("_heads"));sync.changed();Toast.makeText(activity,"已处理此字段，其他新修改仍保留",0).show();resolved.run();}catch(Exception e){error(activity,e);}}).setNegativeButton("取消",null).show();
        }catch(Exception e){error(activity,e);}}).setNegativeButton("关闭",null).show();
    }catch(Exception e){error(activity,e);}}
    private static void error(Activity a,Exception e){Toast.makeText(a,e.getMessage(),1).show();}
}
