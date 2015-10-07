package com.arlo.answerp;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.CallLog;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by arlo on 9/8/15.
 */
public class RemoteUpdateService extends Service {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final String TAG = "RemoteUpdateService";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onDestroy() {
        Log.d(TAG, "Remote updated service destroyed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Remote updated service started");

        scheduler.scheduleAtFixedRate(new Updater(), 0, 1, TimeUnit.MINUTES);

        return super.onStartCommand(intent, flags, startId);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private Collection<Map<String, String>> getMissedCalls() {
        // TODO use immutable list/map
        final Collection<Map<String, String>> calls = new ArrayList<>();

        final String[] projection = null;
        final String selection = String.format("%s == %d AND %s != 0 ", CallLog.Calls.TYPE, CallLog.Calls.MISSED_TYPE, CallLog.Calls.NEW);
        final String[] selectionArgs = null;
        final String sortOrder = android.provider.CallLog.Calls.DATE;
        try (final Cursor cursor = RemoteUpdateService.this.getApplicationContext().getContentResolver().query(
                CallLog.CONTENT_URI.buildUpon().appendPath("calls").build(),
                projection,
                selection,
                selectionArgs,
                sortOrder)){

            while (cursor.moveToNext()) {
                final Map<String, String> call = new HashMap<>();
                String callLogID = cursor.getString(cursor.getColumnIndex(CallLog.Calls._ID));
                String callNumber = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
                //String callDate = cursor.getString(cursor.getColumnIndex(CallLog.Calls.DATE));
                String callType = cursor.getString(cursor.getColumnIndex(CallLog.Calls.TYPE));
                int callNew = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.NEW));
                int callRead = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.IS_READ));
                Log.d(TAG, String.format("Missed Call Found: number: %s, type: %s, new: %d, read: %d", callNumber, callType, callNew, callRead));
                // TODO put into something
                call.put("id", callLogID);
                call.put("callNumber", callNumber);
                call.put("name", cursor.getString(cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)));
                call.put("time", cursor.getString(cursor.getColumnIndex(CallLog.Calls.DATE)));
                calls.add(call);
            }
        } catch(Exception ex){
            Log.e(TAG, "ERROR: " + ex.toString());
        }
        return calls;
    }

    private Collection<Map<String, String>> getNewTexts() {
        // TODO change from content provider to actually receiving texts
        final Cursor cursor = getContentResolver().query(Uri.parse("content://sms/inbox"), null, "read == 0", null, null);
        final Collection<Map<String, String>> texts = new ArrayList<>();

        if (cursor.moveToFirst()) { // must check the result to prevent exception
            do {
                String msgData = "Unread text found: ";
                for(int idx=0;idx<cursor.getColumnCount();idx++) {
                    msgData += "\n     " + cursor.getColumnName(idx) + ":" + cursor.getString(idx);
                    final Map<String, String> text = new HashMap<>();
                    text.put("id", cursor.getString(cursor.getColumnIndex("_id")));
                    text.put("date", cursor.getString(cursor.getColumnIndex("date")));
                    text.put("number", cursor.getString(cursor.getColumnIndex("address")));
                    text.put("name", cursor.getString(cursor.getColumnIndex("person")));
                    text.put("body", cursor.getString(cursor.getColumnIndex("body")));
                    texts.add(text);
                }
                Log.d(TAG, msgData);
            } while (cursor.moveToNext());
        } else {
            // empty box, no SMS
        }
        cursor.close();
        return texts;
    }

        private class Updater implements Runnable {

        @Override
        public void run() {
            Log.d(TAG, "Running updater");

            final Collection<Map<String, String>> missedCalls = getMissedCalls();
            final Collection<Map<String, String>> newTexts = getNewTexts();
            final JSONObject json = new JSONObject();

            try {
                json.put("calls", missedCalls);
                json.put("texts", newTexts);
            } catch (final JSONException e) {
                Log.e(TAG, "Error jsonifying data", e);
            }

            // TODO get address/port from config
            try (Socket socket = new Socket("192.168.1.50", 8888)) {
                socket.getOutputStream().write(json.toString().getBytes());
            } catch (final IOException e) {
                Log.e(TAG, "Error sending data to server", e);
            }
        }
    }

}