package io.github.steinbeck.seneca.app;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

/**
 * The main window "Seneca" (F-10): {@link MainView} as content ({@link MainView#DEFAULT_SIZE}, reduced
 * to the usable screen area on small screens), {@link MainMenuBar}, centred on the screen; the close box asks for confirmation (F-23). Window shell only: all content lives in
 * {@link MainView}, which the tests build without a frame.
 */
public final class MainFrame extends JFrame {

    private final MainView view;

    public MainFrame(AppContext app) {
        super(app.resources.text("Title"));
        AppActions actions = new AppActions(app);
        view = new MainView(app, actions);
        setContentPane(view);
        setJMenuBar(MainMenuBar.create(app, actions));
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                actions.exit.actionPerformed(null);
            }
        });
        if (app.resources.logo() != null) setIconImage(app.resources.logo().getImage());
        pack();
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(getWidth(), usable.width), Math.min(getHeight(), usable.height));
        setLocation(usable.x + (usable.width - getWidth()) / 2, usable.y + (usable.height - getHeight()) / 2);
    }

    public MainView view() {
        return view;
    }
}
