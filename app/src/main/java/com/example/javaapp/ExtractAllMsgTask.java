package com.example.javaapp;

import android.content.ContentResolver;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.stream.LongStream;

public class ExtractAllMsgTask extends ExtractMessages implements Callable<long[]> {
    private final ContentResolver contentResolver;

    public ExtractAllMsgTask(ExtractMessages input){
        super(input);
        this.contentResolver = super.context.getContentResolver();
    }

    @Override
    public long[] call() {

        String TAG = "ExtractAllMsgTask";
        Log.i(TAG, "Reached");
        ArrayList<long[]> tempList = new ArrayList<>();
        ArrayList<Callable<long[]>> taskList = new ArrayList<>();
        taskList.add(new GetSmsTask(contentResolver, threadId, headers, super.extractMap));
        taskList.add(new GetMmsTask(contentResolver, threadId, headers, super.extractMap));
        try {
            List<Future<long[]>> mapOut = executorService.invokeAll(taskList);

            for (Future<long[]> item : mapOut) {
                tempList.add(item.get());
            }
        } catch (Exception e) {
            Log.e(TAG, "FAILURE");
            throw new RuntimeException(e);
        }
        Log.i(TAG, "Complete");
        return tempList.stream().flatMapToLong(LongStream::of).sorted().toArray();
    }
}
