package com.example.javaapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/*
*    This code stills contains some vestigial lines and methods from the development process. I have left these in intentionally 
* to demonstrate to the reader how this code reached its current state, and what my thought process was. This was my first attempt
* at building something for the Android platform and as such there was a large amount of experimentation to see what worked and 
* what did not. This project was an excellent lesson in interacting with frameworks, writing code for maximum compatibility 
* and writing code for a small device with less computing resources. 
* 
*    First, the reader will certainly notice elementary logging, including a timer for tracking the process length. This 
* was an important lesson in debugging for me, as with smaller projects I had done in school lent themselves to using a step-by-step 
* debugger or printing to the terminal. A large initial hurdle for me in this project was the Android lifecycle, logging the various 
* components helped me to very quickly identify where problems were occurring. 
*
*    The reader will also likely notice some IO methods which are currently unused. I wanted to experiment with the idea of skipping 
* a substantial sorting operation which occurs after all MMS and SMS messages in the device have been collected. The idea was to
* write each message to disk with the timestamp as the file name, therefore requiring only as an array of long primitives to be sorted as opposed
* to sorting and merging a large TreeMap. However, this caused a more substantial slowdown. I learned that the IO operations I had 
* been experimenting with on my laptop involved virtual threads which were unavailable to me given the device compatibility restraints 
* here, and that utilizing the abstractions provided by the Android was causing additional overhead. This was an extremely valuable 
* lesson. It taught me most importantly how deep a programmer's understanding of a framework must be to utilize it correctly, particularly
* in a situation where said framework is not optimal for the use case at hand. 
*
*    While there is room for further optimization I am generally pleased with the performance of this app. It meets the goals set out at 
* the beginning of the project and was completed in the time I had allotted for it. It is exponentially faster than the app I had been 
* using previously and its footprint in device storage is minimal. As of now, the performance bottleneck is writing large PDF files. 
* Small files are usually complete by the time the user selects a directory and names the document, very large documents are completed
* in the background and notify the user when complete. This operation is not very resource intensive and the phone can be used normally 
* while this occurs, a PDF document 700 pages long takes roughly two minutes.  
*     
*/

public class MainActivity extends AppCompatActivity {
    private static final int ALL_PERMISSIONS = 101;
    private static final String CHANNEL_ID = "notification";
    private boolean extractComplete = false;
    private Spinner conversationSpinner;
    private Button button;
    private Button helpButton;
    private EditText userNumber;
    private ArrayList<String> conversationList;
    private Map<String, String> threadKey;
    private ExecutorService executor;
    private ActivityResultLauncher<Intent> pdfLauncher;
    public Uri uri;

    //Handled by pre and post API 33 permissions requests.
    @SuppressLint("InlinedApi")
    private final String[] permissions = { Manifest.permission.READ_SMS, Manifest.permission.POST_NOTIFICATIONS};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        conversationSpinner = findViewById(R.id.conversationSpinner);
        userNumber = findViewById(R.id.userPhone);
        button = findViewById(R.id.button);
        helpButton = findViewById(R.id.helpButton);
        threadKey = new HashMap<>();
        executor = Executors.newCachedThreadPool();
        initExtract();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) { preTiramisuPermissions(); }

        else{ postTiramisuPermissions(); }

    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == ALL_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                logicInit();
            }
            else {
                Log.v("ON REQUEST", "DENIED");
            }
        }
    }
    private void preTiramisuPermissions(){
        if (ContextCompat.
                checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {

            logicInit();
        }
        else if(ContextCompat.
                checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED){

            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.READ_SMS}, ALL_PERMISSIONS);
        }
    }
    private void postTiramisuPermissions(){
        if (ContextCompat.
                checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {

            logicInit();
        }
        else if(ContextCompat.
                checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED){

            ActivityCompat.requestPermissions(this, permissions, ALL_PERMISSIONS);
        }
    }
    public void logicInit(){
        button.setOnClickListener(v -> {
            if (conversationList != null) {
                executor.execute(this::mainLogic);
            }
        });
        helpButton.setOnClickListener(v -> displayHelpMessage());
        executor.execute(this::displayConversationsInSpinner);
        createNotificationChannel();
    }
    @Override
    protected void onRestart(){
        super.onRestart();
        if(executor.isTerminated() || executor.isShutdown()){
            executor = Executors.newCachedThreadPool();
        }
    }
    @Override
    protected void onStop(){
        super.onStop();
        Log.v("ONSTOP", "REACHED");
        if(extractComplete){
            executor.shutdown();
        }
    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        finish();
    }
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.v("SAVE", "REACHED");

        outState.putStringArrayList("conversationList", conversationList);
        outState.putCharSequence("userNumber", userNumber.getText());
        for(String key : threadKey.keySet()){
            outState.putString(key, threadKey.get(key));
        }
    }
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.v("RESTORE", "REACHED");
        conversationList = savedInstanceState.getStringArrayList("conversationList");
        userNumber.setText(savedInstanceState.getCharSequence("userNumber"));
        for(String key: conversationList){
            threadKey.put(key, savedInstanceState.getString(key));
        }
    }
    private void displayConversationsInSpinner() {

        conversationList = new ArrayList<>();
        ContentResolver contentResolver = getContentResolver();
        String[] projection = {Telephony.Sms.Inbox.ADDRESS, Telephony.Sms.Inbox.THREAD_ID};
        try(Cursor cursor = contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, projection, null, null, null)) {

            if (cursor != null && cursor.getCount() > 0) {

                int addressIndex = cursor.getColumnIndex(Telephony.Sms.Inbox.ADDRESS);
                int threadIndex = cursor.getColumnIndex(Telephony.Sms.Inbox.THREAD_ID);
                while (cursor.moveToNext()) {
                    String conversationAddress = formatNumber(cursor.getString(addressIndex));
                    String threadNum = cursor.getString(threadIndex);
                    if (conversationAddress.length() > 7 && conversationAddress.length() < 13 && !conversationList.contains(conversationAddress)) {

                        conversationList.add(conversationAddress);
                        if (threadNum != null && !threadNum.isEmpty()) {
                            threadKey.put(conversationAddress, threadNum);
                        }
                    }
                }
            }
        }
        updateSpinner();
    }
    private void mainLogic(){
        long startTime = System.currentTimeMillis();
        long endTime = 0L;
        String phoneNumber = (String) conversationSpinner.getSelectedItem();
        String userNum = formatNumber(userNumber.getText().toString());

        ExtractMessages extract = new ExtractMessages(this, threadKey.get(phoneNumber), makeHeaders(userNum, phoneNumber));

        clearSpinner();

        Future<long[]>futureArray = executor.submit(new ExtractAllMsgTask(extract));
        Future<Void> pdfPermission = executor.submit(() -> {
            getDocumentPermissions();
            return null;
        });

        try {
            long[] filenames = futureArray.get();
            pdfPermission.get();

            while(!extractComplete){
                if(uri != null && futureArray.isDone()){
                    extractComplete = true;
                }
            }
            extract.writeMapToPdf(this, uri, filenames);
            sendNotification();

            endTime = System.currentTimeMillis();
        }
        catch(InterruptedException | ExecutionException e){
            Log.e("MainLogic Catch", "Something fucked up", e);
        }
        finally{
            Log.i("Total Time:", String.valueOf((endTime - startTime)/1000L));
            extract.clearCache();
            extract.endExtract();
        }
        Log.i("MainLogic", "End of Method");

    }
    private void updateSpinner(){

        new Handler(Looper.getMainLooper()).post(() -> {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, conversationList);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            conversationSpinner.setAdapter(adapter);
        });
    }
    private void clearSpinner(){
        new Handler(Looper.getMainLooper()).post(() ->{
            ArrayAdapter<String> adapter = (ArrayAdapter<String>) conversationSpinner.getAdapter();
            adapter.clear();
            adapter.notifyDataSetChanged();
        });
    }
    private String[] makeHeaders(String user, String target){ return new String[] {user, target};}

    private String formatNumber(String s){
        String output;
        if(s.length() > 8 && !s.contains("@")){
            if(s.charAt(0) == '+'){
                s = s.substring(2);
            }
            output = s.substring(0, 3) + "-" + s.substring(3, 6) + "-" + s.substring(6,10);
            return output;
        }
        else
            return s;
    }
    private void initExtract(){
        pdfLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if(result.getResultCode() == RESULT_OK){
                        Intent data = result.getData();
                        if(data != null){
                           uri = data.getData();
                        }
                    }
                }
        );
    }
    public void getDocumentPermissions() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, ".pdf");
        pdfLauncher.launch(intent);
    }

    private void createNotificationChannel() {
            CharSequence name = "Digital ParaLegal";
            String description = "Your PDF is ready.";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
    }
    //Potential permissions issue handled
    @SuppressLint("MissingPermission")
    private void sendNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("Digital ParaLegal")
                .setContentText("Your PDF is ready.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        int notificationId = 1;
        notificationManager.notify(notificationId, builder.build());
    }
    private void displayHelpMessage(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Instructions:");
        builder.setMessage("First, enter your complete phone number into the text box.\n\n" +
                            "Then, select the phone number associated with the conversation you would like a PDF of.\n\n" +
                            "Finally, press the Get PDF button.\n\n" +
                            "You will receive a notification when your PDF is complete.\n\n" +
                            "The task will run in the background so you may minimize the app during this process.\n\n" +
                            "WARNING: if you shut down the app and the PDF is very large it may not complete successfully.\n\n" +
                            "Once complete, if you would like to make another PDF, shut down the app and open it again.");
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
