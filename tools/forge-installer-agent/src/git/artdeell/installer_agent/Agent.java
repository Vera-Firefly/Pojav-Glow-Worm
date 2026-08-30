package git.artdeell.installer_agent;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

public class Agent implements AWTEventListener {
    private boolean forgeWindowHandled;
    private final boolean suppressProfileCreation;
    private final boolean optiFineInstallation;
    private final String modpackFixupId;
    private final Timer componentTimer = new Timer();

    public Agent(boolean nps, boolean of, String mf) {
        suppressProfileCreation = !nps;
        optiFineInstallation = of;
        modpackFixupId = mf;
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        if (!(event instanceof WindowEvent)) return;

        WindowEvent windowEvent = (WindowEvent) event;
        if (windowEvent.getID() != WindowEvent.WINDOW_OPENED) return;

        Window window = windowEvent.getWindow();
        if (forgeWindowHandled && window instanceof JDialog) {
            handleDialog(window);
            return;
        }

        if (!forgeWindowHandled) {
            forgeWindowHandled = handleMainWindow(window);
            checkComponentTimer();
        }
    }

    public void checkComponentTimer() {
        if (forgeWindowHandled) {
            componentTimer.cancel();
            componentTimer.purge();
            return;
        }

        componentTimer.schedule(new ComponentTimeoutTask(), 30_000L);
    }

    public boolean handleMainWindow(Window window) {
        List<Component> components = new ArrayList<>();
        insertAllComponents(components, window, new MainWindowFilter());
        AbstractButton okButton = null;
        for (Component component : components) {
            if (!(component instanceof AbstractButton)) continue;

            AbstractButton button = (AbstractButton) component;
            AbstractButton handledButton = optiFineInstallation
                    ? handleOptiFineButton(button)
                    : handleForgeButton(button);
            if (handledButton != null) okButton = handledButton;
        }

        if (okButton == null) {
            System.out.println("Failed to set all the UI components, wil try again in the next window");
            return false;
        }

        ProfileFixer.storeProfile(optiFineInstallation ? "OptiFine" : "forge");
        EventQueue.invokeLater(okButton::doClick);
        return true;
    }

    public AbstractButton handleForgeButton(AbstractButton button) {
        String text = button.getText();
        if ("OK".equals(text)) return button;
        if ("Install client".equals(text)) button.doClick();
        return null;
    }

    public AbstractButton handleOptiFineButton(AbstractButton button) {
        return "Install".equals(button.getText()) ? button : null;
    }

    public void handleDialog(Window window) {
        List<Component> components = new ArrayList<>();
        insertAllComponents(components, window, new DialogFilter());
        if (components.size() != 1) return;

        JOptionPane optionPane = (JOptionPane) components.get(0);
        if (optionPane.getMessageType() != JOptionPane.INFORMATION_MESSAGE) return;

        System.out.println("The install was successful!");
        ProfileFixer.reinsertProfile(optiFineInstallation ? "OptiFine" : "forge", modpackFixupId, suppressProfileCreation);
        System.exit(0);
    }

    public void insertAllComponents(List<Component> components, Container parent, ComponentFilter filter) {
        int componentCount = parent.getComponentCount();
        for (int i = 0; i < componentCount; i++) {
            Component component = parent.getComponent(i);
            if (filter.checkComponent(component)) components.add(component);
            if (component instanceof Container) {
                insertAllComponents(components, (Container) component, filter);
            }
        }
    }

    public static void premain(String args, Instrumentation instrumentation) {
        boolean noProfileSuppression = false;
        boolean optiFine = false;
        String modpackFixup = null;
        if (args != null) {
            modpackFixup = findQuotedString(args);
            if (modpackFixup != null) {
                noProfileSuppression = args.contains("NPS") && !modpackFixup.contains("NPS");
                optiFine = args.contains("OF") && !modpackFixup.contains("OF");
            } else {
                noProfileSuppression = args.contains("NPS");
                optiFine = args.contains("OF");
            }
        }

        Agent agent = new Agent(noProfileSuppression, optiFine, modpackFixup);
        Toolkit.getDefaultToolkit().addAWTEventListener(agent, AWTEvent.WINDOW_EVENT_MASK);
    }

    private static String findQuotedString(String text) {
        int start = text.indexOf('"');
        if (start == -1) return null;
        int end = text.indexOf('"', start + 1);
        if (end == -1) return null;
        return text.substring(start + 1, end);
    }
}
