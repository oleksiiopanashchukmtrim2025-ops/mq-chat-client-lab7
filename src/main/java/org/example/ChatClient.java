package org.example;

import lpi.server.mq.FileInfo;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

public class ChatClient {
    private static final String BROKER_URL = "tcp://localhost:61616";

    private Connection connection;
    private Session requestSession;
    private Session messageSession;
    private Session fileSession;

    private MessageConsumer messageConsumer;
    private MessageConsumer fileConsumer;

    private String currentUser;
    private long lastActionTime = System.currentTimeMillis();

    public void connect() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(BROKER_URL);
        factory.setTrustedPackages(Arrays.asList("lpi.server.mq"));

        connection = factory.createConnection();
        connection.start();

        requestSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        messageSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        fileSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        setupAsyncReceivers();
    }

    private void setupAsyncReceivers() throws Exception {
        Destination msgQueue = messageSession.createQueue("chat.messages");
        messageConsumer = messageSession.createConsumer(msgQueue);
        messageConsumer.setMessageListener(new MessageReceiver(this));

        Destination filesQueue = fileSession.createQueue("chat.files");
        fileConsumer = fileSession.createConsumer(filesQueue);
        fileConsumer.setMessageListener(new FileReceiver());
    }

    public Session getRequestSession() {
        return requestSession;
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(String currentUser) {
        this.currentUser = currentUser;
    }

    public void updateLastActionTime() {
        lastActionTime = System.currentTimeMillis();
    }

    public boolean isIdleMoreThan5Minutes() {
        return System.currentTimeMillis() - lastActionTime > 5 * 60 * 1000;
    }

    public Message sendAndReceive(String queueName, Message request) throws Exception {
        Destination targetQueue = requestSession.createQueue(queueName);
        Destination replyQueue = requestSession.createTemporaryQueue();

        request.setJMSReplyTo(replyQueue);

        MessageProducer producer = null;
        MessageConsumer consumer = null;

        try {
            producer = requestSession.createProducer(targetQueue);
            consumer = requestSession.createConsumer(replyQueue);

            producer.send(request);

            Message response = consumer.receive(2000);
            if (response == null) {
                throw new RuntimeException("No response from server");
            }
            return response;
        } finally {
            if (consumer != null) consumer.close();
            if (producer != null) producer.close();
        }
    }

    public void ping() throws Exception {
        Message request = requestSession.createMessage();
        Message response = sendAndReceive("chat.diag.ping", request);

        if (response instanceof TextMessage) {
            System.out.println(((TextMessage) response).getText());
        } else {
            System.out.println("Ping success");
        }
    }

    public void echo(String text) throws Exception {
        TextMessage request = requestSession.createTextMessage(text);
        Message response = sendAndReceive("chat.diag.echo", request);

        if (response instanceof TextMessage) {
            System.out.println("Echo from server: " + ((TextMessage) response).getText());
        } else {
            throw new RuntimeException("Unexpected response type");
        }
    }

    public void login(String login, String password) throws Exception {
        MapMessage request = requestSession.createMapMessage();
        request.setString("login", login);
        request.setString("password", password);

        Message response = sendAndReceive("chat.login", request);

        if (response instanceof MapMessage) {
            MapMessage map = (MapMessage) response;
            boolean success = map.getBoolean("success");
            String message = map.getString("message");

            if (success) {
                currentUser = login;
                System.out.println("Login successful: " + message);
            } else {
                System.out.println("Login failed: " + message);
            }
        } else if (response instanceof TextMessage) {
            System.out.println(((TextMessage) response).getText());
        } else {
            throw new RuntimeException("Unexpected response type");
        }
    }

    public void listUsers() throws Exception {
        Message request = requestSession.createMessage();
        Message response = sendAndReceive("chat.listUsers", request);

        if (response instanceof ObjectMessage) {
            Object obj = ((ObjectMessage) response).getObject();
            if (obj instanceof String[]) {
                String[] users = (String[]) obj;
                System.out.println("Online users:");
                for (String user : users) {
                    System.out.println("- " + user);
                }
            } else {
                throw new RuntimeException("Unexpected object inside ObjectMessage");
            }
        } else if (response instanceof MapMessage) {
            MapMessage map = (MapMessage) response;
            System.out.println("Error: " + map.getString("message"));
        } else if (response instanceof TextMessage) {
            System.out.println("Error: " + ((TextMessage) response).getText());
        } else {
            throw new RuntimeException("Unexpected response type");
        }
    }

    public void sendMessageToUser(String receiver, String text) throws Exception {
        if (currentUser == null) {
            System.out.println("Login first");
            return;
        }

        MapMessage request = requestSession.createMapMessage();
        request.setString("receiver", receiver);
        request.setString("message", text);

        Message response = sendAndReceive("chat.sendMessage", request);

        if (response instanceof MapMessage) {
            MapMessage map = (MapMessage) response;
            boolean success = map.getBoolean("success");
            String message = map.getString("message");

            if (success) {
                System.out.println("Message sent successfully");
            } else {
                System.out.println("Failed to send message: " + message);
            }
        } else if (response instanceof TextMessage) {
            System.out.println("Error: " + ((TextMessage) response).getText());
        } else {
            throw new RuntimeException("Unexpected response type");
        }
    }

    public void sendFileToUser(String receiver, String path) throws Exception {
        if (currentUser == null) {
            System.out.println("Login first");
            return;
        }

        File file = new File(path);
        if (!file.exists()) {
            System.out.println("File not found");
            return;
        }

        byte[] content = Files.readAllBytes(file.toPath());

        FileInfo fileInfo = new FileInfo();
        fileInfo.setReceiver(receiver);
        fileInfo.setSender(currentUser);
        fileInfo.setFilename(file.getName());
        fileInfo.setFileContent(content);

        ObjectMessage request = requestSession.createObjectMessage(fileInfo);

        Message response = sendAndReceive("chat.sendFile", request);

        if (response instanceof MapMessage) {
            MapMessage map = (MapMessage) response;
            boolean success = map.getBoolean("success");
            String message = map.getString("message");

            if (success) {
                System.out.println("File sent successfully");
            } else {
                System.out.println("Failed to send file: " + message);
            }
        } else if (response instanceof TextMessage) {
            System.out.println("Error: " + ((TextMessage) response).getText());
        } else {
            throw new RuntimeException("Unexpected response type");
        }
    }

    public void exit() {
        try {
            Message request = requestSession.createMessage();
            sendAndReceive("chat.exit", request);
        } catch (Exception e) {
            System.out.println("Error during exit: " + e.getMessage());
        } finally {
            close();
        }
    }

    public void close() {
        try { if (messageConsumer != null) messageConsumer.close(); } catch (Exception ignored) {}
        try { if (fileConsumer != null) fileConsumer.close(); } catch (Exception ignored) {}
        try { if (messageSession != null) messageSession.close(); } catch (Exception ignored) {}
        try { if (fileSession != null) fileSession.close(); } catch (Exception ignored) {}
        try { if (requestSession != null) requestSession.close(); } catch (Exception ignored) {}
        try { if (connection != null) connection.close(); } catch (Exception ignored) {}
    }
}