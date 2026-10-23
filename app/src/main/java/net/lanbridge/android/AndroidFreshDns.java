package net.lanbridge.android;

import android.net.DnsResolver;
import android.os.CancellationSignal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Android 10+: refresh the app's default network DNS without changing VPN policy. */
final class AndroidFreshDns {
    static List<InetAddress> lookup(String host)throws UnknownHostException{
        CompletableFuture<List<InetAddress>> answer=new CompletableFuture<>();
        CancellationSignal cancel=new CancellationSignal();
        try{
            DnsResolver.getInstance().query(null,host,DnsResolver.FLAG_NO_CACHE_LOOKUP,Runnable::run,cancel,new DnsResolver.Callback<List<InetAddress>>(){
                @Override public void onAnswer(List<InetAddress> values,int code){if(code==0&&!values.isEmpty())answer.complete(values);else answer.completeExceptionally(new UnknownHostException(host));}
                @Override public void onError(DnsResolver.DnsException error){answer.completeExceptionally(error);}
            });
            return answer.get(6,TimeUnit.SECONDS);
        }catch(Exception error){
            if(error instanceof InterruptedException)Thread.currentThread().interrupt();
            UnknownHostException failed=new UnknownHostException(host);failed.initCause(error);throw failed;
        }finally{cancel.cancel();}
    }
}
