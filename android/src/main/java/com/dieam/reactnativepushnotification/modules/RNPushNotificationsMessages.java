package com.dieam.reactnativepushnotification.modules;

import java.util.ArrayList;
import java.util.HashMap;

public class RNPushNotificationsMessages {

  public int countOfmessage = 0;

  public HashMap<String, ArrayList<RNPushNotificationMessage>> messageHashMap = new HashMap<String, ArrayList<RNPushNotificationMessage>>();

  public RNPushNotificationsMessages()
  {
    clear();
  }

  public void clear()
  {
    countOfmessage = 0;
    messageHashMap.clear();
  }

  public boolean addMessage(String dialog_id, RNPushNotificationMessage message)
  {
    System.out.println("[addMessage][called]");
    if (messageHashMap.get(dialog_id) == null)
    {
      ArrayList<RNPushNotificationMessage> newMessageInDialog = new ArrayList<RNPushNotificationMessage>();
      newMessageInDialog.add(message);

      messageHashMap.put(dialog_id, newMessageInDialog);
      countOfmessage++;
      System.out.println(messageHashMap);
      System.out.println("[addMessage][called]");
      return true;
    } else {
      // check double messages message_id
      ArrayList<RNPushNotificationMessage> existingMessages = messageHashMap.get(dialog_id);

      for(int i = 0; i < existingMessages.size(); i++)
      {
        System.out.println("[addMessage][isEquals] " + existingMessages.get(i).message_id + " " + message.message_id);
        if (existingMessages.get(i).message_id.equals(message.message_id))
        {
          System.out.println("[addMessage][isEquals][TRUE]");
          return false;
        }
      }

      messageHashMap.get(dialog_id).add(message);
      countOfmessage++;
      System.out.println(messageHashMap);
      return true;
    }
  }

  public void deleteMessage(String dialog_id, String message_id)
  {
    if (messageHashMap.size() <= 0)
    {
      return;
    }

    if (messageHashMap.get(dialog_id) != null)
    {
      ArrayList<RNPushNotificationMessage> listOfMessages = messageHashMap.get(dialog_id);
      for(int i = 0; i < listOfMessages.size(); i++)
      {
        if (listOfMessages.get(i).message_id == message_id)
        {
          listOfMessages.remove(i);
          countOfmessage--;
          return;
        }
      }
    }
  }

  public int getCountOfDialogs()
  {
    return messageHashMap.size();
  }

  public int getCountOfMessage()
  {
    return countOfmessage;
  }

}
