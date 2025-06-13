package org.example.Servidor;
import java.io.*;
import java.net.*;
import java.awt.Color;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {

    private static final int PORT = 12345;
    private static ConcurrentHashMap<PrintWriter, ClientInfo> clientMap = new ConcurrentHashMap<>();

    // Clase interna para almacenar información del cliente
    private static class ClientInfo {
        String name;
        String colorHex; // Almacenaremos el color como String hexadecimal (ej. "#RRGGBB")

        public ClientInfo(String name, String colorHex) {
            this.name = name;
            this.colorHex = colorHex;
        }
    }

    public static void main(String[] args) {
        System.out.println("Servidor de Chat iniciado en el puerto " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nuevo cliente conectado: " + clientSocket);
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Error al iniciar el servidor: " + e.getMessage());
        }
    }

    // Metodo para enviar un mensaje a todos los clientes conectados

    public static void broadcastMessage(String senderName, String senderColorHex, String message) {
        // El formato del mensaje será: "COLORHEX|NOMBRE: MENSAJE"
        String formattedMessage = senderColorHex + "|" + senderName + ": " + message;
        for (PrintWriter writer : clientMap.keySet()) { // Iteramos sobre las claves (PrintWriters)
            writer.println(formattedMessage);
        }
    }

    // Metodo para generar un color hexadecimal aleatorio
    private static String getRandomHexColor() {
        Random rand = new Random();

        int r = rand.nextInt(151) + 50; // Rango de 50 a 200
        int g = rand.nextInt(151) + 50; // Rango de 50 a 200
        int b = rand.nextInt(151) + 50; // Rango de 50 a 200

        // Convertir los valores RGB a formato hexadecimal
        return String.format("#%02x%02x%02x", r, g, b);
    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter writer;
        private BufferedReader reader;
        private String clientName;
        private String clientColorHex; // Color de este cliente

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                writer = new PrintWriter(clientSocket.getOutputStream(), true);

                // Asignar un color aleatorio a este cliente
                clientColorHex = getRandomHexColor();

                writer.println("Bienvenido al Chat. Por favor, ingresa tu nombre:");
                clientName = reader.readLine();
                if (clientName == null || clientName.trim().isEmpty()) {
                    clientName = "Anónimo-" + UUID.randomUUID().toString().substring(0, 4);
                }

                writer.println("¡Bienvenido, " + clientName + "!");
                System.out.println("Cliente '" + clientName + "' conectado con color " + clientColorHex + ".");

                // Añadir el writer del cliente y su info al mapa global
                clientMap.put(writer, new ClientInfo(clientName, clientColorHex));

                // Anunciar la unión al chat con su color
                broadcastMessage(clientName, clientColorHex, "se ha unido al chat.");

                String message;
                while ((message = reader.readLine()) != null) {
                    System.out.println("Mensaje de " + clientName + ": " + message);
                    if (message.equalsIgnoreCase("/quit")) {
                        break;
                    }
                    broadcastMessage(clientName, clientColorHex, message); // Envía el mensaje con el color
                }
            } catch (IOException e) {
                System.err.println("Error en la comunicación con el cliente " + clientName + ": " + e.getMessage());
            } finally {
                if (clientName != null) {
                    System.out.println("Cliente '" + clientName + "' desconectado.");
                    // Eliminar el cliente del mapa antes de hacer el broadcast final
                    clientMap.remove(writer);
                    broadcastMessage(clientName, clientColorHex, "ha abandonado el chat."); // Usar su color para el mensaje de salida
                }
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error al cerrar el socket del cliente: " + e.getMessage());
                }
            }
        }
    }
}