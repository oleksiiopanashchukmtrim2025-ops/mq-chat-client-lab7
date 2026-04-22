package org.example;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        ChatClient client = new ChatClient();

        try {
            client.connect();
            System.out.println("Connected to server");

            Scanner scanner = new Scanner(System.in);

            while (true) {
                System.out.print("> ");
                String line = scanner.nextLine().trim();

                client.updateLastActionTime();

                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\s+", 3);
                String command = parts[0];

                try {
                    switch (command) {
                        case "ping":
                            client.ping();
                            break;

                        case "echo":
                            if (line.length() <= 5) {
                                System.out.println("Usage: echo <text>");
                            } else {
                                client.echo(line.substring(5));
                            }
                            break;

                        case "login":
                            if (parts.length < 3) {
                                System.out.println("Usage: login <user> <password>");
                            } else {
                                client.login(parts[1], parts[2]);
                            }
                            break;

                        case "list":
                            client.listUsers();
                            break;

                        case "msg":
                            if (parts.length < 3) {
                                System.out.println("Usage: msg <user> <text>");
                            } else {
                                client.sendMessageToUser(parts[1], parts[2]);
                            }
                            break;

                        case "file":
                            if (parts.length < 3) {
                                System.out.println("Usage: file <user> <path>");
                            } else {
                                client.sendFileToUser(parts[1], parts[2]);
                            }
                            break;

                        case "exit":
                            client.exit();
                            System.out.println("Client closed");
                            return;

                        default:
                            System.out.println("Unknown command");
                    }
                } catch (Exception e) {
                    System.out.println("Command error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("Failed to connect: " + e.getMessage());
            client.close();
        }
    }
}