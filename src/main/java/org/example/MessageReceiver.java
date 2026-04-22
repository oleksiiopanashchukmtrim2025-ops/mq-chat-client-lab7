package org.example;

import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

public class MessageReceiver implements MessageListener {
    private final ChatClient client;

    public MessageReceiver(ChatClient client) {
        this.client = client;
    }

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof MapMessage) {
                MapMessage map = (MapMessage) message;
                String sender = map.getString("sender");
                String text = map.getString("message");

                System.out.println();
                System.out.println("[NEW MESSAGE] " + sender + ": " + text);

                if (client.getCurrentUser() != null && client.isIdleMoreThan5Minutes()) {
                    client.sendMessageToUser(sender, "Sorry, I’m AFK, will answer ASAP");
                }
            } else if (message instanceof TextMessage) {
                System.out.println("[Server]: " + ((TextMessage) message).getText());
            } else {
                System.out.println("Unexpected message type: " + message.getClass());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}