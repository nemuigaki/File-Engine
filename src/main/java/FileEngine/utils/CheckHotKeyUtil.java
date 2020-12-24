package FileEngine.utils;

import FileEngine.configs.Enums;
import FileEngine.dllInterface.HotkeyListener;
import FileEngine.eventHandler.EventUtil;
import FileEngine.eventHandler.Event;
import FileEngine.eventHandler.EventHandler;
import FileEngine.eventHandler.impl.frame.searchBar.HideSearchBarEvent;
import FileEngine.eventHandler.impl.frame.searchBar.ShowSearchBarEvent;
import FileEngine.eventHandler.impl.hotkey.RegisterHotKeyEvent;
import FileEngine.eventHandler.impl.hotkey.StopListenHotkeyEvent;
import FileEngine.frames.SearchBar;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class CheckHotKeyUtil {

    private final HashMap<String, Integer> map;
    private final Pattern plus;
    private boolean isRegistered = false;
    private static volatile CheckHotKeyUtil INSTANCE = null;

    public static CheckHotKeyUtil getInstance() {
        initInstance();
        return INSTANCE;
    }

    private static void initInstance() {
        if (INSTANCE == null) {
            synchronized (CheckHotKeyUtil.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CheckHotKeyUtil();
                }
            }
        }
    }

    //关闭对热键的检测，在程序彻底关闭时调用
    private void stopListen() {
        HotkeyListener.INSTANCE.stopListen();
    }

    //注册快捷键
    private void registerHotkey(String hotkey) {
        if (!isRegistered) {
            isRegistered = true;
            int hotkey1 = -1, hotkey2 = -1, hotkey3 = -1, hotkey4 = -1, hotkey5;
            String[] hotkeys = plus.split(hotkey);
            int length = hotkeys.length;
            for (int i = 0; i < length - 1; i++) {
                if (i == 0) {
                    hotkey1 = map.get(hotkeys[i]);
                } else if (i == 1) {
                    hotkey2 = map.get(hotkeys[i]);
                } else if (i == 2) {
                    hotkey3 = map.get(hotkeys[i]);
                } else if (i == 4) {
                    hotkey4 = map.get(hotkeys[i]);
                }
            }
            hotkey5 = hotkeys[length - 1].charAt(0);
            int finalHotkey = hotkey1;
            int finalHotkey1 = hotkey2;
            int finalHotkey2 = hotkey3;
            int finalHotkey3 = hotkey4;
            int finalHotkey4 = hotkey5;
            CachedThreadPoolUtil.getInstance().executeTask(() -> {
                HotkeyListener.INSTANCE.registerHotKey(finalHotkey, finalHotkey1, finalHotkey2, finalHotkey3, finalHotkey4);
                HotkeyListener.INSTANCE.startListen();
            });
        } else {
            changeHotKey(hotkey);
        }
    }

    //检查快捷键是否有效
    public boolean isHotkeyAvailable(String hotkey) {
        String[] hotkeys = plus.split(hotkey);
        int length = hotkeys.length;
        for (int i = 0; i < length - 1; i++) {
            String each = hotkeys[i];
            if (!map.containsKey(each)) {
                return false;
            }
        }
        return 64 < hotkey.charAt(hotkey.length() - 1) && hotkey.charAt(hotkey.length() - 1) < 91;
    }

    //更改快捷键,必须在register后才可用
    private void changeHotKey(String hotkey) {
        if (!isRegistered) {
            throw new NullPointerException();
        }
        int hotkey1 = -1, hotkey2 = -1, hotkey3 = -1, hotkey4 = -1, hotkey5;
        String[] hotkeys = plus.split(hotkey);
        int length = hotkeys.length;
        for (int i = 0; i < length - 1; i++) {
            if (i == 0) {
                hotkey1 = map.get(hotkeys[i]);
            } else if (i == 1) {
                hotkey2 = map.get(hotkeys[i]);
            } else if (i == 2) {
                hotkey3 = map.get(hotkeys[i]);
            } else if (i == 4) {
                hotkey4 = map.get(hotkeys[i]);
            }
        }
        hotkey5 = hotkeys[length - 1].charAt(0);
        HotkeyListener.INSTANCE.registerHotKey(hotkey1, hotkey2, hotkey3, hotkey4, hotkey5);
    }
    
    private void startListenHotkeyThread() {
        CachedThreadPoolUtil.getInstance().executeTask(() -> {
            boolean isExecuted = false;
            long startVisibleTime = 0;
            long endVisibleTime = 0;
            SearchBar searchBar = SearchBar.getInstance();
            HotkeyListener instance = HotkeyListener.INSTANCE;
            try {
                //获取快捷键状态，检测是否被按下线程
                while (EventUtil.getInstance().isNotMainExit()) {
                    if (!isExecuted && instance.getKeyStatus()) {
                        isExecuted = true;
                        if (!searchBar.isVisible()) {
                            if (System.currentTimeMillis() - endVisibleTime > 200) {
                                EventUtil.getInstance().putEvent(new ShowSearchBarEvent(true));
                                startVisibleTime = System.currentTimeMillis();
                            }
                        } else {
                            if (System.currentTimeMillis() - startVisibleTime > 200) {
                                if (searchBar.getShowingMode() == Enums.ShowingSearchBarMode.NORMAL_SHOWING) {
                                    EventUtil.getInstance().putEvent(new HideSearchBarEvent());
                                    endVisibleTime = System.currentTimeMillis();
                                }
                            }
                        }
                    }
                    if (!instance.getKeyStatus()) {
                        isExecuted = false;
                    }
                    TimeUnit.MILLISECONDS.sleep(10);
                }
            } catch (InterruptedException ignored) {
            }
        });
    }
    
    public static void registerEventHandler() {
        EventUtil.getInstance().register(RegisterHotKeyEvent.class, new EventHandler() {
            @Override
            public void todo(Event event) {
                getInstance().registerHotkey(((RegisterHotKeyEvent) event).hotkey);
            }
        });

        EventUtil.getInstance().register(StopListenHotkeyEvent.class, new EventHandler() {
            @Override
            public void todo(Event event) {
                getInstance().stopListen();
            }
        });
    }
    
    private void initThreadPool() {
        startListenHotkeyThread();
    }

    private CheckHotKeyUtil() {
        plus = Pattern.compile(" \\+ ");
        map = new HashMap<>();
        map.put("Ctrl", KeyEvent.VK_CONTROL);
        map.put("Alt", KeyEvent.VK_ALT);
        map.put("Shift", KeyEvent.VK_SHIFT);
        map.put("Win", 0x5B);

        initThreadPool();
    }
}
