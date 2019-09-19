package com.dieam.reactnativepushnotification.modules;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class RNPushNotificationMessageLine {
    public static HashMap<String, ArrayList<Bundle>> notificationsIDLines = new HashMap<>();
    public static HashSet<String> receivedMessagesIds = new HashSet<>();

    public static ArrayList<Bundle> addLineToNotification(int notificationID, Bundle messageBundle) {
        String notificationIDKey = String.valueOf(notificationID);
        if (!notificationsIDLines.containsKey(notificationIDKey)) {
            ArrayList<Bundle> lines = new ArrayList<>();
            notificationsIDLines.put(notificationIDKey, lines);
        }

        String message_id = messageBundle.getString("message_id");
        if (receivedMessagesIds.contains(message_id)) return notificationsIDLines.get(notificationIDKey);

        receivedMessagesIds.add(message_id);

        ArrayList<Bundle> currentMessagesLines = notificationsIDLines.get(notificationIDKey);
        currentMessagesLines.add(messageBundle);
        return currentMessagesLines;
    }

    public static boolean isReceived(String message_id) {
        return receivedMessagesIds.contains(message_id);
    }

    public static ArrayList<Bundle> getAllMessage(int notificationID) {
        return notificationsIDLines.get(String.valueOf(notificationID));
    }

    public static void clear(int notificationID) {
        String notificationIDKey = String.valueOf(notificationID);
        if (notificationsIDLines.containsKey(notificationIDKey)) {
//            ArrayList<Bundle> messagesBundles = notificationsIDLines.get(notificationIDKey);
//            if (messagesBundles != null) {
//                for(Bundle messageBundle : messagesBundles) {
//                    String message_id = messageBundle.getString("message_id");
//                    if (message_id != null) {
//                        receivedMessagesIds.remove(message_id);
//                        Log.d("[notif][clear]:", message_id);
//                    }
//                }
//            }
            notificationsIDLines.get(notificationIDKey).clear();
        }
    }

    public static void clearAll() {
       Set<String> notificationIDs = notificationsIDLines.keySet();
       for(String notificationIDKey : notificationIDs)
           notificationsIDLines.get(notificationIDKey).clear();
    }
}
