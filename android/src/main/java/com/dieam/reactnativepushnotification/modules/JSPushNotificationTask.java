package com.dieam.reactnativepushnotification.modules;


import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.RemoteInput;

import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;

import java.util.List;

import javax.annotation.Nullable;

public class JSPushNotificationTask extends HeadlessJsTaskService {

    static final String TAG = "[JS_BH_TASK]";

    static final String BUNDLE_TASK_NAME_KEY = "BUNDLE_TASK_NAME_KEY";

    static final String START_CALL_TASK_KEY = "START_CALL_TASK_KEY";

    static final String END_CALL_TASK_KEY = "END_CALL_TASK_KEY";

    static final String NOTIFY_TASK_KEY = "NOTIFY_TASK_KEY";

    static final String MARK_AS_READ_TASK_KEY = "MARK_AS_READ_TASK_KEY";

    static final String REPLY_TASK_KEY = "REPLY_TASK_KEY";

    static final String REPLY_INPUT_KEY = "REPLY_INPUT_KEY";

    @Override
    protected @Nullable
    HeadlessJsTaskConfig getTaskConfig(Intent intent) {
        Bundle extras = intent.getExtras();

        for (String key : extras.keySet()) {
            Object value = extras.get(key);
            Log.d("[getTaskConfig]", "[bundleItem]:" + String.format("%s %s (%s)", key,
                    value.toString(), value.getClass().getName()));
        }

        if (extras == null) return null;
        String taskName = extras.getString(BUNDLE_TASK_NAME_KEY);
        Log.d(TAG, "taskName: " + taskName);
        if (taskName == null) return null;
        if (taskName.equals(REPLY_TASK_KEY)) {
            Bundle remoteInputBundle = RemoteInput.getResultsFromIntent(intent);
            extras.putString("reply_message_text", remoteInputBundle.getString(REPLY_INPUT_KEY));
        }
        if (this.isApplicationInForeground()) {
            if (taskName.equals(MARK_AS_READ_TASK_KEY) || taskName.equals(REPLY_TASK_KEY)) {
                sendToJS(extras);
            }
            return null;
        }
        return new HeadlessJsTaskConfig(taskName, Arguments.fromBundle(extras));
    }

    public void sendToJS(Bundle bundle) {
        ReactInstanceManager mReactInstanceManager = ((ReactApplication) getApplication()).getReactNativeHost().getReactInstanceManager();
        ReactContext context = mReactInstanceManager.getCurrentReactContext();
        RNPushNotificationJsDelivery jsDelivery = new RNPushNotificationJsDelivery((ReactApplicationContext) context);
        jsDelivery.notifyNotificationAction(bundle);
    }

    public boolean isApplicationInForeground() {
        ActivityManager activityManager = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        List<RunningAppProcessInfo> processInfos = activityManager.getRunningAppProcesses();
        if (processInfos != null) {
            for (RunningAppProcessInfo processInfo : processInfos) {
                if (processInfo.processName.equals(getApplication().getPackageName())) {
                    if (processInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        for (String d : processInfo.pkgList) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}