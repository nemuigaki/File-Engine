package file.engine.services;

import file.engine.annotation.EventRegister;
import file.engine.configs.Constants;
import file.engine.event.handler.Event;
import file.engine.event.handler.EventManagement;
import file.engine.event.handler.impl.daemon.StartDaemonEvent;
import file.engine.event.handler.impl.daemon.StopDaemonEvent;
import file.engine.event.handler.impl.open.file.OpenFileEvent;
import file.engine.services.utils.DaemonUtil;
import file.engine.utils.ProcessUtil;
import file.engine.utils.system.properties.IsDebug;

import java.io.File;
import java.io.IOException;

public class DaemonService {

    private static final String OPEN_FROM_JAR_SIGNAL = "tmp/openFromJar";

    @EventRegister(registerClass = StopDaemonEvent.class)
    private static void stopDaemon(Event event) {
        DaemonUtil.stopDaemon();
        try {
            ProcessUtil.waitForProcess(Constants.LAUNCH_WRAPPER_NAME, 10);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @EventRegister(registerClass = StartDaemonEvent.class)
    private static void startDaemon(Event event) {
        File launcher = new File("..", Constants.LAUNCH_WRAPPER_NAME);
        if (IsDebug.isDebug()) {
            System.out.println("启动守护进程" + Constants.LAUNCH_WRAPPER_NAME);
            return;
        }
        File openFromJar = new File(OPEN_FROM_JAR_SIGNAL);
        if (!openFromJar.exists()) {
            try {
                if (!openFromJar.createNewFile()) {
                    System.err.println("error create open from jar file signal. File: " + OPEN_FROM_JAR_SIGNAL);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        EventManagement.getInstance().putEvent(new OpenFileEvent(OpenFileEvent.OpenStatus.WITH_ADMIN, launcher.getAbsolutePath()));
    }
}
