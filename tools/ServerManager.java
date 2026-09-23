package tools;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;

public class ServerManager extends JFrame {

    private static final int SERVER_PORT = 8080;
    private static final int CONTROL_PORT = 8088;
    private static final String FONT_NAME = "맑은 고딕";

    private final boolean isHeadless;
    private Process serverProcess = null;
    private HttpServer controlServer = null;

    private JLabel lblStatus;
    private JLabel lblIp;
    private ModernButton btnToggle;
    private ModernButton btnWeb;
    private ModernButton btnFolder;
    private JTextArea txtLog;
    private TrayIcon trayIcon;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");

        boolean headlessMode = GraphicsEnvironment.isHeadless();
        for (String arg : args) {
            if ("--headless".equalsIgnoreCase(arg) || "-h".equalsIgnoreCase(arg) || "--daemon".equalsIgnoreCase(arg)) {
                headlessMode = true;
                break;
            }
        }

        if (headlessMode) {
            System.out.println("======================================================");
            System.out.println("  My Music Server Manager (Headless / Linux Mode)");
            System.out.println("======================================================");
            new ServerManager(true);
        } else {
            SwingUtilities.invokeLater(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                }
                new ServerManager(false).setVisible(true);
            });
        }
    }

    public ServerManager(boolean headless) {
        this.isHeadless = headless;

        if (!isHeadless) {
            setTitle("My Music Server Manager");
            setSize(800, 580);
            setMinimumSize(new Dimension(680, 480));
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

            initUI();
            initSystemTray();

            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    onCloseRequest();
                }
            });
        }

        // 공통: 원격 제어 HTTP 서버 시작 (포트 8088)
        startControlServer();

        // 프로세스 종료 시 훅 등록 (Ctrl+C 또는 kill 시 하위 서버 프로세스도 함께 정리)
        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanupOnExit));

        log("My Music Server Manager 가 시작되었습니다. (모드: " + (isHeadless ? "Headless/리눅스" : "GUI/윈도우") + ")");
        log("원격 제어 포트: " + CONTROL_PORT + " 활성화됨 (스마트폰 앱 연동 대기)");
        log("로컬 서버 IP: " + getLocalIpAddress() + " (음악 서버 포트: " + SERVER_PORT + ")");
        log("스마트폰 앱에서 언제든 원격으로 음악 서버를 켜거나 끌 수 있습니다.");
    }

    private void initUI() {
        Color bgDark = new Color(24, 24, 30);
        Color cardBg = new Color(34, 34, 42);
        Color textMain = new Color(240, 240, 240);
        Color textSub = new Color(175, 175, 185);

        JPanel mainPanel = new JPanel(new BorderLayout(14, 14));
        mainPanel.setBackground(bgDark);
        mainPanel.setBorder(new EmptyBorder(18, 18, 18, 18));

        // 1. 상단 상태 및 컨트롤 카드
        JPanel headerCard = new JPanel(new BorderLayout(16, 16));
        headerCard.setBackground(cardBg);
        headerCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(50, 50, 62), 1),
                new EmptyBorder(16, 20, 16, 20)
        ));

        JPanel statusInfoPanel = new JPanel(new GridLayout(2, 1, 6, 6));
        statusInfoPanel.setOpaque(false);

        lblStatus = new JLabel("● 서버 중지됨");
        lblStatus.setFont(new Font(FONT_NAME, Font.BOLD, 18));
        lblStatus.setForeground(new Color(239, 83, 80)); // Red

        lblIp = new JLabel("접속 주소: http://" + getLocalIpAddress() + ":" + SERVER_PORT);
        lblIp.setFont(new Font(FONT_NAME, Font.PLAIN, 13));
        lblIp.setForeground(textSub);

        statusInfoPanel.add(lblStatus);
        statusInfoPanel.add(lblIp);

        // 상단 제어 버튼 모음
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controlPanel.setOpaque(false);

        btnToggle = new ModernButton(
                "▶  서버 시작",
                new Color(29, 185, 84), // Normal: Spotify Green
                new Color(36, 206, 95),  // Hover: Light Green
                Color.WHITE
        );
        btnToggle.setPreferredSize(new Dimension(145, 42));
        btnToggle.addActionListener(e -> toggleServer());

        btnWeb = new ModernButton(
                "🌐 웹 플레이어",
                new Color(52, 52, 66),
                new Color(68, 68, 86),
                Color.WHITE
        );
        btnWeb.setPreferredSize(new Dimension(130, 42));
        btnWeb.addActionListener(e -> openWebPlayer());

        btnFolder = new ModernButton(
                "📁 음악 폴더",
                new Color(52, 52, 66),
                new Color(68, 68, 86),
                Color.WHITE
        );
        btnFolder.setPreferredSize(new Dimension(120, 42));
        btnFolder.addActionListener(e -> openMusicFolder());

        controlPanel.add(btnToggle);
        controlPanel.add(btnWeb);
        controlPanel.add(btnFolder);

        headerCard.add(statusInfoPanel, BorderLayout.WEST);
        headerCard.add(controlPanel, BorderLayout.EAST);

        // 2. 중앙 콘솔 로그 창
        JPanel logCard = new JPanel(new BorderLayout(8, 8));
        logCard.setBackground(cardBg);
        logCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(50, 50, 62), 1),
                new EmptyBorder(14, 14, 14, 14)
        ));

        JPanel logTitleBar = new JPanel(new BorderLayout());
        logTitleBar.setOpaque(false);
        logTitleBar.setBorder(new EmptyBorder(0, 0, 8, 0));

        JLabel lblLogTitle = new JLabel("실시간 서버 로그 콘솔");
        lblLogTitle.setFont(new Font(FONT_NAME, Font.BOLD, 14));
        lblLogTitle.setForeground(textMain);

        ModernButton btnClearLog = new ModernButton(
                "지우기",
                new Color(45, 45, 56),
                new Color(60, 60, 75),
                textSub
        );
        btnClearLog.setPreferredSize(new Dimension(72, 28));
        btnClearLog.setFont(new Font(FONT_NAME, Font.PLAIN, 12));
        btnClearLog.addActionListener(e -> txtLog.setText(""));

        logTitleBar.add(lblLogTitle, BorderLayout.WEST);
        logTitleBar.add(btnClearLog, BorderLayout.EAST);

        txtLog = new JTextArea();
        txtLog.setBackground(new Color(16, 16, 22));
        txtLog.setForeground(new Color(220, 220, 230));
        txtLog.setCaretColor(Color.WHITE);
        txtLog.setFont(new Font(FONT_NAME, Font.PLAIN, 13));
        txtLog.setEditable(false);
        txtLog.setLineWrap(true);
        txtLog.setWrapStyleWord(true);
        txtLog.setBorder(new EmptyBorder(8, 8, 8, 8));

        JScrollPane scrollPane = new JScrollPane(txtLog);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(45, 45, 55)));

        logCard.add(logTitleBar, BorderLayout.NORTH);
        logCard.add(scrollPane, BorderLayout.CENTER);

        mainPanel.add(headerCard, BorderLayout.NORTH);
        mainPanel.add(logCard, BorderLayout.CENTER);

        setContentPane(mainPanel);
    }

    private void initSystemTray() {
        if (isHeadless || !SystemTray.isSupported()) return;

        try {
            SystemTray tray = SystemTray.getSystemTray();
            Image image = createTrayIconImage();

            PopupMenu popup = new PopupMenu();
            MenuItem itemOpen = new MenuItem("매니저 열기");
            itemOpen.addActionListener(e -> {
                setVisible(true);
                setExtendedState(JFrame.NORMAL);
                toFront();
            });

            MenuItem itemStart = new MenuItem("서버 시작");
            itemStart.addActionListener(e -> startServer());

            MenuItem itemStop = new MenuItem("서버 중지");
            itemStop.addActionListener(e -> stopServer());

            MenuItem itemExit = new MenuItem("완전 종료");
            itemExit.addActionListener(e -> exitApplication());

            popup.add(itemOpen);
            popup.addSeparator();
            popup.add(itemStart);
            popup.add(itemStop);
            popup.addSeparator();
            popup.add(itemExit);

            trayIcon = new TrayIcon(image, "My Music Server", popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> {
                setVisible(true);
                setExtendedState(JFrame.NORMAL);
                toFront();
            });

            tray.add(trayIcon);
        } catch (Exception e) {
            log("시스템 트레이 초기화 실패: " + e.getMessage());
        }
    }

    private Image createTrayIconImage() {
        int size = 16;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(29, 185, 84));
        g2.fillOval(0, 0, size, size);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
        g2.drawString("M", 3, 12);
        g2.dispose();
        return img;
    }

    private synchronized void toggleServer() {
        if (serverProcess != null && serverProcess.isAlive()) {
            stopServer();
        } else {
            startServer();
        }
    }

    public synchronized void startServer() {
        if (serverProcess != null && serverProcess.isAlive()) {
            log("서버가 이미 실행 중입니다.");
            return;
        }

        if (btnToggle != null) btnToggle.setEnabled(false);
        log("Spring Boot 음악 서버 기동을 시작합니다...");

        new Thread(() -> {
            try {
                File jarFile = new File("target/music-server-0.0.1-SNAPSHOT.jar");
                ProcessBuilder pb;

                if (jarFile.exists()) {
                    log("JAR 파일 실행: " + jarFile.getAbsolutePath());
                    pb = new ProcessBuilder("java", "-jar", jarFile.getAbsolutePath());
                } else {
                    log("Maven spring-boot:run 실행 (JAR 파일 미발견)...");
                    boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
                    if (isWindows) {
                        pb = new ProcessBuilder("cmd", "/c", "mvn", "spring-boot:run");
                    } else {
                        pb = new ProcessBuilder("mvn", "spring-boot:run");
                    }
                }

                pb.directory(new File("."));
                pb.redirectErrorStream(true);
                serverProcess = pb.start();

                if (!isHeadless) {
                    SwingUtilities.invokeLater(() -> {
                        lblStatus.setText("● 서버 실행 중");
                        lblStatus.setForeground(new Color(29, 185, 84));
                        btnToggle.setText("⏹  서버 중지");
                        btnToggle.setButtonColors(new Color(229, 57, 53), new Color(244, 67, 54)); // Red
                        btnToggle.setEnabled(true);
                    });
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverProcess.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    final String logLine = line;
                    if (!isHeadless) {
                        SwingUtilities.invokeLater(() -> appendLog(logLine));
                    } else {
                        System.out.println(logLine);
                    }
                }

                int exitCode = serverProcess.waitFor();
                log("서버 프로세스가 종료되었습니다 (종료 코드: " + exitCode + ")");
            } catch (Exception ex) {
                log("서버 실행 오류: " + ex.getMessage());
            } finally {
                serverProcess = null;
                if (!isHeadless) {
                    SwingUtilities.invokeLater(() -> {
                        lblStatus.setText("● 서버 중지됨");
                        lblStatus.setForeground(new Color(239, 83, 80));
                        btnToggle.setText("▶  서버 시작");
                        btnToggle.setButtonColors(new Color(29, 185, 84), new Color(36, 206, 95)); // Green
                        btnToggle.setEnabled(true);
                    });
                }
            }
        }).start();
    }

    public synchronized void stopServer() {
        if (serverProcess == null || !serverProcess.isAlive()) {
            log("중지할 서버 프로세스가 없습니다.");
            return;
        }

        log("서버 프로세스 종료를 요청합니다...");
        if (btnToggle != null) btnToggle.setEnabled(false);

        new Thread(() -> {
            try {
                serverProcess.destroy();
                if (!serverProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    serverProcess.destroyForcibly();
                }
                log("서버가 성공적으로 중지되었습니다.");
            } catch (Exception e) {
                log("서버 종료 처리 중 오류: " + e.getMessage());
            } finally {
                serverProcess = null;
                if (!isHeadless) {
                    SwingUtilities.invokeLater(() -> {
                        lblStatus.setText("● 서버 중지됨");
                        lblStatus.setForeground(new Color(239, 83, 80));
                        btnToggle.setText("▶  서버 시작");
                        btnToggle.setButtonColors(new Color(29, 185, 84), new Color(36, 206, 95));
                        btnToggle.setEnabled(true);
                    });
                }
            }
        }).start();
    }

    // 모바일 앱 연동용 경량 원격 제어 HTTP 서버 (포트 8088)
    private void startControlServer() {
        new Thread(() -> {
            try {
                controlServer = HttpServer.create(new InetSocketAddress(CONTROL_PORT), 0);

                controlServer.createContext("/api/control/status", exchange -> {
                    boolean running = (serverProcess != null && serverProcess.isAlive());
                    String json = String.format("{\"running\":%b,\"serverPort\":%d,\"controlPort\":%d,\"ip\":\"%s\"}",
                            running, SERVER_PORT, CONTROL_PORT, getLocalIpAddress());
                    sendJsonResponse(exchange, 200, json);
                });

                controlServer.createContext("/api/control/start", exchange -> {
                    log("[원격 제어] 스마트폰에서 서버 시작 요청 수신");
                    startServer();
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"서버 시작 처리됨\"}");
                });

                controlServer.createContext("/api/control/stop", exchange -> {
                    log("[원격 제어] 스마트폰에서 서버 중지 요청 수신");
                    stopServer();
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"서버 중지 처리됨\"}");
                });

                controlServer.setExecutor(null);
                controlServer.start();
                log("스마트폰 원격 제어 리스너 활성화: http://" + getLocalIpAddress() + ":" + CONTROL_PORT);
            } catch (Exception e) {
                log("원격 제어 서버 시작 실패: " + e.getMessage());
            }
        }).start();
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
        byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void openWebPlayer() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI("http://localhost:" + SERVER_PORT));
            } else {
                log("웹 브라우저 자동 실행을 지원하지 않는 환경입니다. 브라우저에서 http://localhost:" + SERVER_PORT + " 에 직접 접속하세요.");
            }
        } catch (Exception ex) {
            log("웹 브라우저를 열 수 없습니다: " + ex.getMessage());
        }
    }

    private void openMusicFolder() {
        try {
            File dir = new File("music");
            if (!dir.exists()) dir.mkdirs();
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir);
            } else {
                log("음악 폴더 경로: " + dir.getAbsolutePath());
            }
        } catch (Exception ex) {
            log("음악 폴더를 열 수 없습니다: " + ex.getMessage());
        }
    }

    private void onCloseRequest() {
        if (trayIcon != null) {
            int choice = JOptionPane.showOptionDialog(
                    this,
                    "창을 닫으면 작업표시줄(트레이)에서 계속 실행됩니다.\n완전 종료하시겠습니까?",
                    "My Music Server Manager",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    new String[]{"트레이로 최소화", "완전 종료", "취소"},
                    "트레이로 최소화"
            );

            if (choice == 0) {
                setVisible(false);
                trayIcon.displayMessage("My Music Server", "백그라운드에서 실행 중입니다. 시계 옆 아이콘을 클릭하여 다시 열 수 있습니다.", TrayIcon.MessageType.INFO);
            } else if (choice == 1) {
                exitApplication();
            }
        } else {
            exitApplication();
        }
    }

    private void cleanupOnExit() {
        stopServer();
        if (controlServer != null) {
            try {
                controlServer.stop(0);
            } catch (Exception ignored) {}
        }
    }

    private void exitApplication() {
        cleanupOnExit();
        System.exit(0);
    }

    private void log(String message) {
        String timestamp = timeFormat.format(new Date());
        String formatted = "[" + timestamp + "] " + message;
        if (!isHeadless) {
            appendLog(formatted);
        } else {
            System.out.println(formatted);
        }
    }

    private void appendLog(String message) {
        if (txtLog != null) {
            txtLog.append(message + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        }
    }

    private static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    // 커스텀 다크 모던 버튼 컴포넌트
    static class ModernButton extends JButton {
        private Color normalColor;
        private Color hoverColor;
        private Color pressedColor;
        private int cornerRadius = 8;

        public ModernButton(String text, Color normalColor, Color hoverColor, Color textColor) {
            super(text);
            this.normalColor = normalColor;
            this.hoverColor = hoverColor;
            this.pressedColor = normalColor.darker();
            setForeground(textColor);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setFont(new Font(FONT_NAME, Font.BOLD, 13));
        }

        public void setButtonColors(Color normalColor, Color hoverColor) {
            this.normalColor = normalColor;
            this.hoverColor = hoverColor;
            this.pressedColor = normalColor.darker();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            if (!isEnabled()) {
                g2.setColor(new Color(55, 55, 65));
            } else if (getModel().isPressed()) {
                g2.setColor(pressedColor);
            } else if (getModel().isRollover()) {
                g2.setColor(hoverColor);
            } else {
                g2.setColor(normalColor);
            }

            g2.fillRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius);

            FontMetrics fm = g2.getFontMetrics();
            Rectangle r = fm.getStringBounds(getText(), g2).getBounds();
            int textX = (getWidth() - r.width) / 2;
            int textY = (getHeight() - r.height) / 2 + fm.getAscent();

            g2.setColor(isEnabled() ? getForeground() : new Color(130, 130, 140));
            g2.drawString(getText(), textX, textY);
            g2.dispose();
        }
    }
}
