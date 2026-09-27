package net.lanbridge.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import java.util.concurrent.*;

/** Serial registry for business-data sync. Configuration backups remain explicitly manual. */
public final class DataSyncManager {
 public enum Trigger { STARTUP, LOCAL_CHANGE, PERIODIC, MANUAL }
 public interface Service { boolean configured() throws Exception; String synchronize() throws Exception; default String identity(){return "";} }
 interface StateStore {long time(String id);String error(String id);void save(String id,long success,String error);default String identity(String id){return "";}default void identity(String id,String value){} }
 private static DataSyncManager singleton;
 public static synchronized DataSyncManager get(Context context){
  if(singleton==null){Context app=context.getApplicationContext();SharedPreferences p=app.getSharedPreferences("data-sync-status",0);
   singleton=new DataSyncManager(new StateStore(){public long time(String id){return p.getLong(id+".success",0);}public String error(String id){return p.getString(id+".error","");}public void save(String id,long time,String error){p.edit().putLong(id+".success",time).putString(id+".error",error).commit();}public String identity(String id){return p.getString(id+".identity","");}public void identity(String id,String value){p.edit().putString(id+".identity",value).commit();}},Executors.newSingleThreadScheduledExecutor(),true);
   singleton.register("ledger","记账与诊断",new Service(){public boolean configured()throws Exception{WebDavSettings.migrate(app);return WebDavSettings.store(app).hasPassword();}public String synchronize()throws Exception{return LedgerSync.get(app).performOnce();}public String identity(){return WebDavSettings.identity(app);}});
  }return singleton;
 }
 /** A connection change immediately clears success/error; old work cannot certify the new destination. */
 static synchronized void connectionChanged(Context context){if(singleton!=null)singleton.invalidate();else context.getSharedPreferences("data-sync-status",0).edit().clear().commit();}
 private static final class Registration {final String name;final Service service;Registration(String name,Service service){this.name=name;this.service=service;}}
 private final StateStore state;private final ScheduledExecutorService worker;private final long debounceMillis,retryBase,retryMax;
 private final Map<String,Registration> services=new LinkedHashMap<>();private final Set<String> pending=new LinkedHashSet<>(),active=new HashSet<>();private final Map<String,Long> notBefore=new HashMap<>();private final Map<String,Integer> failures=new HashMap<>();private ScheduledFuture<?> scheduled;private long generation;
 public volatile boolean running;public volatile String status="尚未同步",error;public volatile long lastSuccess,revision;
 DataSyncManager(StateStore state,ScheduledExecutorService worker,boolean periodic){this(state,worker,periodic,periodic?1000:0,periodic?5000:0,300000);}
 DataSyncManager(StateStore state,ScheduledExecutorService worker,boolean periodic,long debounceMillis,long retryBase,long retryMax){this.state=state;this.worker=worker;this.debounceMillis=debounceMillis;this.retryBase=retryBase;this.retryMax=retryMax;lastSuccess=state.time("all");error=state.error("all");if(periodic)worker.scheduleWithFixedDelay(()->requestAll(Trigger.PERIODIC),60,60,TimeUnit.SECONDS);}
 public synchronized void register(String id,String name,Service service){if(!id.matches("[a-z][a-z0-9_-]{0,63}")||services.containsKey(id))throw new IllegalArgumentException("同步服务标识重复或无效");services.put(id,new Registration(name,service));}
 public synchronized void requestAll(Trigger reason){requestLocked(new ArrayList<>(services.keySet()),reason);}
 public synchronized void request(String id,Trigger reason){if(!services.containsKey(id))throw new IllegalArgumentException("同步服务未注册");requestLocked(Collections.singletonList(id),reason);}
 private void requestLocked(Collection<String> ids,Trigger reason){for(String id:ids)if(!running||!active.contains(id)||reason==Trigger.LOCAL_CHANGE){boolean fresh=pending.add(id);if(reason==Trigger.MANUAL)notBefore.remove(id);if(fresh&&reason==Trigger.LOCAL_CHANGE)notBefore.put(id,Math.max(notBefore.getOrDefault(id,0L),System.currentTimeMillis()+debounceMillis));}scheduleLocked();}
 private void scheduleLocked(){if(running||pending.isEmpty())return;long earliest=Long.MAX_VALUE;for(String id:pending)earliest=Math.min(earliest,notBefore.getOrDefault(id,0L));long delay=Math.max(0,earliest-System.currentTimeMillis());if(scheduled!=null&&!scheduled.isDone()){if(scheduled.getDelay(TimeUnit.MILLISECONDS)<=delay)return;scheduled.cancel(false);}scheduled=worker.schedule(this::drain,delay,TimeUnit.MILLISECONDS);}
 synchronized void invalidate(){generation++;lastSuccess=0;error="";status="连接已变化，等待同步";state.save("all",0,"");for(String id:services.keySet())state.save(id,0,"");failures.clear();notBefore.clear();pending.addAll(services.keySet());revision++;scheduleLocked();}
 private synchronized void identity(String id,String current){if(!current.equals(state.identity(id))){state.identity(id,current);generation++;state.save(id,0,"");state.save("all",0,"");lastSuccess=0;error="";failures.remove(id);notBefore.remove(id);revision++;}}
 static long retryDelay(int attempt,long base,long maximum){return Math.min(maximum,base*(1L<<Math.min(20,Math.max(0,attempt-1))));}
 private void drain(){
  while(true){List<String> batch=new ArrayList<>();synchronized(this){scheduled=null;long now=System.currentTimeMillis();for(String id:pending)if(notBefore.getOrDefault(id,0L)<=now)batch.add(id);if(batch.isEmpty()){running=false;active.clear();scheduleLocked();revision++;return;}pending.removeAll(batch);active.clear();active.addAll(batch);running=true;revision++;}
   for(String id:batch){Registration entry;synchronized(this){entry=services.get(id);}String destination=entry.service.identity();identity(id,destination);long epoch;synchronized(this){epoch=generation;}
    try{if(!entry.service.configured()){synchronized(this){failures.remove(id);notBefore.remove(id);pending.remove(id);}continue;}status="正在同步："+entry.name;String result=entry.service.synchronize();String completedIdentity=entry.service.identity();synchronized(this){if(epoch!=generation||!destination.equals(completedIdentity)){pending.add(id);continue;}state.save(id,System.currentTimeMillis(),"");failures.remove(id);if(!pending.contains(id))notBefore.remove(id);status=result;}}
    catch(Exception failure){String completedIdentity=entry.service.identity();synchronized(this){if(epoch!=generation||!destination.equals(completedIdentity)){pending.add(id);continue;}String detail=failure.getMessage()==null?"同步失败":failure.getMessage();if(detail.length()>800)detail=detail.substring(0,800);state.save(id,state.time(id),detail);int count=failures.getOrDefault(id,0)+1;failures.put(id,count);if(retryBase>0){notBefore.put(id,System.currentTimeMillis()+retryDelay(count,retryBase,retryMax));pending.add(id);}}}
   }
   aggregate();revision++;
  }
 }
 private void aggregate(){List<Map.Entry<String,Registration>> registered;synchronized(this){registered=new ArrayList<>(services.entrySet());}List<String> errors=new ArrayList<>();long oldest=Long.MAX_VALUE;boolean allSuccessful=true,configured=false;long epoch;synchronized(this){epoch=generation;}
  for(Map.Entry<String,Registration> item:registered){Registration entry=item.getValue();boolean enabled;try{enabled=entry.service.configured();}catch(Exception failure){enabled=true;}if(!enabled)continue;configured=true;String id=item.getKey();identity(id,entry.service.identity());String failure=state.error(id);long success=state.time(id);if(!failure.isEmpty()){errors.add(entry.name+"："+failure);allSuccessful=false;}if(success==0)allSuccessful=false;oldest=Math.min(oldest,success);}
  synchronized(this){if(epoch!=generation)return;error=String.join("；",errors);if(configured&&oldest==0)lastSuccess=0;if(configured&&allSuccessful&&pending.isEmpty())lastSuccess=oldest;state.save("all",lastSuccess,error);if(!error.isEmpty())status="同步未完成，已保留本机数据";else if(!configured)status="请先配置统一 WebDAV 连接";}
 }
 public void execute(Runnable action){worker.execute(action);}
 public String summary(){String time=lastSuccess==0?"尚无成功同步记录":java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(lastSuccess));return (running?"正在同步\n":"")+"最近成功同步："+time+(error.isEmpty()?"":"\n最近错误："+error);}
 void shutdown(){worker.shutdownNow();}
}
