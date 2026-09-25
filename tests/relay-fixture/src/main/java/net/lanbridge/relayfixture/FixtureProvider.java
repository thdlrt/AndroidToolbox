package net.lanbridge.relayfixture;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class FixtureProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private byte[] bytes(Uri uri){return ("synthetic relay fixture: "+uri.getLastPathSegment()+"\n").getBytes(StandardCharsets.UTF_8);}
    public String getType(Uri uri){return "text/plain";}
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});c.addRow(new Object[]{uri.getLastPathSegment(),bytes(uri).length});return c;}
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException();
        try{File file=new File(getContext().getCacheDir(),"source.txt");try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes(uri));}return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);}catch(IOException e){throw new FileNotFoundException(e.toString());}
    }
    public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
    public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
}
