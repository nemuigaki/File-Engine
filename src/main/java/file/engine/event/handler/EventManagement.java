package file.engine.event.handler;

import file.engine.annotation.EventListener;
import file.engine.annotation.EventRegister;
import file.engine.event.handler.impl.stop.CloseEvent;
import file.engine.event.handler.impl.stop.RestartEvent;
import file.engine.utils.CachedThreadPoolUtil;
import file.engine.utils.ProcessUtil;
import file.engine.utils.clazz.scan.ClassScannerUtil;
import file.engine.utils.system.properties.IsDebug;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class EventManagement {
    private static volatile EventManagement instance = null;
    private final AtomicBoolean exit = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<Event> blockEventQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Event> asyncEventQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, Method> EVENT_HANDLER_MAP = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Method>> EVENT_LISTENER_MAP = new ConcurrentHashMap<>();
    private final AtomicInteger failureEventNum = new AtomicInteger(0);

    private EventManagement() {
        startBlockEventHandler();
        startAsyncEventHandler();
    }

    public static EventManagement getInstance() {
        if (instance == null) {
            synchronized (EventManagement.class) {
                if (instance == null) {
                    instance = new EventManagement();
                }
            }
        }
        return instance;
    }

    /**
     * 等待任务
     *
     * @param event 任务实例
     * @return true如果任务执行失败， false如果执行正常完成
     */
    public boolean waitForEvent(Event event) {
        try {
            final long timeout = 20000; // 20s
            long startTime = System.currentTimeMillis();
            while (!event.isFailed() && !event.isFinished()) {
                if (System.currentTimeMillis() - startTime > timeout) {
                    System.err.println("等待" + event + "超时");
                    break;
                }
                TimeUnit.MILLISECONDS.sleep(5);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return event.isFailed();
    }

    /**
     * 执行任务
     *
     * @param event 任务
     * @return true如果执行失败，false执行成功
     */
    private boolean executeTaskFailed(Event event) {
        event.incrementExecuteTimes();
        if (event instanceof RestartEvent) {
            exit.set(true);
            doAllMethod(RestartEvent.class.toString(), event);
            if (event instanceof CloseEvent) {
                ProcessUtil.stopDaemon();
            }
            event.setFinished();
            CachedThreadPoolUtil.getInstance().shutdown();
            System.exit(0);
        } else {
            String eventClassName = event.getClass().toString();
            Method eventHandler = EVENT_HANDLER_MAP.get(eventClassName);
            if (eventHandler != null) {
                try {
                    eventHandler.invoke(null, event);
                    event.setFinished();
                    doAllMethod(eventClassName, event);
                    return false;
                } catch (Exception e) {
                    e.printStackTrace();
                    return true;
                }
            } else {
                event.setFinished();
                doAllMethod(eventClassName, event);
            }
            return false;
        }
        return true;
    }

    /**
     * 用于在debug时查看在哪个位置发出的任务
     * 由于执行任务的调用栈长度超过3，所以不会出现数组越界
     *
     * @return stackTraceElement
     */
    private StackTraceElement getStackTraceElement() {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        return stacktrace[3];
    }

    /**
     * 执行所有监听了该Event的任务链
     *
     * @param eventType 任务类型
     * @param event     任务
     */
    private void doAllMethod(String eventType, Event event) {
        ConcurrentLinkedQueue<Method> methodChains = EVENT_LISTENER_MAP.get(eventType);
        if (methodChains == null) {
            return;
        }
        CachedThreadPoolUtil.getInstance().executeTask(() -> {
            for (Method each : methodChains) {
                try {
                    each.invoke(null, event);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 发送任务
     * 不要在构造函数中执行，单例模式下可能会导致死锁
     *
     * @param event 任务
     */
    public void putEvent(Event event) {
        boolean isDebug = IsDebug.isDebug();
        if (isDebug) {
            System.err.println("尝试放入任务" + event.toString() + "---来自" + getStackTraceElement().toString());
        }
        if (!exit.get()) {
            if (event.isBlock()) {
                if (!blockEventQueue.contains(event)) {
                    blockEventQueue.add(event);
                }
            } else {
                if (!asyncEventQueue.contains(event)) {
                    asyncEventQueue.add(event);
                }
            }
        } else {
            if (isDebug) {
                System.err.println("任务已被拒绝---" + event);
            }
        }
    }

    /**
     * 异步回调方法发送任务
     * 不要在构造函数中执行，单例模式下可能会导致死锁
     *
     * @param event        任务
     * @param callback     回调函数
     * @param errorHandler 错误处理
     */
    public void putEvent(Event event, Consumer<Event> callback, Consumer<Event> errorHandler) {
        event.setCallback(callback);
        event.setErrorHandler(errorHandler);
        putEvent(event);
    }

    public boolean isNotMainExit() {
        return !exit.get();
    }

    /**
     * 注册所有事件处理器
     */
    public void registerAllHandler() {
        try {
            ClassScannerUtil.searchAndRun(EventRegister.class, (annotationClass, method) -> {
                EventRegister annotation = (EventRegister) method.getAnnotation(annotationClass);
                if (IsDebug.isDebug()) {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (!Modifier.isStatic(method.getModifiers())) {
                        throw new RuntimeException("方法不是static" + method);
                    }
                    if (Arrays.stream(parameterTypes).noneMatch(each -> each.equals(Event.class)) || method.getParameterCount() != 1) {
                        throw new RuntimeException("注册handler方法参数错误" + method);
                    }
                }
                registerHandler(annotation.registerClass().toString(), method);
            });
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * 注册所有时间监听器
     */
    public void registerAllListener() {
        try {
            ClassScannerUtil.searchAndRun(EventListener.class, (annotationClass, method) -> {
                EventListener annotation = (EventListener) method.getAnnotation(annotationClass);
                if (IsDebug.isDebug()) {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (!Modifier.isStatic(method.getModifiers())) {
                        throw new RuntimeException("方法不是static" + method);
                    }
                    if (Arrays.stream(parameterTypes).noneMatch(each -> each.equals(Event.class)) || method.getParameterCount() != 1) {
                        throw new RuntimeException("注册Listener方法参数错误" + method);
                    }
                }
                for (Class<? extends Event> aClass : annotation.listenClass()) {
                    registerListener(aClass.toString(), method);
                }
            });
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * 注册任务监听器
     *
     * @param eventType 需要监听的任务类型
     * @param handler   需要执行的操作
     */
    private void registerHandler(String eventType, Method handler) {
        if (IsDebug.isDebug()) {
            System.err.println("注册监听器" + eventType);
        }
        if (EVENT_HANDLER_MAP.containsKey(eventType)) {
            throw new RuntimeException("重复的监听器：" + eventType + "方法：" + handler);
        }
        EVENT_HANDLER_MAP.put(eventType, handler);
    }

    /**
     * 监听某个任务被发出，并不是执行任务
     *
     * @param eventType 需要监听的任务类型
     */
    private void registerListener(String eventType, Method todo) {
        ConcurrentLinkedQueue<Method> queue = EVENT_LISTENER_MAP.get(eventType);
        if (queue == null) {
            queue = new ConcurrentLinkedQueue<>();
            queue.add(todo);
            EVENT_LISTENER_MAP.put(eventType, queue);
        } else {
            queue.add(todo);
        }
    }

    /**
     * 开启异步任务处理中心
     */
    private void startAsyncEventHandler() {
        CachedThreadPoolUtil threadPoolUtil = CachedThreadPoolUtil.getInstance();
        for (int i = 0; i < 4; i++) {
            int finalI = i;
            threadPoolUtil.executeTask(() -> {
                final boolean isDebug = IsDebug.isDebug();
                try {
                    Event event;
                    while (isEventHandlerNotExit()) {
                        //取出任务
                        if ((event = asyncEventQueue.poll()) == null) {
                            TimeUnit.MILLISECONDS.sleep(5);
                            continue;
                        }
                        //判断任务是否执行完成或者失败
                        if (event.isFinished() || event.isFailed()) {
                            continue;
                        }
                        if (event.getExecuteTimes() < event.getMaxRetryTimes()) {
                            //判断是否超过最大次数
                            if (executeTaskFailed(event)) {
                                System.err.println("异步任务执行失败---" + event);
                                asyncEventQueue.add(event);
                            }
                        } else {
                            event.setFailed();
                            failureEventNum.incrementAndGet();
                            if (isDebug) {
                                System.err.println("任务超时---" + event);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    if (isDebug) {
                        System.err.println("******异步任务执行线程" + finalI + "退出******");
                    }
                }
            });
        }
    }

    /**
     * 检查是否所有任务执行完毕再推出
     *
     * @return boolean
     */
    private boolean isEventHandlerNotExit() {
        return !(exit.get() && blockEventQueue.isEmpty() && asyncEventQueue.isEmpty());
    }

    /**
     * 开启同步任务事件处理中心
     */
    private void startBlockEventHandler() {
        new Thread(() -> {
            final boolean isDebug = IsDebug.isDebug();
            try {
                Event event;
                while (isEventHandlerNotExit()) {
                    //取出任务
                    if ((event = blockEventQueue.poll()) == null) {
                        TimeUnit.MILLISECONDS.sleep(5);
                        continue;
                    }
                    //判断任务是否已经被执行或者失败
                    if (event.isFinished() || event.isFailed()) {
                        continue;
                    }
                    //判断任务是否超过最大执行次数
                    if (event.getExecuteTimes() < event.getMaxRetryTimes()) {
                        if (executeTaskFailed(event)) {
                            if (failureEventNum.get() > 20) {
                                System.err.println("超过20个任务失败，自动重启");
                                putEvent(new RestartEvent());
                            }
                            System.err.println("同步任务执行失败---" + event);
                            blockEventQueue.add(event);
                        }
                    } else {
                        event.setFailed();
                        failureEventNum.incrementAndGet();
                        if (isDebug) {
                            System.err.println("任务超时---" + event);
                        }
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                if (isDebug) {
                    System.err.println("******同步任务执行线程退出******");
                }
            }
        }).start();
    }
}
