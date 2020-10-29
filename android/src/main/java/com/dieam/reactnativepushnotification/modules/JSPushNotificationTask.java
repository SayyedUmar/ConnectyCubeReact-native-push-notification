package com.dieam.reactnativepushnotification.modules;


import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.core.app.RemoteInput;

import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;

import java.util.HashMap;
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
    public static Handler hangUpTimeoutHandler = new Handler();
    static final HashMap<String, Runnable> janusGroupIdHangUpRunableMap = new HashMap<>();

    static public void setHangUpRunable(String janusGroupId, Runnable hangUpTimeout, long delay) {
        hangUpTimeoutHandler.postDelayed(hangUpTimeout, delay);
        janusGroupIdHangUpRunableMap.put(janusGroupId, hangUpTimeout);
    }

    static public boolean removeHangUpRunable(String janusGroupId) {
        if (hangUpTimeoutHandler != null) {
            Runnable hangUp = janusGroupIdHangUpRunableMap.get(janusGroupId);
            if (hangUp != null) {
                hangUpTimeoutHandler.removeCallbacks(hangUp);
                janusGroupIdHangUpRunableMap.remove(janusGroupId);
                return true;
            }
        }
        return false;
    }

    @Override
    protected @Nullable
    HeadlessJsTaskConfig getTaskConfig(Intent intent) {
        Bundle extras = intent.getExtras();
        
        if (extras == null) return null;

        for (String key : extras.keySet()) {
            Object value = extras.get(key);
            if (value == null) {
                continue;
            }
            Log.d("[getTaskConfig]", "[bundleItem]:" + String.format("%s %s (%s)", key,
                    value.toString(), value.getClass().getName()));
        }

        String taskName = extras.getString(BUNDLE_TASK_NAME_KEY);
        Log.d(TAG, "taskName: " + taskName);
        if (taskName == null) return null;
        if (taskName.equals(REPLY_TASK_KEY)) {
            Bundle remoteInputBundle = RemoteInput.getResultsFromIntent(intent);
            extras.putString("reply_message_text", remoteInputBundle.getString(REPLY_INPUT_KEY));
        } else if (taskName.equals(END_CALL_TASK_KEY)) {
            String janusGroupIdKey = extras.getString("janusGroupId");
            boolean result = removeHangUpRunable(janusGroupIdKey);
            Log.d(TAG, "[resultCancelHangUpTimeout] " + result);
        }
        boolean isCallPush = taskName.equals(START_CALL_TASK_KEY) || taskName.equals(END_CALL_TASK_KEY);
        if (this.isApplicationInForeground() && !isCallPush) {
            if (taskName.equals(MARK_AS_READ_TASK_KEY) || taskName.equals(REPLY_TASK_KEY)) {
                sendToJS(extras);
            }
            return null;
        }
        return new HeadlessJsTaskConfig(taskName, Arguments.fromBundle(extras), 0, isCallPush);
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
                            //TelecomManager telM = (TelecomManager) getApplicationContext().getSystemService(Context.TELECOM_SERVICE);
                            //boolean isInCall = telM.isInCall();
                            //return !isInCall;
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
