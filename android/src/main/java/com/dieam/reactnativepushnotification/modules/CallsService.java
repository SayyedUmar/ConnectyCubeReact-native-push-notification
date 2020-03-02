package com.dieam.reactnativepushnotification.modules;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class CallsService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = intent.getParcelableExtra("notification");
        int notificationID = intent.getIntExtra("notificationID", 0);
        startForeground(notificationID, notification);
        return super.onStartCommand(intent, flags, startId);
    }
}
