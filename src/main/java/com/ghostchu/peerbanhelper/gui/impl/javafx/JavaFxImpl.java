package com.ghostchu.peerbanhelper.gui.impl.javafx;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.MainJavaFx;
import com.ghostchu.peerbanhelper.event.PBHServerStartedEvent;
import com.ghostchu.peerbanhelper.gui.impl.GuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.javafx.mainwindow.JFXWindowController;
import com.ghostchu.peerbanhelper.log4j2.SwingLoggerAppender;
import com.ghostchu.peerbanhelper.text.Lang;
import com.google.common.eventbus.Subscribe;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@Slf4j
public class JavaFxImpl extends ConsoleGuiImpl implements GuiImpl {
    @Getter
    private final boolean silentStart;
    private final String[] args;
    private TrayIcon trayIcon;

    public JavaFxImpl(String[] args) {
        super(args);
        this.args = args;
        this.silentStart = Arrays.stream(args).anyMatch(s -> s.equalsIgnoreCase("silent"));
        Main.getEventBus().register(this);
    }

    private boolean isWebViewSupported() {
        try {
            Class.forName("javafx.scene.web.WebView");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void showConfigurationSetupDialog() {
        log.info(Lang.CONFIG_PEERBANHELPER);
        JOptionPane.showMessageDialog(null, Lang.CONFIG_PEERBANHELPER, "Dialog", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void setup() {
        super.setup();
        setupSystemTray();
    }

    @Subscribe
    public void onPBHServerStarted(PBHServerStartedEvent event) {
        Platform.runLater(() -> {
            MainJavaFx.getStage().setTitle(String.format(Lang.GUI_TITLE_LOADED, "JavaFx", Main.getMeta().getVersion(), Main.getMeta().getAbbrev()));
            String webuiPath = "http://localhost:" + Main.getServer().getHttpdPort();
            if (isWebViewSupported()) {
                JFXWindowController controller = MainJavaFx.INSTANCE.getController();
                Tab webuiTab = JavaFxWebViewWrapper.installWebViewTab(controller.getTabPane(), webuiPath);
                javafx.scene.control.SingleSelectionModel<Tab> selectionModel = controller.getTabPane().getSelectionModel();
                selectionModel.select(webuiTab);
                log.info(Lang.WEBVIEW_ENABLED);
            } else {
                log.info(Lang.WEBVIEW_DISABLED_WEBKIT_NOT_INCLUDED);
            }
        });
    }

    @SneakyThrows
    @Override
    public void createMainWindow() {
        Platform.setImplicitExit(false);
        CompletableFuture.runAsync(() -> MainJavaFx.launchApp(args));
        while (!MainJavaFx.ready.get()) {
            Thread.sleep(50);
            Thread.yield();
        }
        Platform.runLater(() -> setupJFXWindow());
    }

    private void setupJFXWindow() {
        Stage st = MainJavaFx.getStage();
        st.getScene().getWindow().addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, this::closeWindowEvent);
        JFXWindowController controller = MainJavaFx.INSTANCE.getController();
        Pattern newline = Pattern.compile("\n");
        TextArea textArea = controller.getLogsTextArea();
        textArea.setTextFormatter(new TextFormatter<String>(change -> {
            String newText = change.getControlNewText();
            Matcher matcher = newline.matcher(newText);
            int lines = 1;
            while (matcher.find()) lines++;
            if (lines <= SwingLoggerAppender.maxLinesSetting) return change;
            int linesToDrop = lines - SwingLoggerAppender.maxLinesSetting;
            int index = 0;
            for (int i = 0; i < linesToDrop; i++) {
                index = newText.indexOf('\n', index);
            }
            change.setRange(0, change.getControlText().length());
            change.setText(newText.substring(index + 1));
            return change;
        }));
        initLoggerRedirection();
        controller.getMenuProgram().setText(Lang.GUI_MENU_PROGRAM);
        controller.getMenuWebui().setText(Lang.GUI_MENU_WEBUI);
        controller.getTabLogs().setText(Lang.GUI_TABBED_LOGS);
        controller.getMenuProgramQuit().setText(Lang.GUI_MENU_QUIT);
        controller.getMenuProgramQuit().setOnAction(e -> System.exit(0));
        controller.getMenuProgramOpenInGithub().setText(Lang.ABOUT_VIEW_GITHUB);
        controller.getMenuProgramOpenInGithub().setOnAction(e -> openWebpage(URI.create(Lang.GITHUB_PAGE)));
        controller.getMenuProgramOpenInBrowser().setText(Lang.GUI_MENU_WEBUI_OPEN);
        controller.getMenuProgramOpenInBrowser().setOnAction(e -> openWebpage(URI.create("http://localhost:" + Main.getServer().getWebContainer().javalin().port())));
        controller.getMenuProgramCopyWebuiToken().setText(Lang.GUI_COPY_WEBUI_TOKEN);
        controller.getMenuProgramCopyWebuiToken().setOnAction(e -> {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            String content = Main.getServer().getWebContainer().getToken();
            Transferable ts = new StringSelection(content);
            clipboard.setContents(ts, null);
            createDialog(Level.INFO, Lang.GUI_COPY_TO_CLIPBOARD_TITLE, String.format(Lang.GUI_COPY_TO_CLIPBOARD_DESCRIPTION, content));
        });
        if (silentStart) {
            setVisible(false);
        }
    }

    private void closeWindowEvent(WindowEvent windowEvent) {
        minimizeToTray();
    }

    private void minimizeToTray() {
        if (trayIcon != null) {
            setVisible(false);
            trayIcon.displayMessage(Lang.GUI_TRAY_MESSAGE_CAPTION, Lang.GUI_TRAY_MESSAGE_DESCRIPTION, TrayIcon.MessageType.INFO);
        }
    }

    private void initLoggerRedirection() {
        SwingLoggerAppender.registerListener(loggerEvent -> {
            try {
                Platform.runLater(() -> {
                    JFXWindowController controller = MainJavaFx.INSTANCE.getController();
                    javafx.scene.control.TextArea textArea = controller.getLogsTextArea();
                    textArea.appendText(loggerEvent.message());
                });
            } catch (IllegalStateException exception) {
                exception.printStackTrace();
            }
        });
    }

    public boolean openWebpage(URI uri) {
        Desktop desktop = Desktop.getDesktop();
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            try {
                desktop.browse(uri);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public void sync() {
        super.sync();
    }

    private void setupSystemTray() {
        if (SystemTray.isSupported()) {
            TrayIcon icon = new TrayIcon(Toolkit.getDefaultToolkit().getImage(Main.class.getResource("/assets/icon.png")));
            icon.setImageAutoSize(true);
            //创建弹出菜单
            PopupMenu menu = new PopupMenu();
            //添加一个用于退出的按钮
            MenuItem item = new MenuItem("Exit");
            item.addActionListener(e -> System.exit(0));
            menu.add(item);
            //添加弹出菜单到托盘图标
            icon.setPopupMenu(menu);
            SystemTray tray = SystemTray.getSystemTray();//获取系统托盘
            icon.addActionListener(e -> setVisible(true));
            try {
                tray.add(icon);//将托盘图表添加到系统托盘
                this.trayIcon = icon;
            } catch (AWTException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void setVisible(boolean b) {
        Platform.runLater(() -> {
            if (b) {
                MainJavaFx.getStage().show();
            } else {
                MainJavaFx.getStage().hide();
            }
        });
    }

    @Override
    public void close() {
    }

    @Override
    public void createDialog(Level level, String title, String description) {
        Alert.AlertType alertType = Alert.AlertType.CONFIRMATION;
        if (level.equals(Level.WARNING)) {
            alertType = Alert.AlertType.WARNING;
        }
        if (level.equals(Level.SEVERE)) {
            alertType = Alert.AlertType.ERROR;
        }
        if (level.equals(Level.INFO)) {
            alertType = Alert.AlertType.INFORMATION;
        }
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(description);
        alert.show();
    }

    @Override
    public void createNotification(Level level, String title, String description) {
        Alert.AlertType alertType = Alert.AlertType.NONE;
        if (level.equals(Level.WARNING)) {
            alertType = Alert.AlertType.WARNING;
        }
        if (level.equals(Level.SEVERE)) {
            alertType = Alert.AlertType.ERROR;
        }
        if (level.equals(Level.INFO)) {
            alertType = Alert.AlertType.INFORMATION;
        }
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(description);
        alert.show();
    }
}