package org.example;

import lpi.server.mq.FileInfo;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.TextMessage;
import java.io.FileOutputStream;

public class FileReceiver implements MessageListener {

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof ObjectMessage) {
                Object obj = ((ObjectMessage) message).getObject();

                if (obj instanceof FileInfo) {
                    FileInfo fileInfo = (FileInfo) obj;

                    String fileName = fileInfo.getFilename();
                    byte[] content = fileInfo.getFileContent();

                    try (FileOutputStream fos = new FileOutputStream("received_" + fileName)) {
                        fos.write(content);
                    }

                    System.out.println();
                    System.out.println("[NEW FILE] Saved as received_" + fileName);
                }
            } else if (message instanceof TextMessage) {
                System.out.println("[Server]: " + ((TextMessage) message).getText());
            } else {
                System.out.println("Unexpected file message type: " + message.getClass());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}