package net.lanbridge.relayfixture;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);TextView text=new TextView(this);text.setTextSize(20);setContentView(text);
        Intent source=getIntent();String mode=source.getStringExtra("mode");
        if(mode!=null){
            String[] names=source.getStringArrayExtra("names");if(names==null)names=new String[]{source.getStringExtra("name")};
            ArrayList<Uri> uris=new ArrayList<>();for(String name:names)uris.add(Uri.parse("content://net.lanbridge.relayfixture.files/"+name));
            Intent send=new Intent(mode.equals("view")?Intent.ACTION_VIEW:mode.equals("multi")?Intent.ACTION_SEND_MULTIPLE:Intent.ACTION_SEND);
            send.setPackage(source.getStringExtra("target")).setType("text/plain").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ClipData clip=ClipData.newRawUri("fixture",uris.get(0));for(int i=1;i<uris.size();i++)clip.addItem(new ClipData.Item(uris.get(i)));send.setClipData(clip);
            if(mode.equals("view"))send.setDataAndType(uris.get(0),"text/plain");
            else if(mode.equals("multi"))send.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);
            else if(!mode.equals("clip"))send.putExtra(Intent.EXTRA_STREAM,uris.get(0));
            try{startActivity(send);finish();}catch(Exception e){text.setText("SEND_FAILED: "+e);}
        }else{
            Uri uri=Intent.ACTION_SEND.equals(source.getAction())?source.getParcelableExtra(Intent.EXTRA_STREAM):source.getData();
            try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                String result="READ_OK: "+new String(out.toByteArray(),StandardCharsets.UTF_8);text.setText(result);
                try(FileOutputStream saved=openFileOutput("received.txt",MODE_PRIVATE)){saved.write(out.toByteArray());}
            }catch(Exception e){text.setText("READ_FAILED: "+e);}
        }
    }
}
