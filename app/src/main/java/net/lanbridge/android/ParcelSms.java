package net.lanbridge.android;

import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Explicit inbox range reads only. No background listener or SMS mutation. */
public final class ParcelSms {
    public static final int MAX_MESSAGES = 10000;
    public static final long MAX_RANGE_MS = 93L * 86400000;
    private ParcelSms() {}

    public static final class Message {
        public final String id, sender, body, fingerprint, imageSha256;
        public final long date;
        public Message(String id, long date, String sender, String body) {
            this(id, date, sender, body, null);
        }
        public Message(String id, long date, String sender, String body, String imageSha256) {
            if (id == null || id.isEmpty() || id.length() > 128 || date < 0 || body == null || body.length() > 262144)
                throw new IllegalArgumentException("来源标识、时间或内容长度无效");
            if (imageSha256 != null && !imageSha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("图片来源指纹无效");
            this.id = id;
            this.date = date;
            this.sender = sender == null ? "" : sender;
            if (this.sender.length() > 1000) throw new IllegalArgumentException("发送方信息过长");
            this.body = body;
            this.imageSha256 = imageSha256;
            String identity = imageSha256 == null ? date + "\n" + this.sender + "\n" + body : "image\n" + imageSha256;
            this.fingerprint = sha256(identity.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder();
            for (byte b : digest) result.append(String.format(Locale.ROOT, "%02x", b));
            return result.toString();
        } catch (Exception e) { throw new IllegalStateException("无法计算内容指纹"); }
    }

    /** Dates are inclusive, in Unix milliseconds. Must run on a worker thread. */
    public static List<Message> query(Context context, long startMillis, long endMillis) throws IOException {
        if (startMillis < 0 || endMillis < startMillis || endMillis - startMillis > MAX_RANGE_MS)
            throw new IOException("请选择有效时间范围，单次最多 93 天");
        List<Message> result = new ArrayList<>();
        String[] projection = {Telephony.Sms._ID, Telephony.Sms.DATE, Telephony.Sms.ADDRESS, Telephony.Sms.BODY};
        try (Cursor cursor = context.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI, projection,
                Telephony.Sms.DATE + " >= ? AND " + Telephony.Sms.DATE + " <= ?",
                new String[]{Long.toString(startMillis), Long.toString(endMillis)},
                Telephony.Sms.DATE + " ASC, " + Telephony.Sms._ID + " ASC")) {
            if (cursor == null) throw new IOException("无法读取收件箱");
            while (cursor.moveToNext()) {
                if (result.size() >= MAX_MESSAGES) throw new IOException("所选范围超过 10000 条短信，请缩小时间范围");
                result.add(new Message(cursor.getString(0), cursor.getLong(1), cursor.getString(2),
                        cursor.isNull(3) ? "" : cursor.getString(3)));
            }
        } catch (SecurityException e) { throw new IOException("需要允许读取短信，才能查询所选日期的收件箱"); }
        return result;
    }
}
