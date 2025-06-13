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
    private JButton reconnectButton;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    private StyledDocument doc;
    private SimpleAttributeSet defaultStyle;
    private SimpleAttributeSet nameStyle;

    private boolean isConnected = false;

    public ChatClientGUI() {
        super("Cliente de Chat");
        createUI();

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
        defaultStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(defaultStyle, Color.BLACK);

        nameStyle = new SimpleAttributeSet();
        StyleConstants.setBold(nameStyle, true);

        // Panel inferior para entrada de mensaje y botones
        JPanel southPanel = new JPanel(new BorderLayout());

        JPanel inputPanel = new JPanel(new BorderLayout());
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

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        // Panel para el botón de reconexión, alineado a la izquierda o derecha
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        reconnectButton = new JButton("Reconectar");
        reconnectButton.addActionListener(e -> attemptReconnect());
        buttonPanel.add(reconnectButton);


        southPanel.add(inputPanel, BorderLayout.CENTER); // Campo de texto y enviar en el centro del panel sur
        southPanel.add(buttonPanel, BorderLayout.EAST); // Botón de reconectar a la derecha

        add(southPanel, BorderLayout.SOUTH);

        setVisible(true);

        // Intentar conectar al inicio
        attemptConnect();
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
                String namePart = fullMessageContent.substring(0, colonIndex + 1);
                String messagePart = fullMessageContent.substring(colonIndex + 1);

                try {
                    Color userColor = Color.decode(colorHex);
                    StyleConstants.setForeground(nameStyle, userColor);
                } catch (NumberFormatException ex) {
                    StyleConstants.setForeground(nameStyle, Color.BLACK);
                }

                doc.insertString(doc.getLength(), namePart, nameStyle);
                doc.insertString(doc.getLength(), messagePart + "\n", defaultStyle);
            } else {
                doc.insertString(doc.getLength(), fullMessageContent + "\n", defaultStyle);
            }
            messageArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            System.err.println("Error al insertar texto en JTextPane: " + e.getMessage());
        }
    }

    // Nuevo metodo para reintentar conexion
    private void attemptConnect() {
        if (isConnected) {
            appendStyledMessage("Ya estás conectado.");
            return;
        }

        appendStyledMessage("Intentando conectar al servidor...");
        // Deshabilitar el botón de reconexión mientras se intenta conectar
        reconnectButton.setEnabled(false);

        // Intentar la conexión en un hilo separado para no bloquear la GUI
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                writer = new PrintWriter(socket.getOutputStream(), true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                SwingUtilities.invokeLater(() -> {
                    isConnected = true; // Establecer el estado de conexión
                    sendButton.setEnabled(true);
                    messageField.setEditable(true);
                    reconnectButton.setEnabled(false); // Deshabilitar si ya estamos conectados
                    appendStyledMessage("Conectado al servidor de Chat.");
                });

                // Hilo para recibir mensajes del servidor
                String serverMessage;
                while ((serverMessage = reader.readLine()) != null) {
                    String finalServerMessage = serverMessage;
                    SwingUtilities.invokeLater(() -> appendStyledMessage(finalServerMessage));
                }

            } catch (UnknownHostException e) {
                SwingUtilities.invokeLater(() -> {
                    appendStyledMessage("Error: Servidor no encontrado. " + e.getMessage());
                    handleConnectionLoss(); // Manejar la pérdida/fallo de conexión
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    appendStyledMessage("Error de I/O al conectar/recibir: " + e.getMessage());
                    handleConnectionLoss(); // Manejar la pérdida/fallo de conexión
                });
            } finally {
                // Si el bucle while termina (conexión cerrada por el servidor o error)
                SwingUtilities.invokeLater(() -> {
                    if (isConnected) { // Si estábamos conectados y se cerró la conexión
                        appendStyledMessage("Desconectado del servidor.");
                    } else { // Si la conexión falló inicialmente
                        appendStyledMessage("Fallo la conexión al servidor.");
                    }
                    handleConnectionLoss(); // Manejar la pérdida/fallo de conexión
                });
                closeConnectionResources(); // Cerrar los recursos del socket
            }
        }).start();
    }

    // Metodo para manejar la lógica cuando la conexión se pierde o falla
    private void handleConnectionLoss() {
        isConnected = false;
        sendButton.setEnabled(false);
        messageField.setEditable(false);
        reconnectButton.setEnabled(true); // Habilitar el botón de reconexión
    }

    // Nuevo metodo para cerrar solo los recursos del socket sin afectar la GUI
    private void closeConnectionResources() {
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
        } catch (IOException e) {
            System.err.println("Error al cerrar los recursos del socket: " + e.getMessage());
        } finally {
            socket = null; // Asegurarse de que el socket sea nulo para una nueva conexión
            writer = null;
            reader = null;
        }
    }


    private void sendMessage() {
        String message = messageField.getText();
        if (isConnected && message != null && !message.trim().isEmpty()) { // Solo enviar si está conectado
            writer.println(message);
            messageField.setText("");

            if (message.equalsIgnoreCase("/quit")) {
                SwingUtilities.invokeLater(() -> appendStyledMessage("Saliendo del chat..."));
                closeConnectionResources(); // Cerrar recursos
                handleConnectionLoss(); // Actualizar estado de GUI
            }
        } else if (!isConnected) {
            SwingUtilities.invokeLater(() -> appendStyledMessage("No estás conectado al servidor. Intenta reconectar."));
            messageField.setText("");
        }
    }

    // Metodo que se llama cuando se hace clic en el botón de reconexión
    private void attemptReconnect() {
        if (!isConnected) {
            attemptConnect();
        } else {
            appendStyledMessage("Ya estás conectado.");
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatClientGUI());
    }
}