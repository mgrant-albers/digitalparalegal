package com.example.javaapp;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Callable;

public class WriteTask implements Callable<Void> {

    private final Long filename;
    private final String text;
    private final File cache;
    public WriteTask(Long filename, String text, File cache){
        this.filename = filename;
        this.text = text;
        this.cache = cache;
    }
    @Override
    public Void call() {
        writeText(filename, text, cache);
        return null;
    }

    private void writeText(Long fileName, String text, File cache){
        String filePath = cache.getAbsolutePath() + "/" + fileName;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {

            writer.write(text);
        }
        catch (IOException e) {
            Log.e("IO", "Failed to write to cache");
        }
    }
}
