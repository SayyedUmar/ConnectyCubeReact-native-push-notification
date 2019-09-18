package com.dieam.reactnativepushnotification.modules;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class RNPushNotificationMessageLine {
    public static HashMap<String, ArrayList<Bundle>> notificationsIDLines = new HashMap<>();

    public static ArrayList<Bundle> addLineToNotification(int notificationID, Bundle messageBundle) {
        String notificationIDKey = String.valueOf(notificationID);
        if (!notificationsIDLines.containsKey(notificationIDKey)) {
            ArrayList<Bundle> lines = new ArrayList<>();
            notificationsIDLines.put(notificationIDKey, lines);
        }

        ArrayList<Bundle> currentMessagesLines = notificationsIDLines.get(notificationIDKey);
        currentMessagesLines.add(messageBundle);
        return currentMessagesLines;
    }

    public static ArrayList<Bundle> getAllMessage(int notificationID) {
        return notificationsIDLines.get(String.valueOf(notificationID));
    }

    public static void clear(int notificationID) {
        String notificationIDKey = String.valueOf(notificationID);
        if (notificationsIDLines.containsKey(notificationIDKey)) {
            notificationsIDLines.get(notificationIDKey).clear();
        }
    }

    public static void clearAll() {
       Set<String> notificationIDs = notificationsIDLines.keySet();
       for(String notificationIDKey : notificationIDs)
           notificationsIDLines.get(notificationIDKey).clear();
    }
}
