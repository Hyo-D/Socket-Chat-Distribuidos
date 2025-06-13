package org.example.Cliente;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class ChatClientGUI extends JFrame {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;

    private JTextPane messageArea;
    private JTextField messageField;
    private JButton sendButton;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    private StyledDocument doc;
    private SimpleAttributeSet defaultStyle;
    private SimpleAttributeSet nameStyle; // Este estilo ahora también incluirá negrita

    public ChatClientGUI() {
        super("Cliente de Chat");
        createUI();
        connectToServer();
    }

    private void createUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 400);
        setLocationRelativeTo(null);

        messageArea = new JTextPane();
        messageArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(messageArea);
        add(scrollPane, BorderLayout.CENTER);

        doc = messageArea.getStyledDocument();

        // --- Configuración del estilo por defecto (para el mensaje general) ---
        defaultStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(defaultStyle, Color.BLACK); // Color por defecto para el texto general

        // --- Configuración del estilo para el nombre (ahora con negrita) ---
        nameStyle = new SimpleAttributeSet();
        StyleConstants.setBold(nameStyle, true); // <--- ¡NUEVA LÍNEA CLAVE PARA LA NEGRITA!
        // El color se establecerá dinámicamente al procesar el mensaje,
        // pero la propiedad de negrita ya está definida aquí.

        JPanel southPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        sendButton = new JButton("Enviar");

        ActionListener sendAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        };
        sendButton.addActionListener(sendAction);
        messageField.addActionListener(sendAction);

        southPanel.add(messageField, BorderLayout.CENTER);
        southPanel.add(sendButton, BorderLayout.EAST);
        add(southPanel, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void appendStyledMessage(String message) {
        String[] parts = message.split("\\|", 2);
        String colorHex = null;
        String fullMessageContent;

        if (parts.length == 2) {
            colorHex = parts[0];
            fullMessageContent = parts[1];
        } else {
            fullMessageContent = message;
        }

        try {
            if (colorHex != null && fullMessageContent.contains(":")) {
                int colonIndex = fullMessageContent.indexOf(":");
                String namePart = fullMessageContent.substring(0, colonIndex + 1); // Incluye los dos puntos
                String messagePart = fullMessageContent.substring(colonIndex + 1); // Parte del mensaje después de los dos puntos

                // Antes de insertar el nombre, establecer el color para esta instancia del nombre
                try {
                    Color userColor = Color.decode(colorHex);
                    StyleConstants.setForeground(nameStyle, userColor);
                } catch (NumberFormatException ex) {
                    StyleConstants.setForeground(nameStyle, Color.BLACK); // Fallback color
                }

                doc.insertString(doc.getLength(), namePart, nameStyle); // Inserta el nombre con color y negrita
                doc.insertString(doc.getLength(), messagePart + "\n", defaultStyle); // Inserta el resto del mensaje
            } else {
                // Mensajes sin formato de color/nombre (ej. mensajes del sistema o de bienvenida)
                doc.insertString(doc.getLength(), fullMessageContent + "\n", defaultStyle);
            }

            messageArea.setCaretPosition(doc.getLength());

        } catch (BadLocationException e) {
            System.err.println("Error al insertar texto en JTextPane: " + e.getMessage());
        }
    }


    private void connectToServer() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = reader.readLine()) != null) {
                        String finalServerMessage = serverMessage;
                        SwingUtilities.invokeLater(() -> {
                            appendStyledMessage(finalServerMessage);
                        });
                    }
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> appendStyledMessage("Error al recibir mensajes del servidor: " + e.getMessage()));
                } finally {
                    SwingUtilities.invokeLater(() -> {
                        appendStyledMessage("Desconectado del servidor.");
                        sendButton.setEnabled(false);
                        messageField.setEditable(false);
                    });
                    closeConnection();
                }
            }).start();

        } catch (UnknownHostException e) {
            appendStyledMessage("Servidor no encontrado: " + e.getMessage());
            sendButton.setEnabled(false);
            messageField.setEditable(false);
        } catch (IOException e) {
            appendStyledMessage("Error de I/O al conectar con el servidor: " + e.getMessage());
            sendButton.setEnabled(false);
            messageField.setEditable(false);
        }
    }

    private void sendMessage() {
        String message = messageField.getText();
        if (message != null && !message.trim().isEmpty()) {
            writer.println(message);
            messageField.setText("");

            if (message.equalsIgnoreCase("/quit")) {
                closeConnection();
            }
        }
    }

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (writer != null) {
                writer.close();
            }
            if (reader != null) {
                reader.close();
            }
            sendButton.setEnabled(false);
            messageField.setEditable(false);
        } catch (IOException e) {
            System.err.println("Error al cerrar la conexión del cliente: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatClientGUI());
    }
}