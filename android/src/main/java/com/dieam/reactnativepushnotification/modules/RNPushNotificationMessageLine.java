package com.dieam.reactnativepushnotification.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class RNPushNotificationMessageLine {
    public static HashMap<String, ArrayList<String>> notificationsIDLines = new HashMap<String, ArrayList<String>>();

    public static ArrayList<String> addLineToNotification(int notificationID, String messageLine) {
        String notificationIDKey = String.valueOf(notificationID);
        if (!notificationsIDLines.containsKey(notificationIDKey)) {
            ArrayList<String> lines = new ArrayList<String>();
            notificationsIDLines.put(notificationIDKey, lines);
        }

        ArrayList<String> currentMessagesLines = notificationsIDLines.get(notificationIDKey);
        currentMessagesLines.add(messageLine);
        return currentMessagesLines;
    }

    public static ArrayList<String> getAllMessage(int notificationID) {
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
