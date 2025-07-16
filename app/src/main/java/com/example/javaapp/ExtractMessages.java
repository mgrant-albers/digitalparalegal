package com.example.javaapp;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ExtractMessages{

    protected final String[] headers;
    protected final String threadId;
    protected final ExecutorService executorService;
    protected Context context;
    protected ConcurrentHashMap<Long, String> extractMap;
    protected final File cacheDirectory;
    private static final String TAG = "EXTRACTION";


    public ExtractMessages(Context context, String threadId, String[] headers){
        this.headers = headers;
        this.threadId = threadId;
        this.context = context;

        executorService = Executors.newFixedThreadPool(8);
        cacheDirectory = context.getCacheDir();
        extractMap = new ConcurrentHashMap<>();
        Log.i(TAG, "constructor successful");
        Log.i(TAG, cacheDirectory.toString());
    }
    public ExtractMessages(ExtractMessages input){
        this.headers = input.headers;
        this.threadId = input.threadId;
        this.context = input.context;
        this.executorService = input.executorService;
        this.extractMap = input.extractMap;
        this.cacheDirectory = input.cacheDirectory;
    }

    public void endExtract(){
        executorService.shutdown();
    }
    public void writeMapToPdf(Context context, Uri uriIn, long[] filenames) {

        Log.i(TAG, "Write Map Reached");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);

            try(
                    FileOutputStream fileOutputStream = (FileOutputStream) context.getContentResolver().openOutputStream(uriIn);
                    BufferedOutputStream buffOut = new BufferedOutputStream(fileOutputStream);
                    PdfWriter writer = new PdfWriter(buffOut);
                    PdfDocument pdf = new PdfDocument(writer);
                    Document document = new Document(pdf)
            ){

                StringBuilder strBuild = new StringBuilder();
                for (long messageId : filenames) {
                    ZonedDateTime key = getTime(messageId);
                    String value = extractMap.get(messageId);
                    strBuild.append(key.format(formatter)).append(": ").append("\n").append(value).append("\n\n");

                    if(strBuild.length() > 32768) {
                        document.add(new Paragraph(strBuild.toString()));
                        strBuild.setLength(0);
                    }
                }
                if(strBuild.length() > 0){
                    document.add(new Paragraph(strBuild.toString()));
                }
            }
            catch(IOException e){
                Log.e("IO", "FAILURE", e);
            }
        Log.i("IO", "COMPLETE");
    }

    public static void writePdf(Context context, Uri uriIn, long[] fileNames){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);
        String tempDirectory = context.getCacheDir().getAbsolutePath();
        Map<String, String> fileContent = filesToMap(context.getCacheDir());
        Log.i(TAG, tempDirectory);
        try(
                FileOutputStream fileOutputStream = (FileOutputStream) context.getContentResolver().openOutputStream(uriIn);
                PdfWriter writer = new PdfWriter(fileOutputStream);
                PdfDocument pdf = new PdfDocument(writer);
                Document document = new Document(pdf)
        ){

            for(long fileId : fileNames){

                ZonedDateTime date = getTime(fileId);
                document.add(new Paragraph(date.format(formatter) + ": " + fileContent.get(String.valueOf(fileId))));

            }
        }
        catch(IOException e){
            Log.e("PDF write", "Failure", e);
        }
    }
    public static Map<String, String> filesToMap(File directory){
        try{
            return Arrays.stream(directory.listFiles())
                    .parallel()
                    .collect(Collectors.toMap(
                            File::getName,
                            file -> {
                                try{
                                    return new String(Files.readAllBytes(file.toPath()));
                                }
                                catch(IOException e){
                                    Log.e(TAG, "stream", e);
                                    return "";
                                }
                            }
                    ));
        }
        catch(Exception e){
            Log.e(TAG, "Map method error");
        }
        return Map.of();
    }
    public void clearCache(){
        File[] cacheFiles = cacheDirectory.listFiles();

        assert cacheFiles != null;
        for(File file : cacheFiles){
            executorService.execute(file::delete);
        }
    }
    private static ZonedDateTime getTime(long timestamp){
        Instant instant = Instant.ofEpochMilli(timestamp);
        return instant.atZone(ZoneId.systemDefault());
    }
}
