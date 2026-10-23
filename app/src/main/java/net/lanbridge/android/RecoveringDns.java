package net.lanbridge.android;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import okhttp3.Dns;

/** Retry only name resolution, before any credential or upload byte is sent. */
final class RecoveringDns implements Dns {
    private final Dns cached,fresh;
    RecoveringDns(Dns cached,Dns fresh){this.cached=cached;this.fresh=fresh;}
    private static List<InetAddress> checked(List<InetAddress> values,String host)throws UnknownHostException{
        if(values==null||values.isEmpty())throw new UnknownHostException(host);
        return values;
    }
    @Override public List<InetAddress> lookup(String host)throws UnknownHostException{
        try{return checked(cached.lookup(host),host);}catch(UnknownHostException first){
            if(Thread.currentThread().isInterrupted())throw first;
            try{return checked(fresh.lookup(host),host);}catch(UnknownHostException second){first.addSuppressed(second);throw first;}
        }
    }
}
