package com.arlo.answerp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by arlo on 9/8/15.
 */
public class Autostart extends BroadcastReceiver {
    public void onReceive(Context context, Intent arg1) {
        Intent intent = new Intent(context, RemoteUpdateService.class);
        context.startService(intent);
        Log.i("Autostart", "started");
    }
}