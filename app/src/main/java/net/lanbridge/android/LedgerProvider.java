package net.lanbridge.android;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
/** Only URI-granted ledger camera output or explicitly opened attachment copies. */
public final class LedgerProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private File file(Uri uri)throws FileNotFoundException{
        try{String name=uri.getLastPathSegment();if(name==null||!name.matches("[0-9a-f]{32}\\.(jpg|jpeg|png|webp|pdf|ofd)"))throw new IOException();File root=new File(getContext().getCacheDir(),"ledger-shared").getCanonicalFile();File file=new File(root,name).getCanonicalFile();if(!file.getParentFile().equals(root)||!file.isFile())throw new IOException();return file;}catch(Exception e){throw new FileNotFoundException();}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{int flags=mode.equals("r")?ParcelFileDescriptor.MODE_READ_ONLY:(mode.equals("w")||mode.equals("wt"))?ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_TRUNCATE:mode.equals("rw")?ParcelFileDescriptor.MODE_READ_WRITE:0;if(flags==0)throw new FileNotFoundException();return ParcelFileDescriptor.open(file(uri),flags);}
    @Override public String getType(Uri uri){return RelayProvider.mime(uri.getLastPathSegment());}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){try{File f=file(uri);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(cols);Object[] values=new Object[cols.length];for(int i=0;i<cols.length;i++)values[i]=OpenableColumns.DISPLAY_NAME.equals(cols[i])?f.getName():OpenableColumns.SIZE.equals(cols[i])?f.length():null;c.addRow(values);return c;}catch(Exception e){return null;}}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
