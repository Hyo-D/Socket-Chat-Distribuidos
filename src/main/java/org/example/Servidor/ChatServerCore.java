package org.example.Servidor;
import java.io.*;
import java.net.*;
import java.awt.Color;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatServerCore implements Runnable {

    private static final int PORT = 12345;
    private static ConcurrentHashMap<PrintWriter, ClientInfo> clientMap = new ConcurrentHashMap<>();
    private ServerSocket serverSocket;
    private volatile boolean running = false; // Bandera para controlar el bucle del servidor
    private Consumer<String> messageLogger; // Para enviar logs a la GUI

    // Clase interna para almacenar información del cliente
    private static class ClientInfo {
        String name;
        String colorHex;

        public ClientInfo(String name, String colorHex) {
            this.name = name;
            this.colorHex = colorHex;
        }
    }

    public ChatServerCore(Consumer<String> messageLogger) {
        this.messageLogger = messageLogger;
    }

    @Override
    public void run() { // El metodo run para el hilo del servidor
        running = true;
        try {
            serverSocket = new ServerSocket(PORT);
            logMessage("Servidor de Chat iniciado en el puerto " + PORT);

            while (running) { // Ahora controlado por la bandera 'running'
                Socket clientSocket = serverSocket.accept();
                logMessage("Nuevo cliente conectado: " + clientSocket);
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
            }
        } catch (SocketException se) {
            if (running) { // Solo mostrar error si no fue una detención intencional
                logMessage("Error de socket (probablemente al detener el servidor): " + se.getMessage());
            } else {
                logMessage("Servidor detenido."); // Mensaje para parada limpia
            }
        } catch (IOException e) {
            logMessage("Error al iniciar o ejecutar el servidor: " + e.getMessage());
        } finally {
            stopServer(); // Asegurarse de que el servidor esté detenido si el bucle termina
        }
    }

    // Metodo para detener el servidor
    public void stopServer() {
        if (!running) return; // Ya está detenido
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // Esto lanzará una SocketException en el accept()
                logMessage("Intentando detener el servidor...");
            }
        } catch (IOException e) {
            logMessage("Error al cerrar el socket del servidor: " + e.getMessage());
        } finally {
            // Desconectar a todos los clientes activos
            for (PrintWriter writer : clientMap.keySet()) {
                try {
                    writer.close(); // Esto notificará a los clientes y cerrará sus streams
                } catch (Exception e) {
                    // Ignorar errores al cerrar writers
                }
            }
            clientMap.clear(); // Limpiar el mapa de clientes
            logMessage("Todos los clientes desconectados.");
            logMessage("Servidor detenido exitosamente.");
        }
    }

    public boolean isRunning() {
        return running;
    }


    // Metodo para enviar un mensaje a todos los clientes conectados
    public static void broadcastMessage(String senderName, String senderColorHex, String message) {
        String formattedMessage = senderColorHex + "|" + senderName + ": " + message;
        for (PrintWriter writer : clientMap.keySet()) {
            writer.println(formattedMessage);
        }
    }

    // Metodo para generar un color hexadecimal aleatorio
    private static String getRandomHexColor() {
        Random rand = new Random();
        int r = rand.nextInt(151) + 50;
        int g = rand.nextInt(151) + 50;
        int b = rand.nextInt(151) + 50;
        return String.format("#%02x%02x%02x", r, g, b);
    }

    // Metodo para loguear mensajes a la GUI
    private void logMessage(String message) {
        if (messageLogger != null) {
            messageLogger.accept(message);
        } else {
            System.out.println(message); // Fallback a la consola si no hay logger GUI
        }
    }

    private class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter writer;
        private BufferedReader reader;
        private String clientName;
        private String clientColorHex;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                writer = new PrintWriter(clientSocket.getOutputStream(), true);

                clientColorHex = getRandomHexColor();

                writer.println("Bienvenido al Chat. Por favor, ingresa tu nombre:");
                clientName = reader.readLine();
                if (clientName == null || clientName.trim().isEmpty()) {
                    clientName = "Anónimo-" + UUID.randomUUID().toString().substring(0, 4);
                }

                writer.println("¡Bienvenido, " + clientName + "!");
                logMessage("Cliente '" + clientName + "' conectado con color " + clientColorHex + ".");

                clientMap.put(writer, new ClientInfo(clientName, clientColorHex));

                broadcastMessage(clientName, clientColorHex, "se ha unido al chat.");

                String message;
                while ((message = reader.readLine()) != null) {
                    logMessage("Mensaje de " + clientName + ": " + message);
                    if (message.equalsIgnoreCase("/quit")) {
                        break;
                    }
                    broadcastMessage(clientName, clientColorHex, message);
                }
            } catch (IOException e) {
                logMessage("Error en la comunicación con el cliente " + (clientName != null ? clientName : "Desconocido") + ": " + e.getMessage());
            } finally {
                if (clientName != null) {
                    logMessage("Cliente '" + clientName + "' desconectado.");
                    ClientInfo disconnectedClientInfo = clientMap.remove(writer); // Obtener info antes de eliminar
                    if (disconnectedClientInfo != null) {
                        broadcastMessage(disconnectedClientInfo.name, disconnectedClientInfo.colorHex, "ha abandonado el chat.");
                    } else {
                        // Caso para un cliente que se desconecta antes de establecer su nombre
                        broadcastMessage("Un cliente desconocido", "#808080", "ha abandonado el chat.");
                    }
                }
                try {
                    if (clientSocket != null && !clientSocket.isClosed()) {
                        clientSocket.close();
                    }
                } catch (IOException e) {
                    logMessage("Error al cerrar el socket del cliente: " + e.getMessage());
                }
            }
        }
    }
}