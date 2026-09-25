package net.lanbridge.android;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.*;
import java.util.Locale;

/** Only explicitly granted, read-only files in the relay share cache are exposed. */
public final class RelayProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private File file(Uri uri)throws FileNotFoundException {
        try{File root=new File(getContext().getCacheDir(),"relay-shared").getCanonicalFile();File f=new File(root,uri.getPath().substring(1)).getCanonicalFile();if(!f.getPath().startsWith(root.getPath()+File.separator)||!f.isFile())throw new IOException();return f;}catch(Exception e){throw new FileNotFoundException();}
    }
    public static String mime(String name){String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);String type=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);return type==null?"application/octet-stream":type;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {if(!mode.equals("r"))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public String getType(Uri uri){return mime(uri.getLastPathSegment()==null?"":uri.getLastPathSegment());}
    @Override public Cursor query(Uri u,String[] projection,String selection,String[] args,String order){try{File f=file(u);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(cols);Object[] values=new Object[cols.length];for(int i=0;i<cols.length;i++)values[i]=OpenableColumns.DISPLAY_NAME.equals(cols[i])?f.getName():OpenableColumns.SIZE.equals(cols[i])?f.length():null;c.addRow(values);return c;}catch(IOException e){return null;}}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
