package net.lanbridge.android;

import java.text.Normalizer;
import java.util.regex.*;

/** Stable formatting after extraction, independent of model or import batch. */
final class ParcelLocation {
    final String location,locker;
    private static final Pattern DETAIL=Pattern.compile("^(.+?)(室内|室外)((?:第)?[0-9A-Za-z一二三四五六七八九十百-]+号?(?:丰巢柜|快递柜|柜)?|丰巢柜|快递柜|柜)?$");
    private static final Pattern CABINET=Pattern.compile("^(.+?)([0-9A-Za-z一二三四五六七八九十百-]+号(?:丰巢柜|快递柜))$");
    private ParcelLocation(String location,String locker){this.location=location;this.locker=locker;}
    static String format(String value){return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC).replaceAll("[\\s\\u00a0\\u3000]+"," ").trim();}
    static ParcelLocation of(String location,String locker){
        String place=format(location),cabinet=format(locker);
        // Chinese names have no word separators. Preserve spaces in other languages.
        place=place.replaceAll("(?<=[\\p{IsHan}0-9]) +(?=[\\p{IsHan}0-9])","");
        cabinet=cabinet.replaceAll("(?<=[\\p{IsHan}0-9]) +(?=[\\p{IsHan}0-9])","");
        Matcher suffix=DETAIL.matcher(place);
        if(suffix.matches()){
            String area=suffix.group(2),detail=area+(suffix.group(3)==null?"":suffix.group(3));
            // Conflicting indoor/outdoor evidence stays intact for manual review.
            String other=area.equals("室内")?"室外":"室内";
            if(!cabinet.contains(other)){
                place=suffix.group(1);
                if(!cabinet.contains(detail)){
                    if(detail.equals(area)&&cabinet.startsWith(area)){}
                    else if(!cabinet.isEmpty()&&detail.endsWith(cabinet))cabinet=detail;
                    else if(detail.equals(area)||cabinet.isEmpty()||cabinet.equals("丰巢柜")||cabinet.equals("快递柜")||cabinet.equals("柜"))cabinet=detail+cabinet;
                    else if(detail.startsWith(area)&&cabinet.startsWith(detail.substring(area.length())))cabinet=area+cabinet;
                    else cabinet=detail+" · "+cabinet;
                }
            }
        }else{
            Matcher numbered=CABINET.matcher(place);
            if(numbered.matches()){place=numbered.group(1);String detail=numbered.group(2);if(cabinet.isEmpty()||detail.endsWith(cabinet))cabinet=detail;else if(!cabinet.contains(detail))cabinet=detail+" · "+cabinet;}
        }
        return new ParcelLocation(place,cabinet);
    }
}
