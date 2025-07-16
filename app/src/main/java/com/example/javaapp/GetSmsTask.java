package com.example.javaapp;

import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.Telephony;
import android.util.Log;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

public class GetSmsTask implements Callable<long[]> {

    private final ContentResolver contentResolver;
    private final String threadId;
    public ConcurrentHashMap<Long, String> mapIn;
    private final String FROM_HEADER;
    private final String TO_HEADER;

    public GetSmsTask(ContentResolver contentResolver, String threadId, String[] headers, ConcurrentHashMap<Long, String> mapIn){

        this.contentResolver = contentResolver;
        this.threadId = threadId;
        this.mapIn = mapIn;

        FROM_HEADER = "SMS From: " + headers[0] + "\n" + " To: " + headers[1] + "\n";
        TO_HEADER = "SMS To: " + headers[0] + "\n" + " From: " + headers[1] + "\n";
    }
    @Override
    public long[] call()  { return getSmsByAddress(contentResolver, threadId, mapIn); }

    private long[] getSmsByAddress(ContentResolver contentResolver, String threadId, ConcurrentHashMap<Long, String> mapIn) {
        Log.i("SMS Task", "Reached");
        long[] datesOut = null;

        String[] projection = {Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE};
        String selection = Telephony.Sms.THREAD_ID + "=?";
        String[] selectionArgs = {threadId};

        try(Cursor cursor = contentResolver.query(Telephony.Sms.CONTENT_URI, projection, selection, selectionArgs, Telephony.Sms.DATE + " DESC"))
        {
            if (cursor != null && cursor.getCount() > 0) {

                int dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE);
                int bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY);
                int msgType = cursor.getColumnIndex(Telephony.Sms.TYPE);
                datesOut = new long[cursor.getCount()];
                int index = 0;
                while (cursor.moveToNext()) {

                    String messageBody = cursor.getString(bodyIndex);
                    long dateSent = cursor.getLong(dateIndex);
                    if(messageBody == null){ messageBody = " "; }

                    if(cursor.getInt(msgType) == Telephony.Sms.MESSAGE_TYPE_INBOX) { mapIn.put(dateSent, TO_HEADER + messageBody); }
                    else{ mapIn.put(dateSent, FROM_HEADER + messageBody); }

                    datesOut[index] = dateSent;
                    index++;
                }
            }
        }
        Log.i("SMS Task", "Complete");
        return datesOut;
    }
}
