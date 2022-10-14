package com.dieam.reactnativepushnotification.modules;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

public class ChannelSettings {

    public static final String DEFAULT_MESSAGE_SOUND = "receive_message.mp3";
    public static final String DEFAULT_CALL_SOUND = "axia_incoming_call.mp3";

    String id;
    String name;
    int importance;
    int lockScreenVisibility;
    boolean vibration;
    boolean lights;
    Uri soundUri;
    AudioAttributes soundAtr;
    Context context;

    ChannelSettings(Context context) {
        this.context = context;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    ChannelSettings(Context context, NotificationChannel channel) {
        id = channel.getId();
        name = (String) channel.getName();
        importance = channel.getImportance();
        vibration = channel.shouldVibrate();
        lights = channel.shouldShowLights();
        lockScreenVisibility = channel.getLockscreenVisibility();
        soundUri = channel.getSound();
        soundAtr = channel.getAudioAttributes();
    }

    public void setDefaultMessageChannelSettings() {
        name = "Messages";
        importance = NotificationManager.IMPORTANCE_HIGH;
        lockScreenVisibility = Notification.VISIBILITY_PUBLIC;
        vibration = true;
        lights = true;
        soundAtr = getDefaultSoundArt();
        soundUri = getSoundUri(context, DEFAULT_MESSAGE_SOUND);
    }

    public void setDefaultCallChannelSettings() {
        name = "Calls";
        importance = NotificationManager.IMPORTANCE_HIGH;
        lockScreenVisibility = Notification.VISIBILITY_PUBLIC;
        vibration = true;
        lights = true;
        soundAtr = getDefaultSoundArt();
        soundUri = getSoundUri(context, DEFAULT_CALL_SOUND);
    }

    public boolean checkSoundIsSame(Uri soundUri) {
        if (this.soundUri == null && soundUri == null) {
            return true;
        }
        if (this.soundUri == null && soundUri != null) {
            return false;
        }
        if (soundUri == null && this.soundUri != null) {
            return false;
        }
        return this.soundUri.compareTo(soundUri) == 0;
    }

    public boolean checkVibrationStateIsSame(boolean vibration) {
        return vibration == this.vibration;
    }

    public static Uri getSoundUri(Context context, String soundName) {
        if (soundName == null || soundName.equals("NONE")) {
            return null;
        }
        int resId = context.getResources().getIdentifier(soundName, "raw", context.getPackageName());
        if (resId == 0) {
            int soundNameExtDotIndex = soundName.lastIndexOf('.');
            if (soundNameExtDotIndex != -1) {
                soundName = soundName.substring(0, soundName.lastIndexOf('.'));
                resId = context.getResources().getIdentifier(soundName, "raw", context.getPackageName());
            }
        }
        if (resId != 0) {
            return Uri.parse("android.resource://" + context.getPackageName() + "/" + resId);
        }
        return Uri.parse(soundName);
    }

    public AudioAttributes getDefaultSoundArt() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build();
        return audioAttributes;
    }
}
