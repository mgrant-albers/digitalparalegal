package com.example.javaapp;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.util.Log;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

public class GetMmsTask implements Callable<long[]> {

    private final ContentResolver contentResolver;
    private final String threadId;
    public ConcurrentHashMap<Long, String> mapIn;
    private final String FROM_HEADER;
    private final String TO_HEADER;


    public GetMmsTask(ContentResolver contentResolver, String threadId, String[] headers, ConcurrentHashMap<Long, String> mapIn){

        this.contentResolver = contentResolver;
        this.threadId = threadId;
        this.mapIn = mapIn;

        FROM_HEADER = "MMS From: " + headers[0] + "\n" + " To: " + headers[1] + "\n";
        TO_HEADER = "MMS To: " + headers[0] + "\n" + " From: " + headers[1] + "\n";
    }
    @Override
    public long[] call()  { return getMmsByAddress(contentResolver, threadId, mapIn); }

    private long[] getMmsByAddress(ContentResolver contentResolver, String threadId, ConcurrentHashMap<Long, String> mapIn) {

        Log.i("MMS Task", "Reached");

        long[] datesOut = null;
        String[] projection = {Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX};
        String selection = Telephony.Mms.THREAD_ID + "=?";
        String[] selectionArgs = {threadId};

        try(Cursor cursor = contentResolver.query(Telephony.Mms.CONTENT_URI, projection, selection, selectionArgs, Telephony.Sms.DATE + " DESC")) {

            if(cursor != null && cursor.getCount() > 0){
                datesOut = new long[cursor.getCount()];
                int dateIndex = cursor.getColumnIndex(Telephony.Mms.DATE);
                int messageIdIndex = cursor.getColumnIndex(Telephony.Mms._ID);
                int boxIndex = cursor.getColumnIndex(Telephony.Mms.MESSAGE_BOX);
                int index = 0;

                while(cursor.moveToNext()){
                    String messageId = cursor.getString(messageIdIndex);
                    long timeOut = cursor.getLong(dateIndex);

                    if(cursor.getInt(boxIndex) == Telephony.Mms.MESSAGE_BOX_INBOX){
                        mapIn.put(timeOut * 1_000L, TO_HEADER + getBody(contentResolver, messageId));
                    }
                    else {
                        mapIn.put(timeOut * 1_000L, FROM_HEADER + getBody(contentResolver,  messageId));
                    }
                    datesOut[index] = timeOut * 1_000L;
                    index++;
                }
            }
        }
        Log.i("MMS Task", "Complete");
        return datesOut;
    }
    private static String getBody(ContentResolver contentResolver, String messageId){

        Uri partUri = Uri.parse("content://mms/part");
        String selection = "mid=" + messageId;
        StringBuilder bodyOut = new StringBuilder();

        try(Cursor bodyCursor = contentResolver.query(partUri, null, selection, null, null)){
            if(bodyCursor != null) {
                int contentIndex = bodyCursor.getColumnIndex(Telephony.Mms.Part.CONTENT_TYPE);
                while(bodyCursor.moveToNext()){
                    String typeCheck = bodyCursor.getString(contentIndex);

                    if("text/plain".equals(typeCheck)){

                        int textIndex = bodyCursor.getColumnIndex(Telephony.Mms.Part.TEXT);
                        bodyOut.append(bodyCursor.getString(textIndex));
                    }
                    else if(typeCheck.contains("image")){ bodyOut.append("User sent image attachment\n"); }

                    else if(typeCheck.contains("video")){ bodyOut.append("User sent video attachment\n"); }
                }
            }
        }
        return bodyOut.toString();
    }
}