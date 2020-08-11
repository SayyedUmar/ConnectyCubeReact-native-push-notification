package com.dieam.reactnativepushnotification.modules;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

public class NotificationChannelManager {

    public static final String NOTIFICATION_MANAGER_PREF_NAME = "NOTIFICATION_MANAGER_PREF_NAME";

    public static final String NOTIFICATION_IN_APP_MESSAGE_CHANNEL_ID_PREF_KEY = "NOTIFICATION_IN_APP_MESSAGE_CHANNEL_ID_PREF_KEY";
    public static final String NOTIFICATION_IN_APP_GROUP_CHANNEL_ID_PREF_KEY = "NOTIFICATION_IN_APP_GROUP_CHANNEL_ID_PREF_KEY";
    public static final String NOTIFICATION_PUSH_MESSAGES_CHANNEL_ID_PREF_KEY = "NOTIFICATION_PUSH_MESSAGES_CHANNEL_ID_PREF_KEY";
    public static final String NOTIFICATION_CALL_CHANNEL_ID_PREF_KEY = "NOTIFICATION_CALL_CHANNEL_ID_PREF_KEY";
    public static enum CHANNELS {
        IN_APP_MESSAGES,
        IN_APP_GROUP,
        PUSH_MESSAGES,
        CALLS
    }

    public Context context;
    public NotificationManager notificationManager;

    NotificationChannelManager(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public String generateNewChannelId() {
        String newChannelId = "AXIA-ChannelId:" + String.valueOf(System.currentTimeMillis());
        return newChannelId;
    }

    public SharedPreferences getSharedPreferences() {
        return context.getSharedPreferences(NOTIFICATION_MANAGER_PREF_NAME, Context.MODE_PRIVATE);
    }

    public CHANNELS getType(String type) {
        if (type == null) {
            return CHANNELS.IN_APP_MESSAGES;
        }
        CHANNELS channelType;
        switch (type) {
            case "IN_APP_MESSAGES":
                channelType = CHANNELS.IN_APP_MESSAGES;
                break;
            case "IN_APP_GROUP":
                channelType = CHANNELS.IN_APP_GROUP;
                break;
            case "PUSH_MESSAGES":
                channelType = CHANNELS.PUSH_MESSAGES;
                break;
            case "CALLS":
                channelType = CHANNELS.CALLS;
                break;
            default:
                channelType = CHANNELS.IN_APP_MESSAGES;
        }
        return channelType;
    }

    public String getChannelIdSettingKey(CHANNELS channel) {
        String settingKey;
        switch (channel) {
            case IN_APP_MESSAGES:
                settingKey = NOTIFICATION_IN_APP_MESSAGE_CHANNEL_ID_PREF_KEY;
                break;
            case IN_APP_GROUP:
                settingKey = NOTIFICATION_IN_APP_GROUP_CHANNEL_ID_PREF_KEY;
                break;
            case PUSH_MESSAGES:
                settingKey = NOTIFICATION_PUSH_MESSAGES_CHANNEL_ID_PREF_KEY;
                break;
            case CALLS:
                settingKey = NOTIFICATION_CALL_CHANNEL_ID_PREF_KEY;
                break;
            default:
                settingKey = NOTIFICATION_IN_APP_MESSAGE_CHANNEL_ID_PREF_KEY;
        }
        return settingKey;
    }

    public String getChannelId(CHANNELS channel) {
        SharedPreferences preferences = getSharedPreferences();
        String settingKey = getChannelIdSettingKey(channel);
        return preferences.getString(settingKey, null);
    }

    public boolean setChannelId(CHANNELS channel, String channelId) {
        SharedPreferences preferences = getSharedPreferences();
        SharedPreferences.Editor preferencesEditor = preferences.edit();
        String settingKey = getChannelIdSettingKey(channel);
        preferencesEditor.putString(settingKey, channelId);
        return preferencesEditor.commit();
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    public void deleteChannel(String channelId) {
        notificationManager.deleteNotificationChannel(channelId);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public NotificationChannel createChannel(ChannelSettings settings) {
        NotificationChannel channel = new NotificationChannel(settings.id, settings.name, settings.importance);
        channel.enableLights(settings.lights);
        channel.enableVibration(settings.vibration);
        channel.setLockscreenVisibility(settings.lockScreenVisibility);
        channel.setSound(settings.soundUri, settings.soundAtr);
        return channel;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public String createChannelIfNotExists(CHANNELS channel) {
        String storedChannelId = getChannelId(channel);
        if (storedChannelId == null) {
            ChannelSettings channelSettings = new ChannelSettings(context);
            channelSettings.id = generateNewChannelId();
            switch (channel) {
                case IN_APP_MESSAGES:
                case PUSH_MESSAGES:
                case IN_APP_GROUP:
                    channelSettings.setDefaultMessageChannelSettings();
                    break;
                case CALLS:
                    channelSettings.setDefaultCallChannelSettings();
                    break;
                default:
                    channelSettings.setDefaultMessageChannelSettings();
            }
            notificationManager.createNotificationChannel(createChannel(channelSettings));
            storedChannelId = channelSettings.id;
            setChannelId(channel, storedChannelId);
        }
        return storedChannelId;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public String updateChannel(CHANNELS channelType, ChannelSettings settings) {
        deleteChannel(settings.id);
        settings.id = generateNewChannelId();
        NotificationChannel channel = createChannel(settings);
        notificationManager.createNotificationChannel(channel);
        setChannelId(channelType, settings.id);
        return settings.id;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void updateChannelSound(CHANNELS channel, String soundName) {
        NotificationChannel oldChannel = notificationManager.getNotificationChannel(createChannelIfNotExists(channel));
        ChannelSettings settings = new ChannelSettings(context, oldChannel);
        Uri newSoundUri = settings.getSoundUri(context, soundName);
        if (settings.checkSoundIsSame(newSoundUri)) {
            return;
        }
        settings.soundUri = newSoundUri;
        updateChannel(channel, settings);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void updateChannelVibration(CHANNELS channel, boolean shouldVibrate) {
        NotificationChannel oldChannel = notificationManager.getNotificationChannel(createChannelIfNotExists(channel));
        ChannelSettings settings = new ChannelSettings(context, oldChannel);
        if (settings.checkVibrationStateIsSame(shouldVibrate)) {
            return;
        }
        settings.vibration = shouldVibrate;
        updateChannel(channel, settings);
    }
}
