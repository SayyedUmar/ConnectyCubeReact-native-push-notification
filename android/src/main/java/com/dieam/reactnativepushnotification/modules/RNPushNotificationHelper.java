package com.dieam.reactnativepushnotification.modules;


import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;

import com.facebook.react.bridge.ReadableMap;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.dieam.reactnativepushnotification.modules.RNPushNotification.LOG_TAG;
import static com.dieam.reactnativepushnotification.modules.RNPushNotificationAttributes.fromJson;

public class RNPushNotificationHelper {
    public static final String PREFERENCES_KEY = "rn_push_notification";
    private static final long DEFAULT_VIBRATION = 300L;
    private static final String NOTIFICATION_CHANNEL_ID = "rn-push-notification-channel-id";
    private static final String NOTIFICATION_CHANNEL_CALL_ID = "rn-push-notification-channel-call-id";
    private static final String NOTIFICATION_GROUP_ID = "rn-push-notification-group-id";
    private static final int NOTIFICATION_WITH_GROUP_ID = 6784;
    public static final String CLEAR_MESSAGE = "CLEAR_MESSAGE";
    public static final String NOTIFICATION_BUNDLE = "notification";
    public static final String DELETE_MESSAGE = "DELETE_MESSAGE";
    public static final String MESSAGING_STYLE_TEXT = "";
    private static final int ONE_MINUTE = 60 * 1000;
    private static final long ONE_HOUR = 60 * ONE_MINUTE;
    private static final long ONE_DAY = 24 * ONE_HOUR;
    private static final RNPushNotificationsMessages hashMapDialogsToMessages = new RNPushNotificationsMessages();


    private Context context;
    private RNPushNotificationConfig config;
    private final SharedPreferences scheduledNotificationsPersistence;

    private int blueColor = Color.argb(255, 67, 163, 204);

    public NotificationChannelManager notificationChannelManager;

    public RNPushNotificationHelper(Application context) {
        this.context = context;
        this.config = new RNPushNotificationConfig(context);
        this.scheduledNotificationsPersistence = context.getSharedPreferences(RNPushNotificationHelper.PREFERENCES_KEY, Context.MODE_PRIVATE);
        notificationChannelManager = new NotificationChannelManager(context);
    }

    public static void clearMessage()
    {
        hashMapDialogsToMessages.clear();

    }

    public static void deleteMessage(String dialog_id, String message_id)
    {
        hashMapDialogsToMessages.deleteMessage(dialog_id, message_id);
    }

    public Class getMainActivityClass() {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        String className = launchIntent.getComponent().getClassName();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private AlarmManager getAlarmManager() {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    private PendingIntent toScheduleNotificationIntent(Bundle bundle) {
        int notificationID = Integer.parseInt(bundle.getString("id"));

        Intent notificationIntent = new Intent(context, RNPushNotificationPublisher.class);
        notificationIntent.putExtra(RNPushNotificationPublisher.NOTIFICATION_ID, notificationID);
        notificationIntent.putExtras(bundle);

        return PendingIntent.getBroadcast(context, notificationID, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public void sendNotificationScheduled(Bundle bundle) {
        Class intentClass = getMainActivityClass();
        if (intentClass == null) {
            Log.e(LOG_TAG, "No activity class found for the scheduled notification");
            return;
        }

        if (bundle.getString("message") == null) {
            Log.e(LOG_TAG, "No message specified for the scheduled notification");
            return;
        }

        if (bundle.getString("id") == null) {
            Log.e(LOG_TAG, "No notification ID specified for the scheduled notification");
            return;
        }

        double fireDate = bundle.getDouble("fireDate");
        if (fireDate == 0) {
            Log.e(LOG_TAG, "No date specified for the scheduled notification");
            return;
        }

        RNPushNotificationAttributes notificationAttributes = new RNPushNotificationAttributes(bundle);
        String id = notificationAttributes.getId();

        Log.d(LOG_TAG, "Storing push notification with id " + id);

        SharedPreferences.Editor editor = scheduledNotificationsPersistence.edit();
        editor.putString(id, notificationAttributes.toJson().toString());
        commit(editor);

        boolean isSaved = scheduledNotificationsPersistence.contains(id);
        if (!isSaved) {
            Log.e(LOG_TAG, "Failed to save " + id);
        }

        sendNotificationScheduledCore(bundle);
    }

    public void sendNotificationScheduledCore(Bundle bundle) {
        long fireDate = (long) bundle.getDouble("fireDate");

        // If the fireDate is in past, this will fire immediately and show the
        // notification to the user
        PendingIntent pendingIntent = toScheduleNotificationIntent(bundle);

        Log.d(LOG_TAG, String.format("Setting a notification with id %s at time %s",
                bundle.getString("id"), Long.toString(fireDate)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getAlarmManager().setExact(AlarmManager.RTC_WAKEUP, fireDate, pendingIntent);
        } else {
            getAlarmManager().set(AlarmManager.RTC_WAKEUP, fireDate, pendingIntent);
        }
    }

    private void setTimeoutCancelNotification(final Context appContext, final Bundle callHangUpTimeoutIntentBundle, long delay) {
        Runnable hangUpTimeoutRunable = new Runnable() {
            @Override
            public void run() {
                Intent actionIntent = new Intent(context, JSPushNotificationTask.class);
                actionIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                actionIntent.setAction(context.getPackageName() + "." + callHangUpTimeoutIntentBundle.getString("action"));
                actionIntent.putExtras(callHangUpTimeoutIntentBundle);
                appContext.startService(actionIntent);
            }
        };
        String janusGroupIdKey = callHangUpTimeoutIntentBundle.getString("janusGroupId");
        JSPushNotificationTask.setHangUpRunable(janusGroupIdKey, hangUpTimeoutRunable, delay);
    }

    public void sendToCallNotifications(Bundle bundle, boolean onlyForegroundService) {
        boolean isWithFillScreenIntent = RNPushNotification.isAndroidXOrHigher && !onlyForegroundService;
        Random randomIds = new Random();
        Class intentClass = getMainActivityClass();
        try {
            bundle.putBoolean("userInteraction", true);
            bundle.putBoolean("foreground", true);
            bundle.putBoolean("foregroundCall", true);
            if (intentClass == null) {
                Log.e(LOG_TAG, "No activity class found for the notification");
                return;
            }

            String title = bundle.getString("title");

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, isWithFillScreenIntent ? NOTIFICATION_CHANNEL_ID : NOTIFICATION_CHANNEL_CALL_ID)
                    .setContentTitle(title)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setOngoing(true)
                    .setColor(blueColor)
                    .setCategory(NotificationCompat.CATEGORY_CALL);

            Resources res = context.getResources();
            String packageName = context.getPackageName();
            String smallIcon = bundle.getString("smallIcon");

            int smallIconResId;

            if (smallIcon != null) {
                smallIconResId = res.getIdentifier(smallIcon, "mipmap", packageName);
            } else {
                smallIconResId = res.getIdentifier("ic_notification", "mipmap", packageName);
            }

            if (smallIconResId == 0) {
                smallIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);

                if (smallIconResId == 0) {
                    smallIconResId = android.R.drawable.ic_dialog_info;
                }
            }

            notificationBuilder.setSmallIcon(smallIconResId);

            if (isWithFillScreenIntent) {
                String contentText = bundle.getString("message");
                if (contentText != null) {
                    if (title == null) {
                        notificationBuilder.setContentTitle(contentText);
                    } else {
                        notificationBuilder.setContentText(contentText);
                    }
                }
            }

            Intent intent = new Intent(context, intentClass);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.putExtra(NOTIFICATION_BUNDLE, bundle);

            int pendingIntentId = randomIds.nextInt();
            PendingIntent contentPendingIntent = PendingIntent.getActivity(context, pendingIntentId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            notificationBuilder.setContentIntent(contentPendingIntent);

            if (isWithFillScreenIntent) {
                Intent fullScreenIntent = new Intent(context, intentClass);
                fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                fullScreenIntent.putExtra(NOTIFICATION_BUNDLE, bundle);

                int pendingFullScreenIntentId = randomIds.nextInt();
                PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(context, pendingFullScreenIntentId, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true);

                Intent answerIntent = new Intent(context, intentClass);
                answerIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                Bundle answerBundle = new Bundle(bundle);
                answerBundle.putBoolean("answer", true);
                answerBundle.putString(JSPushNotificationTask.BUNDLE_TASK_NAME_KEY, JSPushNotificationTask.START_CALL_TASK_KEY);
                answerIntent.putExtra(NOTIFICATION_BUNDLE, answerBundle);

                int pendingAnswerIntentId = randomIds.nextInt();
                PendingIntent answerPendingIntent = PendingIntent.getActivity(context, pendingAnswerIntentId, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                Intent declineIntent = new Intent(context, JSPushNotificationTask.class);
                Bundle declineBundle = new Bundle(bundle);
                declineBundle.putBoolean("decline", true);
                declineBundle.putString(JSPushNotificationTask.BUNDLE_TASK_NAME_KEY, JSPushNotificationTask.END_CALL_TASK_KEY);
                declineIntent.putExtras(declineBundle);

                int pendingDeclineIntentId = randomIds.nextInt();
                PendingIntent declinePendingIntent = PendingIntent.getService(context, pendingDeclineIntentId, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                notificationBuilder.addAction(0, "Accept", answerPendingIntent);
                notificationBuilder.addAction(0, "Decline", declinePendingIntent);
            }

            NotificationManager notificationManager = notificationManager();
            checkOrCreateChannel(notificationManager, !isWithFillScreenIntent, null);

            Notification notification = notificationBuilder.build();

            notification.flags = notification.flags | Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE;

            Intent startCallServiceIntent = new Intent(context, CallsService.class);
            Bundle startServiceBundle = new Bundle();
            startServiceBundle.putParcelable("notification", notification);
            startCallServiceIntent.putExtras(startServiceBundle);
            context.startService(startCallServiceIntent);

        } catch (Exception e) {
            Log.e(LOG_TAG, "failed to send push notification", e);
        }
    }

    public void sendToMessagingNotifications(Bundle bundle) {
        System.out.println("[sendToMessagingNotificatios][arguments]");
        System.out.println(bundle);

        try {
            Class intentClass = getMainActivityClass();
            if (intentClass == null) {
                Log.e(LOG_TAG, "No activity class found for the notification");
                return;
            }

            int notificationID = bundle.getInt("notificationID");
            String message_id = bundle.getString("message_id");

            if (message_id != null && RNPushNotificationMessageLine.isReceived(message_id)) {
                System.out.println("[sendToMessagingNotificatios][isExists]: " + message_id);
                return;
            }

            System.out.println("[sendToMessagingNotificatios][id]: " + notificationID);

            boolean isPrivateDialog = bundle.getBoolean("is_private");
            String dialog = bundle.getString("dialog");
            String notificationChannelType = bundle.getString("сhannelType");
            NotificationChannelManager.CHANNELS channelType = notificationChannelManager.getType(notificationChannelType);

            ArrayList<Bundle> allMessages = RNPushNotificationMessageLine.addLineToNotification(notificationID, bundle);

            NotificationCompat.Builder notificationBuilder =  new NotificationCompat.Builder(context, Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? notificationChannelManager.createChannelIfNotExists(channelType) : NOTIFICATION_CHANNEL_ID);

            NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(MESSAGING_STYLE_TEXT);

            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.O) {
                messagingStyle.setConversationTitle(dialog);
            }

            if (!isPrivateDialog) {
                messagingStyle.addMessage("", 0, dialog + "(" + allMessages.size() + " new messages)");
            }

            ArrayList<String> message_ids = new ArrayList<>();

            for(Bundle messageBundle : allMessages) {
                message_ids.add(messageBundle.getString("message_id"));

                messagingStyle.addMessage(messageBundle.getString("message"), 0, messageBundle.getString("sender"));
            }

            notificationBuilder
                    .setContentTitle(dialog)
                    .setContentText(dialog)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setStyle(messagingStyle)
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .setGroup(dialog)
                    .setColor(blueColor)
                    .setPriority(NotificationCompat.PRIORITY_MAX);

            Resources res = context.getResources();
            String packageName = context.getPackageName();

            Uri soundUri = getNotificationSound(bundle);
            if (soundUri != null) {
                notificationBuilder.setSound(soundUri);
            }

            String numberString = bundle.getString("number", hashMapDialogsToMessages.getCountOfMessage() + "");

            if (numberString != null) {
                notificationBuilder.setNumber(Integer.parseInt(numberString));
            }

            String smallIcon = bundle.getString("smallIcon");
            String largeIcon = bundle.getString("largeIcon");

            int smallIconResId;
            int largeIconResId;

            if (smallIcon != null) {
                smallIconResId = res.getIdentifier(smallIcon, "mipmap", packageName);
            } else {
                smallIconResId = res.getIdentifier("ic_notification", "mipmap", packageName);
            }

            if (smallIconResId == 0) {
                smallIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);

                if (smallIconResId == 0) {
                    smallIconResId = android.R.drawable.ic_dialog_info;
                }
            }

            if (largeIcon != null) {
                largeIconResId = res.getIdentifier(largeIcon, "mipmap", packageName);
            } else {
                largeIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);
            }

            Bitmap largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);

            if (largeIconResId != 0 && (largeIcon != null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)) {
                notificationBuilder.setLargeIcon(largeIconBitmap);
            }

            notificationBuilder.setSmallIcon(smallIconResId);

            if (!bundle.containsKey("vibrate") || bundle.getBoolean("vibrate")) {
                long vibration = bundle.containsKey("vibration") ? (long) bundle.getDouble("vibration") : DEFAULT_VIBRATION;
                if (vibration == 0)
                    vibration = DEFAULT_VIBRATION;
                notificationBuilder.setVibrate(new long[]{0, vibration});
            }

            Intent intent = new Intent(context, intentClass);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra(DELETE_MESSAGE, true);
            intent.putExtra(NOTIFICATION_BUNDLE, bundle);
            int pandingIntentId = message_id.hashCode();
            PendingIntent pendingIntent = PendingIntent.getActivity(context, pandingIntentId, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            notificationBuilder.setContentIntent(pendingIntent);

            Intent intentDeleteNotification = new Intent(context, DeleteNotification.class);
            intentDeleteNotification.putExtra(DELETE_MESSAGE, true);
            intentDeleteNotification.putExtra(NOTIFICATION_BUNDLE, bundle);
            PendingIntent pendingIntentDeleteNotification = PendingIntent.getBroadcast(context, pandingIntentId, intentDeleteNotification, 0);

            // notificationBuilder.setDeleteIntent(pendingIntentDeleteNotification);

            Bundle actionsBundle = bundle.getBundle("actions");
            Bundle actionBundle = new Bundle(bundle);
            actionBundle.putStringArrayList("message_ids", message_ids);

            if (actionsBundle != null) {
                // Need to display the action "REPLAY" on the left and the action "MARK AS READ" on the right in Notification
                List<String> actionKeyList = new ArrayList<String>(actionsBundle.keySet());
                Collections.reverse(actionKeyList);
                // No icon for now. The icon value of 0 shows no icon.
                int icon = 0;
                for (String actionName : actionKeyList) {
                    String jsBgTaskName = actionsBundle.getString(actionName);

                    Intent actionIntent = new Intent(context, JSPushNotificationTask.class);
                    actionIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    actionIntent.setAction(context.getPackageName() + "." + actionName);

                    System.out.println("[Action Bundle] " + actionBundle);

                    // Add "action" for later identifying which button gets pressed.
                    actionBundle.putString("action", actionName);
                    actionBundle.putString(JSPushNotificationTask.BUNDLE_TASK_NAME_KEY, jsBgTaskName);
                    actionIntent.putExtras(actionBundle);
                    int actionNotificationID = notificationID + (int)(System.currentTimeMillis() / 1000);
                    PendingIntent pendingActionIntent = PendingIntent.getService(context, actionNotificationID, actionIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                    if (jsBgTaskName.equals(JSPushNotificationTask.REPLY_TASK_KEY)) {
                        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
                        {
                            NotificationCompat.Action actionReplyEmpty = new NotificationCompat.Action.Builder(
                                    icon, actionName, pendingIntent) // just show activity
                                    .build();
                            notificationBuilder.addAction(actionReplyEmpty);
                        } else {
                            NotificationCompat.Action actionReply = new NotificationCompat.Action.Builder(
                                    icon, actionName, pendingActionIntent)
                                    .addRemoteInput(new RemoteInput.Builder(JSPushNotificationTask.REPLY_INPUT_KEY)
                                            .setLabel("Type your message")
                                            .build())
                                    .setAllowGeneratedReplies(true)
                                    .build();
                            notificationBuilder.addAction(actionReply);
                        }
                    } else {
                        notificationBuilder.addAction(icon, actionName, pendingActionIntent);
                    }
                }
            }

            Log.d("[NID]", "" + notificationID);
            System.out.println("[IntentBundle]: " + bundle);

            bundle.putBoolean("userInteraction", true);


            NotificationManager notificationManager = notificationManager();
//            checkOrCreateChannel(notificationManager, false, soundName);

            Notification notification = notificationBuilder.build();

            notificationManager.notify(notificationID, notification);


        }  catch (Exception e) {
            Log.e(LOG_TAG, "failed to send push notification", e);
        }
    }

    public void sendToGroupNotifications(Bundle bundle)
    {
      System.out.println("[sendToGroupNotifications][arguments]");
      System.out.println(bundle);

      try {
        Class intentClass = getMainActivityClass();
        if (intentClass == null) {
          Log.e(LOG_TAG, "No activity class found for the notification");
          return;
        }

        String message_id = bundle.getString("message_id");
        String message = bundle.getString("message");

        if (message == null && message_id == null) {
          // this happens when a 'data' notification is received - we do not synthesize a local notification in this case
          Log.d(LOG_TAG, "Cannot send to notification centre because there is no 'message' and 'message_id' field in: " + bundle);
          return;
        }

        String dialog = bundle.getString("dialog");
        String dialog_id = bundle.getString("dialog_id");

        if (dialog_id == null) {
          // this happens when a 'data' notification is received - we do not synthesize a local notification in this case
          Log.d(LOG_TAG, "Cannot send to notification centre because there is no 'dialog_id' field in: " + bundle);
          return;
        }

        String sender = bundle.getString("sender");
        String sender_id = bundle.getString("sender_id");

        if (sender_id == null) {
          // this happens when a 'data' notification is received - we do not synthesize a local notification in this case
          Log.d(LOG_TAG, "Cannot send to notification centre because there is no and 'sender_id' field in: " + bundle);
          return;
        }

        String notificationIdString = bundle.getString("id");
        if (notificationIdString == null) {
          Log.e(LOG_TAG, "No notification ID specified for the notification");
          return;
        }

          int notificationID = Integer.parseInt(notificationIdString);

          boolean success = hashMapDialogsToMessages.addMessage(dialog_id, new RNPushNotificationMessage(notificationID, sender_id, sender, message_id, message));
          if(!success)
          {
              return;
          }

          System.out.println("[Version]");
          System.out.println(Build.VERSION.SDK_INT);
          System.out.println(Build.VERSION_CODES.O);

          if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
          {
              sendToNotificationCentre(bundle);
              return;
          }

        Resources res = context.getResources();
        String packageName = context.getPackageName();

        String title = sender;
        if (title == null) {
          ApplicationInfo appInfo = context.getApplicationInfo();
          title = context.getPackageManager().getApplicationLabel(appInfo).toString();
        }

        int priority = NotificationCompat.PRIORITY_HIGH;
        int visibility = NotificationCompat.VISIBILITY_PRIVATE;

        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
          .setContentTitle(title)
          .setTicker(bundle.getString("ticker"))
          .setVisibility(visibility)
          .setPriority(priority)
          .setGroup(NOTIFICATION_GROUP_ID)
          .setAutoCancel(bundle.getBoolean("autoCancel", true));

        notification.setContentText(message);


        String numberString = bundle.getString("number", hashMapDialogsToMessages.getCountOfMessage() + "");

        if (numberString != null) {
          notification.setNumber(Integer.parseInt(numberString));
        }

        String smallIcon = bundle.getString("smallIcon");
        String largeIcon = bundle.getString("largeIcon");

        int smallIconResId;
        int largeIconResId;

        if (smallIcon != null) {
          smallIconResId = res.getIdentifier(smallIcon, "mipmap", packageName);
        } else {
          smallIconResId = res.getIdentifier("ic_notification", "mipmap", packageName);
        }

        if (smallIconResId == 0) {
          smallIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);

          if (smallIconResId == 0) {
            smallIconResId = android.R.drawable.ic_dialog_info;
          }
        }

        if (largeIcon != null) {
            largeIconResId = res.getIdentifier(largeIcon, "mipmap", packageName);
        } else {
            largeIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);
        }

        Bitmap largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);

        if (largeIconResId != 0 && (largeIcon != null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)) {
          notification.setLargeIcon(largeIconBitmap);
        }

        notification.setSmallIcon(smallIconResId);

          bundle.putBoolean("userInteraction", true);

          Uri soundUri = getNotificationSound(bundle);
          if (soundUri != null) {
              notification.setSound(soundUri);
          }

        if (bundle.containsKey("ongoing") || bundle.getBoolean("ongoing")) {
          notification.setOngoing(bundle.getBoolean("ongoing"));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          notification.setCategory(NotificationCompat.CATEGORY_CALL);

          String color = bundle.getString("color");
          int defaultColor = this.config.getNotificationColor();
          if (color != null) {
            notification.setColor(Color.parseColor(color));
          } else if (defaultColor != -1) {
            notification.setColor(defaultColor);
          }
        }

          Intent intent = new Intent(context, intentClass);
          intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
          intent.putExtra(DELETE_MESSAGE, true);
          intent.putExtra(NOTIFICATION_BUNDLE, bundle);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, notificationID, intent,
          PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationManager notificationManager = notificationManager();
        checkOrCreateChannel(notificationManager, false, null);

        notification.setContentIntent(pendingIntent);

          Intent intentDeleteNotification = new Intent(context, DeleteNotification.class);
          intentDeleteNotification.putExtra(DELETE_MESSAGE, true);
          intentDeleteNotification.putExtra(NOTIFICATION_BUNDLE, bundle);
          PendingIntent pendingIntentDeleteNotification = PendingIntent.getBroadcast(context, 0, intentDeleteNotification, 0);
          notification.setDeleteIntent(pendingIntentDeleteNotification);

        if (!bundle.containsKey("vibrate") || bundle.getBoolean("vibrate")) {
          long vibration = bundle.containsKey("vibration") ? (long) bundle.getDouble("vibration") : DEFAULT_VIBRATION;
          if (vibration == 0)
            vibration = DEFAULT_VIBRATION;
          notification.setVibrate(new long[]{0, vibration});
        }

          JSONArray actionsArray = null;
          try {
              actionsArray = bundle.getString("actions") != null ? new JSONArray(bundle.getString("actions")) : null;
              Log.d(LOG_TAG, "[Actions]: " + actionsArray);
          } catch (JSONException e) {
              Log.e(LOG_TAG, "Exception while converting actions to JSON object.", e);
          }

          if (actionsArray != null) {
              // No icon for now. The icon value of 0 shows no icon.
              int icon = 0;

              // Add button for each actions.
              for (int i = 0; i < actionsArray.length(); i++) {
                  String action;
                  try {
                      action = actionsArray.getString(i);
                  } catch (JSONException e) {
                      Log.e(LOG_TAG, "Exception while getting action from actionsArray.", e);
                      continue;
                  }

                  Intent actionIntent = new Intent(context, JSPushNotificationTask.class);
                  actionIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                  actionIntent.setAction(context.getPackageName() + "." + action);

                  System.out.println("[Action Bundle]: " + bundle);

                  // Add "action" for later identifying which button gets pressed.
                  bundle.putString("action", action);
                  bundle.putString(JSPushNotificationTask.BUNDLE_TASK_NAME_KEY, JSPushNotificationTask.MARK_AS_READ_TASK_KEY);
                  actionIntent.putExtras(bundle);
                  int actionNotificationID = notificationID + (int)(System.currentTimeMillis() / 1000);
                  PendingIntent pendingActionIntent = PendingIntent.getService(context, actionNotificationID, actionIntent,
                          PendingIntent.FLAG_UPDATE_CURRENT);
                  notification.addAction(icon, action, pendingActionIntent);
              }
          }

        // Remove the notification from the shared preferences once it has been shown
        // to avoid showing the notification again when the phone is rebooted. If the
        // notification is not removed, then every time the phone is rebooted, we will
        // try to reschedule all the notifications stored in shared preferences and since
        // these notifications will be in the past time, they will be shown immediately
        // to the user which we shouldn't do. So, remove the notification from the shared
        // preferences once it has been shown to the user. If it is a repeating notification
        // it will be scheduled again.
        if (scheduledNotificationsPersistence.getString(notificationIdString, null) != null) {
          SharedPreferences.Editor editor = scheduledNotificationsPersistence.edit();
          editor.remove(notificationIdString);
          commit(editor);
        }

        Notification info = notification.build();
        info.defaults |= Notification.DEFAULT_LIGHTS;

        if (bundle.containsKey("tag")) {
          String tag = bundle.getString("tag");
          notificationManager.notify(tag, notificationID, info);
        } else {
          notificationManager.notify(notificationID, info);
        }

          Intent intentDelete = new Intent(context, DeleteSummaryNotification.class);
          PendingIntent pendingIntentDelete = PendingIntent.getBroadcast(context, 0, intentDelete, 0);

          Intent intentContext = new Intent(context, getMainActivityClass());
          intentContext.putExtra(CLEAR_MESSAGE, true);
          PendingIntent pendingIntentContent = PendingIntent.getActivity(context, NOTIFICATION_WITH_GROUP_ID, intentContext,
                  PendingIntent.FLAG_UPDATE_CURRENT);

        Notification summaryNotification =
          new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(hashMapDialogsToMessages.getCountOfMessage() + " new messages")
            .setContentText(hashMapDialogsToMessages.getCountOfMessage() + " new messages")
            .setSmallIcon(smallIconResId)
            .setStyle(new NotificationCompat.InboxStyle()
              .setBigContentTitle(hashMapDialogsToMessages.getCountOfMessage() + " new messages")
              .setSummaryText(hashMapDialogsToMessages.getCountOfMessage() + " new messages from " +
                  (hashMapDialogsToMessages.getCountOfDialogs() == 1 ?  "1 chat" : hashMapDialogsToMessages.getCountOfDialogs() + " chats")))
            .setGroup(NOTIFICATION_GROUP_ID)
            .setGroupSummary(true)
            .setContentIntent(pendingIntentContent)
            .setAutoCancel(true)
            .setDeleteIntent(pendingIntentDelete)
            .build();

        notificationManager.notify(NOTIFICATION_WITH_GROUP_ID, summaryNotification);

        // Can't use setRepeating for recurring notifications because setRepeating
        // is inexact by default starting API 19 and the notifications are not fired
        // at the exact time. During testing, it was found that notifications could
        // late by many minutes.
        this.scheduleNextNotificationIfRepeating(bundle);
      } catch (Exception e) {
        Log.e(LOG_TAG, "failed to send push notification", e);
      }
    }

    public void sendToNotificationCentre(Bundle bundle) {
        try {
            Class intentClass = getMainActivityClass();
            if (intentClass == null) {
                Log.e(LOG_TAG, "No activity class found for the notification");
                return;
            }

            if (bundle.getString("message") == null) {
                // this happens when a 'data' notification is received - we do not synthesize a local notification in this case
                Log.d(LOG_TAG, "Cannot send to notification centre because there is no 'message' field in: " + bundle);
                return;
            }

            String notificationIdString = bundle.getString("id");
            if (notificationIdString == null) {
                Log.e(LOG_TAG, "No notification ID specified for the notification");
                return;
            }

            Resources res = context.getResources();
            String packageName = context.getPackageName();

            String title = bundle.getString("title");
            if (title == null) {
                ApplicationInfo appInfo = context.getApplicationInfo();
                title = context.getPackageManager().getApplicationLabel(appInfo).toString();
            }

            int priority = NotificationCompat.PRIORITY_HIGH;
            final String priorityString = bundle.getString("priority");

            if (priorityString != null) {
                switch(priorityString.toLowerCase()) {
                    case "max":
                        priority = NotificationCompat.PRIORITY_MAX;
                        break;
                    case "high":
                        priority = NotificationCompat.PRIORITY_HIGH;
                        break;
                    case "low":
                        priority = NotificationCompat.PRIORITY_LOW;
                        break;
                    case "min":
                        priority = NotificationCompat.PRIORITY_MIN;
                        break;
                    case "default":
                        priority = NotificationCompat.PRIORITY_DEFAULT;
                        break;
                    default:
                        priority = NotificationCompat.PRIORITY_HIGH;
                }
            }

            int visibility = NotificationCompat.VISIBILITY_PRIVATE;
            final String visibilityString = bundle.getString("visibility");

            if (visibilityString != null) {
                switch(visibilityString.toLowerCase()) {
                    case "private":
                        visibility = NotificationCompat.VISIBILITY_PRIVATE;
                        break;
                    case "public":
                        visibility = NotificationCompat.VISIBILITY_PUBLIC;
                        break;
                    case "secret":
                        visibility = NotificationCompat.VISIBILITY_SECRET;
                        break;
                    default:
                        visibility = NotificationCompat.VISIBILITY_PRIVATE;
                }
            }

            NotificationCompat.Builder notification = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(title)
                    .setTicker(bundle.getString("ticker"))
                    .setVisibility(visibility)
                    .setPriority(priority)
                    .setAutoCancel(true);

            String group = bundle.getString("group");
            if (group != null) {
                notification.setGroup(group);
            }

            notification.setContentText(bundle.getString("message"));

            String largeIcon = bundle.getString("largeIcon");

            String subText = bundle.getString("subText");

            if (subText != null) {
                notification.setSubText(subText);
            }

            String numberString = bundle.getString("number");

            if (numberString != null) {
                notification.setNumber(Integer.parseInt(numberString));
            }

            int smallIconResId;
            int largeIconResId;

            String smallIcon = bundle.getString("smallIcon");

            if (smallIcon != null) {
                smallIconResId = res.getIdentifier(smallIcon, "mipmap", packageName);
            } else {
                smallIconResId = res.getIdentifier("ic_notification", "mipmap", packageName);
            }

            if (smallIconResId == 0) {
                smallIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);

                if (smallIconResId == 0) {
                    smallIconResId = android.R.drawable.ic_dialog_info;
                }
            }

            if (largeIcon != null) {
                largeIconResId = res.getIdentifier(largeIcon, "mipmap", packageName);
            } else {
                largeIconResId = res.getIdentifier("ic_notification", "mipmap", packageName);
            }

            if (largeIconResId == 0) {
                largeIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);

                if (largeIconResId == 0) {
                    largeIconResId = android.R.drawable.ic_dialog_info;
                }
            }

            Bitmap largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);

            if (largeIconResId != 0 && (largeIcon != null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)) {
                notification.setLargeIcon(largeIconBitmap);
            }

            notification.setSmallIcon(smallIconResId);
            
            String bigText = bundle.getString("bigText");

            if (bigText == null) {
                bigText = bundle.getString("message");
            }

            notification.setStyle(new NotificationCompat.BigTextStyle().bigText(bigText));

            Intent intent = new Intent(context, intentClass);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            bundle.putBoolean("userInteraction", true);
            intent.putExtra("notification", bundle);

            if (!bundle.containsKey("playSound") || bundle.getBoolean("playSound")) {
                Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                String soundName = bundle.getString("soundName");
                if (soundName != null) {
                    if (!"default".equalsIgnoreCase(soundName)) {

                        // sound name can be full filename, or just the resource name.
                        // So the strings 'my_sound.mp3' AND 'my_sound' are accepted
                        // The reason is to make the iOS and android javascript interfaces compatible

                        int resId;
                        if (context.getResources().getIdentifier(soundName, "raw", context.getPackageName()) != 0) {
                            resId = context.getResources().getIdentifier(soundName, "raw", context.getPackageName());
                        } else {
                            soundName = soundName.substring(0, soundName.lastIndexOf('.'));
                            resId = context.getResources().getIdentifier(soundName, "raw", context.getPackageName());
                        }

                        soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + resId);
                    }
                }
                notification.setSound(soundUri);
            }

            if (bundle.containsKey("ongoing") || bundle.getBoolean("ongoing")) {
                notification.setOngoing(bundle.getBoolean("ongoing"));
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                notification.setCategory(NotificationCompat.CATEGORY_CALL);

                String color = bundle.getString("color");
                int defaultColor = this.config.getNotificationColor();
                if (color != null) {
                    notification.setColor(Color.parseColor(color));
                } else if (defaultColor != -1) {
                    notification.setColor(defaultColor);
                }
            }

            int notificationID = Integer.parseInt(notificationIdString);

            PendingIntent pendingIntent = PendingIntent.getActivity(context, notificationID, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationManager notificationManager = notificationManager();
            checkOrCreateChannel(notificationManager, false, null);

            notification.setContentIntent(pendingIntent);

            if (!bundle.containsKey("vibrate") || bundle.getBoolean("vibrate")) {
                long vibration = bundle.containsKey("vibration") ? (long) bundle.getDouble("vibration") : DEFAULT_VIBRATION;
                if (vibration == 0)
                    vibration = DEFAULT_VIBRATION;
                notification.setVibrate(new long[]{0, vibration});
            }

            JSONArray actionsArray = null;
            try {
                actionsArray = bundle.getString("actions") != null ? new JSONArray(bundle.getString("actions")) : null;
                Log.d(LOG_TAG, "[Actions]: " + actionsArray);
            } catch (JSONException e) {
                Log.e(LOG_TAG, "Exception while converting actions to JSON object.", e);
            }

            if (actionsArray != null) {
                // No icon for now. The icon value of 0 shows no icon.
                int icon = 0;

                // Add button for each actions.
                for (int i = 0; i < actionsArray.length(); i++) {
                    String action;
                    try {
                        action = actionsArray.getString(i);
                    } catch (JSONException e) {
                        Log.e(LOG_TAG, "Exception while getting action from actionsArray.", e);
                        continue;
                    }

                    Intent actionIntent = new Intent(context, JSPushNotificationTask.class);
                    actionIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    actionIntent.setAction(context.getPackageName() + "." + action);

                    System.out.println("[Action Bundle]: " + bundle);

                    // Add "action" for later identifying which button gets pressed.
                    bundle.putString("action", action);
                    bundle.putString(JSPushNotificationTask.BUNDLE_TASK_NAME_KEY, JSPushNotificationTask.MARK_AS_READ_TASK_KEY);
                    actionIntent.putExtras(bundle);
                    int actionNotificationID = notificationID + (int)(System.currentTimeMillis() / 1000);
                    PendingIntent pendingActionIntent = PendingIntent.getService(context, actionNotificationID, actionIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                    notification.addAction(icon, action, pendingActionIntent);
                }
            }

            // Remove the notification from the shared preferences once it has been shown
            // to avoid showing the notification again when the phone is rebooted. If the
            // notification is not removed, then every time the phone is rebooted, we will
            // try to reschedule all the notifications stored in shared preferences and since
            // these notifications will be in the past time, they will be shown immediately
            // to the user which we shouldn't do. So, remove the notification from the shared
            // preferences once it has been shown to the user. If it is a repeating notification
            // it will be scheduled again.
            if (scheduledNotificationsPersistence.getString(notificationIdString, null) != null) {
                SharedPreferences.Editor editor = scheduledNotificationsPersistence.edit();
                editor.remove(notificationIdString);
                commit(editor);
            }

            Notification info = notification.build();
            info.defaults |= Notification.DEFAULT_LIGHTS;

            if (bundle.containsKey("tag")) {
                String tag = bundle.getString("tag");
                notificationManager.notify(tag, notificationID, info);
            } else {
                notificationManager.notify(notificationID, info);
            }

            // Can't use setRepeating for recurring notifications because setRepeating
            // is inexact by default starting API 19 and the notifications are not fired
            // at the exact time. During testing, it was found that notifications could
            // late by many minutes.
            this.scheduleNextNotificationIfRepeating(bundle);
        } catch (Exception e) {
            Log.e(LOG_TAG, "failed to send push notification", e);
        }
    }

    private void scheduleNextNotificationIfRepeating(Bundle bundle) {
        String repeatType = bundle.getString("repeatType");
        long repeatTime = (long) bundle.getDouble("repeatTime");

        if (repeatType != null) {
            long fireDate = (long) bundle.getDouble("fireDate");

            boolean validRepeatType = Arrays.asList("time", "week", "day", "hour", "minute").contains(repeatType);

            // Sanity checks
            if (!validRepeatType) {
                Log.w(LOG_TAG, String.format("Invalid repeatType specified as %s", repeatType));
                return;
            }

            if ("time".equals(repeatType) && repeatTime <= 0) {
                Log.w(LOG_TAG, "repeatType specified as time but no repeatTime " +
                        "has been mentioned");
                return;
            }

            long newFireDate = 0;

            switch (repeatType) {
                case "time":
                    newFireDate = fireDate + repeatTime;
                    break;
                case "week":
                    newFireDate = fireDate + 7 * ONE_DAY;
                    break;
                case "day":
                    newFireDate = fireDate + ONE_DAY;
                    break;
                case "hour":
                    newFireDate = fireDate + ONE_HOUR;
                    break;
                case "minute":
                    newFireDate = fireDate + ONE_MINUTE;
                    break;
            }

            // Sanity check, should never happen
            if (newFireDate != 0) {
                Log.d(LOG_TAG, String.format("Repeating notification with id %s at time %s",
                        bundle.getString("id"), Long.toString(newFireDate)));
                bundle.putDouble("fireDate", newFireDate);
                this.sendNotificationScheduled(bundle);
            }
        }
    }

    public void clearNotifications() {
        Log.i(LOG_TAG, "Clearing alerts from the notification centre");

        NotificationManager notificationManager = notificationManager();
        notificationManager.cancelAll();
    }

    public void clearNotification(int notificationID) {
        Log.i(LOG_TAG, "Clearing notification: " + notificationID);

        NotificationManager notificationManager = notificationManager();
        notificationManager.cancel(notificationID);
    }

    public void cancelAllScheduledNotifications() {
        Log.i(LOG_TAG, "Cancelling all notifications");

        for (String id : scheduledNotificationsPersistence.getAll().keySet()) {
            cancelScheduledNotification(id);
        }
    }

    public void cancelScheduledNotification(ReadableMap userInfo) {
        for (String id : scheduledNotificationsPersistence.getAll().keySet()) {
            try {
                String notificationAttributesJson = scheduledNotificationsPersistence.getString(id, null);
                if (notificationAttributesJson != null) {
                    RNPushNotificationAttributes notificationAttributes = fromJson(notificationAttributesJson);
                    if (notificationAttributes.matches(userInfo)) {
                        cancelScheduledNotification(id);
                    }
                }
            } catch (JSONException e) {
                Log.w(LOG_TAG, "Problem dealing with scheduled notification " + id, e);
            }
        }
    }

    private void cancelScheduledNotification(String notificationIDString) {
        Log.i(LOG_TAG, "Cancelling notification: " + notificationIDString);

        // remove it from the alarm manger schedule
        Bundle b = new Bundle();
        b.putString("id", notificationIDString);
        getAlarmManager().cancel(toScheduleNotificationIntent(b));

        if (scheduledNotificationsPersistence.contains(notificationIDString)) {
            // remove it from local storage
            SharedPreferences.Editor editor = scheduledNotificationsPersistence.edit();
            editor.remove(notificationIDString);
            commit(editor);
        } else {
            Log.w(LOG_TAG, "Unable to find notification " + notificationIDString);
        }

        // removed it from the notification center
        NotificationManager notificationManager = notificationManager();

        notificationManager.cancel(Integer.parseInt(notificationIDString));
    }

    private NotificationManager notificationManager() {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static void commit(SharedPreferences.Editor editor) {
        if (Build.VERSION.SDK_INT < 9) {
            editor.commit();
        } else {
            editor.apply();
        }
    }

    public Uri getNotificationSound(Bundle bundle) {
        String soundName = bundle.getString("soundName");
        boolean playSound = bundle.getBoolean("playSound", true);
        if (!playSound) {
            return null;
        }
        return ChannelSettings.getSoundUri(context, soundName);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void setNotificationChanneSound(NotificationChannel channel, String soundName) {
        if (soundName == null) {
            return;
        }
        int resId;
        if (context.getResources().getIdentifier(soundName, "raw", context.getPackageName()) != 0) {
            resId = context.getResources().getIdentifier(soundName, "raw", context.getPackageName());
        } else {
            int soundExtDotIndex = soundName.lastIndexOf('.');
            if (soundExtDotIndex != -1) {
                soundName = soundName.substring(0, soundName.lastIndexOf('.'));
                resId = context.getResources().getIdentifier(soundName, "raw", context.getPackageName());
            } else {
                return;
            }
        }
        Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + resId);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build();
        channel.setSound(soundUri, audioAttributes);
    }

    private static boolean channelCreated = false;
    private static boolean channelCallCreated = false;
    private void checkOrCreateChannel(NotificationManager manager, boolean isCallChannel, String soundName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;
        if (isCallChannel ? channelCallCreated : channelCreated)
            return;
        if (manager == null)
            return;


        int importance = isCallChannel ? NotificationManager.IMPORTANCE_DEFAULT : NotificationManager.IMPORTANCE_HIGH;

        NotificationChannel channel = new NotificationChannel(isCallChannel ? NOTIFICATION_CHANNEL_CALL_ID : NOTIFICATION_CHANNEL_ID, this.config.getChannelName(), importance);
        channel.setDescription(this.config.getChannelDescription());
        if (!isCallChannel) {
            channel.enableLights(true);
            channel.enableVibration(true);
        } else {
            channel.setSound(null, null);
        }
        setNotificationChanneSound(channel, soundName);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        manager.createNotificationChannel(channel);
        if (isCallChannel) {
            channelCallCreated = true;
        } else {
            channelCreated = true;
        }
    }
}
