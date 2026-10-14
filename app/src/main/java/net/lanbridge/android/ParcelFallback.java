package net.lanbridge.android;

import java.io.IOException;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Strict literal fcbox arrival template, only correcting a model's OTHER classification. */
final class ParcelFallback {
    private static final Pattern FCBOX=Pattern.compile("^【丰巢】\\s*凭取件码\\s*([0-9]{8})\\s*至\\s*([^。！？\\r\\n]{1,512}?丰巢柜)\\s*取件[。.!！]?(?:\\s*快递员\\s*p\\.fcbox\\.com/[A-Za-z0-9_-]{1,80})?\\s*$");
    static ParcelAi.Item fcbox(ParcelSms.Message source){
        if(source.imageSha256!=null)return null;
        Matcher match=FCBOX.matcher(Normalizer.normalize(source.body.trim(),Normalizer.Form.NFKC));if(!match.matches())return null;
        ParcelLocation place=ParcelLocation.of(match.group(2),"");if(place.location.isEmpty())return null;
        return new ParcelAi.Item(List.of(source.id),match.group(1),"","",place.location,place.locker,ParcelAi.PENDING,null);
    }
    static final class Result {final List<ParcelAi.Item> items;final int corrected;Result(List<ParcelAi.Item> items,int corrected){this.items=items;this.corrected=corrected;}}
    static Result correct(List<ParcelSms.Message> sources,List<ParcelAi.Item> items)throws IOException{
        ParcelAi.validateItems(sources,items,false);
        Set<String> blocked=new HashSet<>();for(ParcelAi.Item item:items)if(!item.status.equals(ParcelAi.OTHER))blocked.addAll(item.sourceIds);
        Map<String,ParcelAi.Item> corrections=new LinkedHashMap<>();for(ParcelSms.Message source:sources)if(!blocked.contains(source.id)){ParcelAi.Item corrected=fcbox(source);if(corrected!=null)corrections.put(source.id,corrected);}
        if(corrections.isEmpty())return new Result(items,0);
        List<ParcelAi.Item> result=new ArrayList<>();for(ParcelAi.Item item:items){List<String> remaining=new ArrayList<>(item.sourceIds);remaining.removeAll(corrections.keySet());if(!remaining.isEmpty())result.add(remaining.size()==item.sourceIds.size()?item:item.withSources(remaining));}
        result.addAll(corrections.values());ParcelAi.validateItems(sources,result,false);return new Result(result,corrections.size());
    }
}
