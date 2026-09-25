package net.lanbridge.android;

import android.app.Activity;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared native controls: consistent touch targets, typography, icons and adaptive navigation. */
final class ToolUi {
    static final int INK=Color.rgb(29,43,65), MUTED=Color.rgb(100,116,139), BLUE=Color.rgb(56,108,244), BG=Color.rgb(245,247,251);
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(1);return l;}
    static GradientDrawable box(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static TextView text(Context c,String s,int size,int color){TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
    static void update(TextView view,String value){if(!android.text.TextUtils.equals(view.getText(),value))view.setText(value);}
    static void ripple(View v,int color,int radius){v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x20386CF4),box(v.getContext(),color,radius),null));}
    static Button button(Context c,String label,boolean primary,Runnable action){Button b=new Button(c);b.setAllCaps(false);b.setText(label);b.setTextSize(14);b.setTextColor(primary?Color.WHITE:BLUE);b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setPadding(dp(c,16),0,dp(c,16),0);b.setStateListAnimator(null);ripple(b,primary?BLUE:0xffedf2ff,14);b.setOnClickListener(v->action.run());return b;}
    static ImageButton iconButton(Context c,String icon,String label,Runnable action){ImageButton b=new ImageButton(c);b.setImageDrawable(new Icon(icon,INK));b.setContentDescription(label);b.setTooltipText(label);b.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));ripple(b,Color.TRANSPARENT,14);b.setOnClickListener(v->action.run());b.setLayoutParams(new LinearLayout.LayoutParams(dp(c,48),dp(c,48)));return b;}
    static ImageView icon(Context c,String name,int color){ImageView v=new ImageView(c);v.setImageDrawable(new Icon(name,color));v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return v;}
    static EditText field(Activity a,LinearLayout parent,String label,String value,boolean secret){TextView title=text(a,label,13,MUTED);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(a,20);tp.bottomMargin=dp(a,8);parent.addView(title,tp);EditText e=new EditText(a);e.setSingleLine(true);e.setTextSize(16);e.setContentDescription(label);e.setText(value);e.setPadding(dp(a,14),0,dp(a,14),0);e.setInputType(secret?129:1);e.setSaveEnabled(!secret);e.setBackground(box(a,0xffedf1f7,12));parent.addView(e,new LinearLayout.LayoutParams(-1,dp(a,52)));return e;}
    static void navigate(Activity a,String id){Intent i;if(id.equals("relay"))i=new Intent(a,RelayActivity.class);else if(id.equals("vpn"))i=new Intent(a,VpnActivity.class);else i=new Intent(a,MainActivity.class).putExtra("page",id);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);a.startActivity(i);if(!(a instanceof MainActivity))a.finish();}
    static final class Shell extends LinearLayout {
        private final Activity activity; private final LinearLayout rail; private View bottom; private String selected; private boolean wide;
        Shell(Activity a,View body,String selected){super(a);activity=a;this.selected=selected;setOrientation(HORIZONTAL);setBackgroundColor(BG);rail=column(a);rail.setPadding(dp(a,16),dp(a,24),dp(a,16),dp(a,20));rail.setBackgroundColor(0xffedf1f8);addView(rail,new LinearLayout.LayoutParams(dp(a,184),-1));addView(body,new LinearLayout.LayoutParams(0,-1,1));rail.setVisibility(GONE);setOnApplyWindowInsetsListener((v,i)->{setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});renderRail();}
        void bottom(View view){bottom=view;bottom.setVisibility(wide?GONE:VISIBLE);}
        void selected(String value){selected=value;renderRail();}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);boolean next=w-getPaddingLeft()-getPaddingRight()>=dp(activity,600);if(next!=wide){wide=next;post(()->{rail.setVisibility(wide?VISIBLE:GONE);if(bottom!=null)bottom.setVisibility(wide?GONE:VISIBLE);});}}
        private void renderRail(){rail.removeAllViews();TextView name=text(activity,"工具箱",22,INK);name.setTypeface(null,Typeface.BOLD);rail.addView(name,new LinearLayout.LayoutParams(-1,dp(activity,64)));String[][] items={{"home","首页","home"},{"relay","文件中转站","folder"},{"vpn","回家 VPN","shield"},{"tools","全部工具","grid"},{"settings","设置","settings"}};for(String[] item:items){boolean on=selected.equals(item[0]);LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(activity,12),0,dp(activity,8),0);ripple(row,on?0xffdfe8ff:Color.TRANSPARENT,14);row.addView(icon(activity,item[2],on?BLUE:MUTED),new LinearLayout.LayoutParams(dp(activity,22),dp(activity,22)));TextView label=text(activity,item[1],14,on?BLUE:INK);label.setPadding(dp(activity,12),0,0,0);row.addView(label);row.setContentDescription(item[1]);row.setFocusable(true);row.setOnClickListener(v->{if(activity instanceof MainActivity&&(item[0].equals("home")||item[0].equals("tools")||item[0].equals("settings")))((MainActivity)activity).show(item[0]);else if(!selected.equals(item[0]))navigate(activity,item[0]);});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(activity,52));p.bottomMargin=dp(activity,8);rail.addView(row,p);}}
    }
    /** Original 24dp line glyphs, shared across navigation, file rows and actions. */
    static final class Icon extends Drawable {
        final String name;final Paint paint=new Paint(3);Icon(String name,int color){this.name=name;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
        private void line(Canvas c,float... points){Path p=new Path();p.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)p.lineTo(points[i],points[i+1]);c.drawPath(p,paint);}
        public void draw(Canvas c){c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);switch(name){
            case "folder":line(c,3,7,3,5,9,5,11,7,21,7,21,20,3,20,3,7);break;
            case "home":line(c,3,11,12,3,21,11);line(c,5,10,5,21,10,21,10,15,14,15,14,21,19,21,19,10);break;
            case "shield":line(c,12,3,20,6,20,12,18,17,12,21,6,17,4,12,4,6,12,3);line(c,8,12,11,15,16,9);break;
            case "grid":for(int x:new int[]{4,14})for(int y:new int[]{4,14})c.drawRoundRect(x,y,x+6,y+6,1,1,paint);break;
            case "settings":c.drawCircle(12,12,7,paint);c.drawCircle(12,12,2.5f,paint);for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);line(c,12,2,12,5);c.restore();}break;
            case "cloud":line(c,6,17,4,17,2,15,2,12,4,10,7,10,8,6,11,4,15,4,18,7,18,10,21,11,22,14,20,17,18,17);line(c,12,12,12,22);line(c,9,15,12,12,15,15);break;
            case "upload":line(c,12,16,12,3);line(c,7,8,12,3,17,8);line(c,4,15,4,21,20,21,20,15);break;
            case "download":line(c,12,3,12,16);line(c,7,11,12,16,17,11);line(c,4,17,4,21,20,21,20,17);break;
            case "more":paint.setStyle(Paint.Style.FILL);for(int y:new int[]{5,12,19})c.drawCircle(12,y,1.5f,paint);paint.setStyle(Paint.Style.STROKE);break;
            case "back":line(c,15,5,8,12,15,19);break;
            case "close":line(c,6,6,18,18);line(c,18,6,6,18);break;
            case "search":c.drawCircle(10,10,6,paint);line(c,15,15,21,21);break;
            case "refresh":line(c,20,4,20,10,14,10);c.drawArc(4,4,20,20,40,285,false,paint);break;
            case "trash":line(c,4,6,20,6);line(c,9,6,9,3,15,3,15,6);line(c,6,6,7,21,17,21,18,6);line(c,10,10,10,17);line(c,14,10,14,17);break;
            case "check":line(c,5,12,10,17,20,6);break;
            case "image":c.drawRoundRect(3,3,21,21,2,2,paint);c.drawCircle(8,8,1.5f,paint);line(c,3,18,9,12,13,16,17,11,21,15);break;
            default:line(c,6,3,14,3,19,8,19,21,5,21,5,3,6,3);line(c,14,3,14,8,19,8);line(c,8,12,16,12);line(c,8,16,14,16);
        }c.restore();}
        public void setAlpha(int a){paint.setAlpha(a);}public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
