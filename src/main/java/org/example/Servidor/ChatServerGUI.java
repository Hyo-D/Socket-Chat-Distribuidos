package org.example.Servidor;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.function.Consumer;

public class ChatServerGUI extends JFrame {

    private JTextPane logArea;
    private JButton startStopButton;
    private JLabel statusLabel;
    private JLabel portLabel;

    private ChatServerCore serverCore;
    private Thread serverThread;

    public ChatServerGUI() {
        super("Control del Servidor de Chat");
        createUI();
        initServerLogic();
    }

    private void createUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 500);
        setLocationRelativeTo(null); // Centrar la ventana

        // Panel superior para controles y estado
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10)); // Más espacio
        startStopButton = new JButton("Iniciar Servidor");
        startStopButton.setFont(new Font("Arial", Font.BOLD, 14));
        startStopButton.addActionListener(e -> toggleServer());
        topPanel.add(startStopButton);

        statusLabel = new JLabel("Estado: Detenido");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        statusLabel.setForeground(Color.RED);
        topPanel.add(statusLabel);

        portLabel = new JLabel("Puerto: 12345");
        portLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        topPanel.add(portLabel);

        add(topPanel, BorderLayout.NORTH);

        // Área de log central
        logArea = new JTextPane();
        logArea.setEditable(false);
        logArea.setBackground(new Color(240, 240, 240)); // Un gris claro
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12)); // Fuente monoespaciada para logs
        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);

        // Listener para detener el servidor al cerrar la ventana
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (serverCore != null && serverCore.isRunning()) {
                    serverCore.stopServer();
                    try {
                        serverThread.join(2000); // Esperar un poco a que el hilo termine
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });

        setVisible(true);
    }

    private void initServerLogic() {
        // Creamos un Consumer que recibirá los mensajes y los añadirá al logArea
        Consumer<String> guiLogger = message -> {
            SwingUtilities.invokeLater(() -> {
                try {
                    StyledDocument doc = logArea.getStyledDocument();
                    SimpleAttributeSet style = new SimpleAttributeSet();
                    StyleConstants.setForeground(style, Color.BLACK); // Color del texto
                    doc.insertString(doc.getLength(), message + "\n", style);
                    logArea.setCaretPosition(doc.getLength()); // Scroll automático
                } catch (BadLocationException e) {
                    System.err.println("Error al insertar log en GUI: " + e.getMessage());
                }
            });
        };
        serverCore = new ChatServerCore(guiLogger); // Inyectamos el logger en el núcleo del servidor
    }

    private void toggleServer() {
        if (serverCore.isRunning()) {
            // Si el servidor está corriendo, detenerlo
            serverCore.stopServer();
            // Esperar al hilo para asegurar que el estado se actualice
            if (serverThread != null && serverThread.isAlive()) {
                try {
                    serverThread.join(1000); // Espera un máximo de 1 segundo
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logToGui("Interrupción al detener el hilo del servidor.");
                }
            }
            updateServerStatus(false);
        } else {
            // Si el servidor está detenido, iniciarlo
            serverThread = new Thread(serverCore);
            serverThread.start();
            updateServerStatus(true);
        }
    }

    private void updateServerStatus(boolean isRunning) {
        if (isRunning) {
            statusLabel.setText("Estado: Ejecutando");
            statusLabel.setForeground(new Color(0, 150, 0)); // Verde oscuro
            startStopButton.setText("Detener Servidor");
        } else {
            statusLabel.setText("Estado: Detenido");
            statusLabel.setForeground(Color.RED);
            startStopButton.setText("Iniciar Servidor");
        }
    }

    // Metodo para loguear mensajes a la GUI (para la propia GUI)
    private void logToGui(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = logArea.getStyledDocument();
                SimpleAttributeSet style = new SimpleAttributeSet();
                StyleConstants.setForeground(style, Color.BLUE); // Mensajes de la GUI en azul
                doc.insertString(doc.getLength(), message + "\n", style);
                logArea.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                System.err.println("Error al insertar log en GUI: " + e.getMessage());
            }
        });
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatServerGUI());
    }
}
