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
    static String toolIcon(String id){switch(id){case "vpn":return "shield";case "relay":return "transfer";case "ledger":return "ledger";case "diagnostics":return "network";case "parcel":return "parcel";default:return id;}}
    static int toolColor(String id){switch(id){case "vpn":return 0xff25816d;case "ledger":return 0xffb47724;case "diagnostics":return 0xff7254ba;case "parcel":return 0xffc66b44;default:return BLUE;}}
    static LinearLayout card(Context c){LinearLayout v=column(c);int p=dp(c,18);v.setPadding(p,p,p,p);GradientDrawable bg=box(c,Color.WHITE,20);bg.setStroke(dp(c,1),0xffe7ebf2);v.setBackground(bg);return v;}
    static LinearLayout header(Context c,String title,Runnable back){LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(iconButton(c,"back","返回",back));TextView t=text(c,title,23,INK);t.setTypeface(null,Typeface.BOLD);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));row.setPadding(0,0,0,dp(c,16));return row;}
    static void space(LinearLayout parent,View child){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(parent.getContext(),12);parent.addView(child,p);}
    /** Reflows existing children without rebuilding forms or discarding their drafts. */
    static final class Grid extends ViewGroup {
        private final int minimum,maximum,gap;private int columns=1;private final java.util.List<Integer> heights=new java.util.ArrayList<>();
        Grid(Context c,int minimum,int maximum,int gap){super(c);this.minimum=minimum;this.maximum=maximum;this.gap=gap;}
        @Override protected void onMeasure(int ws,int hs){int width=MeasureSpec.getSize(ws),g=dp(getContext(),gap);float scale=Math.max(1,getResources().getConfiguration().fontScale);columns=Math.max(1,Math.min(maximum,(int)((width+g)/(dp(getContext(),minimum)*scale+g))));if(maximum==4&&columns==3)columns=2;int cell=Math.max(0,(width-g*(columns-1))/columns);heights.clear();int count=0,total=0;
            for(int i=0;i<getChildCount();i++){View v=getChildAt(i);if(v.getVisibility()==GONE)continue;v.measure(MeasureSpec.makeMeasureSpec(cell,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));int row=count/columns;if(row==heights.size())heights.add(0);heights.set(row,Math.max(heights.get(row),v.getMeasuredHeight()));count++;}
            for(int h:heights)total+=h;total+=Math.max(0,heights.size()-1)*g;setMeasuredDimension(width,resolveSize(total,hs));}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int g=dp(getContext(),gap),cell=(getWidth()-g*(columns-1))/columns,count=0,y=0;for(int i=0;i<getChildCount();i++){View v=getChildAt(i);if(v.getVisibility()==GONE)continue;int row=count/columns,col=count%columns;if(col==0&&count>0)y+=heights.get(row-1)+g;int x=col*(cell+g);v.layout(x,y,x+cell,y+heights.get(row));count++;}}
        @Override protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(-1,-2);}
    }
    static GradientDrawable box(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static TextView text(Context c,String s,int size,int color){TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
    static void update(TextView view,String value){if(!android.text.TextUtils.equals(view.getText(),value))view.setText(value);}
    static void ripple(View v,int color,int radius){v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x20386CF4),box(v.getContext(),color,radius),null));}
    static Button button(Context c,String label,boolean primary,Runnable action){Button b=new Button(c);b.setAllCaps(false);b.setText(label);b.setTextSize(14);b.setTextColor(primary?Color.WHITE:BLUE);b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setPadding(dp(c,12),dp(c,10),dp(c,12),dp(c,10));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(c,8);b.setLayoutParams(bp);b.setStateListAnimator(null);ripple(b,primary?BLUE:0xffedf2ff,14);b.setOnClickListener(v->action.run());return b;}
    static ImageButton iconButton(Context c,String icon,String label,Runnable action){ImageButton b=new ImageButton(c);b.setImageDrawable(new Icon(icon,INK));b.setContentDescription(label);b.setTooltipText(label);b.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));ripple(b,Color.TRANSPARENT,14);b.setOnClickListener(v->action.run());b.setLayoutParams(new LinearLayout.LayoutParams(dp(c,48),dp(c,48)));return b;}
    static ImageView icon(Context c,String name,int color){ImageView v=new ImageView(c);v.setImageDrawable(new Icon(name,color));v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return v;}
    static EditText field(Activity a,LinearLayout parent,String label,String value,boolean secret){TextView title=text(a,label,13,MUTED);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(a,20);tp.bottomMargin=dp(a,8);parent.addView(title,tp);EditText e=new EditText(a);e.setSingleLine(true);e.setTextSize(16);e.setContentDescription(label);e.setText(value);e.setPadding(dp(a,14),0,dp(a,14),0);e.setInputType(secret?129:1);e.setSaveEnabled(!secret);e.setBackground(box(a,0xffedf1f7,12));parent.addView(e,new LinearLayout.LayoutParams(-1,dp(a,52)));return e;}
    static final class Shell extends LinearLayout {
        private final MainActivity activity;
        private final LinearLayout rail;
        private View bottom;
        private final ScrollView railScroll; private TextView brand; private int navigationWidth;
        private String selected;
        private boolean wide, bottomEnabled=true;
        private final java.util.Map<String,LinearLayout> rows=new java.util.LinkedHashMap<>();
        private final String[][] items={{"home","首页","home"},{"relay","文件中转站","transfer"},{"vpn","回家 VPN","shield"},{"ledger","项目记账","ledger"},{"diagnostics","网络诊断","network"},{"parcel","取件助手","parcel"},{"tools","全部工具","grid"},{"settings","设置","settings"}};
        Shell(MainActivity a,View body,String selected){
            super(a);activity=a;this.selected=selected;setOrientation(HORIZONTAL);setBackgroundColor(BG);
            rail=column(a);rail.setTag("toolbox-navigation");rail.setPadding(dp(a,16),dp(a,24),dp(a,16),dp(a,20));rail.setBackgroundColor(0xffedf1f8);
            railScroll=new ScrollView(a);railScroll.setFillViewport(true);railScroll.setVerticalScrollBarEnabled(false);railScroll.addView(rail);addView(railScroll,new LinearLayout.LayoutParams(dp(a,88),-1));addView(body,new LinearLayout.LayoutParams(0,-1,1));railScroll.setVisibility(GONE);
            setOnApplyWindowInsetsListener((v,i)->{setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
            TextView name=text(activity,"工具箱",22,INK);brand=name;name.setTypeface(null,Typeface.BOLD);rail.addView(name,new LinearLayout.LayoutParams(-1,dp(activity,64)));
            for(String[] item:items){
                LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(activity,12),0,dp(activity,8),0);
                row.addView(icon(activity,item[2],MUTED),new LinearLayout.LayoutParams(dp(activity,22),dp(activity,22)));
                TextView label=text(activity,item[1],14,INK);label.setPadding(dp(activity,12),0,0,0);row.addView(label);
                row.setContentDescription(item[1]);row.setFocusable(true);row.setOnClickListener(v->activity.show(item[0]));
                LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,dp(activity,52));layout.bottomMargin=dp(activity,8);rail.addView(row,layout);rows.put(item[0],row);
            }
            selected(selected);
        }
        void bottom(View view){bottom=view;updateNavigation();}
        void bottomEnabled(boolean enabled){bottomEnabled=enabled;updateNavigation();}
        private void updateNavigation(){
            boolean expanded=navigationWidth>=dp(activity,1100);railScroll.setVisibility(wide?VISIBLE:GONE);railScroll.getLayoutParams().width=dp(activity,expanded?200:88);railScroll.requestLayout();
            rail.setPadding(dp(activity,expanded?16:8),dp(activity,16),dp(activity,expanded?16:8),dp(activity,16));brand.setTextSize(expanded?22:15);brand.setGravity(Gravity.CENTER);
            for(LinearLayout row:rows.values()){row.setOrientation(expanded?HORIZONTAL:VERTICAL);row.setGravity(expanded?Gravity.CENTER_VERTICAL:Gravity.CENTER);row.setPadding(dp(activity,expanded?12:2),dp(activity,8),dp(activity,expanded?8:2),dp(activity,8));TextView label=(TextView)row.getChildAt(1);label.setTextSize(expanded?14:11);label.setGravity(expanded?Gravity.START:Gravity.CENTER);label.setPadding(dp(activity,expanded?12:0),dp(activity,expanded?0:5),0,0);row.getLayoutParams().height=LayoutParams.WRAP_CONTENT;row.setMinimumHeight(dp(activity,expanded?56:72));}
            if(bottom!=null)bottom.setVisibility(wide||!bottomEnabled?GONE:VISIBLE);
        }
        void selected(String value){
            selected=value;
            for(String[] item:items){LinearLayout row=rows.get(item[0]);boolean on=selected.equals(item[0]);row.setSelected(on);ripple(row,on?0xffdfe8ff:Color.TRANSPARENT,14);((ImageView)row.getChildAt(0)).setImageDrawable(new Icon(item[2],on?BLUE:MUTED));((TextView)row.getChildAt(1)).setTextColor(on?BLUE:INK);}
        }
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);navigationWidth=w-getPaddingLeft()-getPaddingRight();wide=navigationWidth>=dp(activity,600);post(this::updateNavigation);}
    }
    /** Original 24dp line glyphs, shared across navigation, file rows and actions. */
    static final class Icon extends Drawable {
        final String name;final Paint paint=new Paint(3);Icon(String name,int color){this.name=name;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
        private void line(Canvas c,float... points){Path p=new Path();p.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)p.lineTo(points[i],points[i+1]);c.drawPath(p,paint);}
        public void draw(Canvas c){c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);switch(name){
            case "parcel":line(c,3,7,12,3,21,7,21,17,12,22,3,17,3,7,12,12,21,7);line(c,12,12,12,22);line(c,8,5,17,10,17,14);break;
            case "ledger":line(c,5,3,19,3,19,22,16,20,13,22,10,20,7,22,5,20,5,3);line(c,9,7,15,7);line(c,9,11,15,11);line(c,9,15,12,15);break;
            case "network":c.drawRoundRect(8,2,16,8,1,1,paint);line(c,12,8,12,12,4,12,4,16);line(c,12,12,20,12,20,16);c.drawRoundRect(1,16,7,22,1,1,paint);c.drawRoundRect(17,16,23,22,1,1,paint);break;
            case "transfer":line(c,3,7,20,7,16,3);line(c,20,7,16,11);line(c,21,17,4,17,8,13);line(c,4,17,8,21);break;
            case "ai":line(c,12,2,15,9,22,12,15,15,12,22,9,15,2,12,9,9,12,2);line(c,20,2,20,6);line(c,18,4,22,4);break;
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
