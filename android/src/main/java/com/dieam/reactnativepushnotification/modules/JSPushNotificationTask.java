package com.dieam.reactnativepushnotification.modules;


import android.content.Intent;
import android.os.Bundle;

import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;
import javax.annotation.Nullable;

public class JSPushNotificationTask extends HeadlessJsTaskService {

    static String TASK_NAME = null;

    static void setTaskName(String taskName)
    {
        TASK_NAME = taskName;
    }

    @Override
    protected @Nullable
    HeadlessJsTaskConfig getTaskConfig(Intent intent) {

        if(TASK_NAME == null)
        {
            return null;
        }

        Bundle extras = intent.getExtras();
        if (extras != null) {
            return new HeadlessJsTaskConfig(
                    TASK_NAME,
                    Arguments.fromBundle(extras)
            );
        }
        return null;
    }
}