package main;

import com.alibaba.fastjson.JSONObject;
import fileMonitor.FileMonitor;
import frame.SearchBar;
import frame.SettingsFrame;
import frame.TaskBar;
import search.Search;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.io.*;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainClass {
    public static final String version = "2.7"; //TODO 更改版本号
    public static boolean mainExit = false;
    public static String name;
    private Search search = new Search();
    private SearchBar searchBar = SearchBar.getInstance();
    private static TaskBar taskBar = null;
    private static MainClass mainInstance = new MainClass();

    public static MainClass getInstance() {
        return mainInstance;
    }

    public void setMainExit(boolean b) {
        mainExit = b;
    }

    public void showMessage(String caption, String message) {
        if (taskBar != null) {
            taskBar.showMessage(caption, message);
        }
    }

    public boolean isAdmin() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe");
            Process process = processBuilder.start();
            PrintStream printStream = new PrintStream(process.getOutputStream(), true);
            Scanner scanner = new Scanner(process.getInputStream());
            printStream.println("@echo off");
            printStream.println(">nul 2>&1 \"%SYSTEMROOT%\\system32\\cacls.exe\" \"%SYSTEMROOT%\\system32\\config\\system\"");
            printStream.println("echo %errorlevel%");

            boolean printedErrorlevel = false;
            while (true) {
                String nextLine = scanner.nextLine();
                if (printedErrorlevel) {
                    int errorlevel = Integer.parseInt(nextLine);
                    return errorlevel == 0;
                } else if (nextLine.equals("echo %errorlevel%")) {
                    printedErrorlevel = true;
                }
            }       
        } catch (IOException e) {
            return false;
        }
    }

    private void copyFile(InputStream source, File dest) {
        try (OutputStream os = new FileOutputStream(dest); BufferedInputStream bis = new BufferedInputStream(source); BufferedOutputStream bos = new BufferedOutputStream(os)) {
            byte[] buffer = new byte[8192];
            int count = bis.read(buffer);
            while (count != -1) {
                //使用缓冲流写数据
                bos.write(buffer, 0, count);
                //刷新
                bos.flush();
                count = bis.read(buffer);
            }
        } catch (IOException ignored) {

        }
    }

    public void deleteDir(String path) {
        File file = new File(path);
        if (!file.exists()) {//判断是否待删除目录是否存在
            return;
        }

        String[] content = file.list();//取得当前目录下所有文件和文件夹
        if (content != null) {
            for (String name : content) {
                File temp = new File(path, name);
                if (temp.isDirectory()) {//判断是否是目录
                    deleteDir(temp.getAbsolutePath());//递归调用，删除目录里的内容
                    temp.delete();//删除空目录
                } else {
                    if (!temp.delete()) {//直接删除文件
                        System.err.println("Failed to delete " + name);
                    }
                }
            }
        }
    }


    public void entry() {
        try {
            System.setProperty("sun.java2d.noddraw", "true");
            org.jb2011.lnf.beautyeye.BeautyEyeLNFHelper.launchBeautyEyeLNF();
            UIManager.put("RootPane.setupButtonVisible", false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String osArch = System.getProperty("os.arch");
        if (osArch.contains("64")) {
            name = "search_x64.exe";
        } else {
            name = "search_x86.exe";
        }
        File settings = SettingsFrame.settings;
        File caches = new File("cache.dat");
        File data = new File("data");

        if (!settings.exists()) {
            String ignorePath = "C:\\Windows,";
            JSONObject json = new JSONObject();
            json.put("hotkey", "Ctrl + Alt + J");
            json.put("ignorePath", ignorePath);
            json.put("isStartup", false);
            json.put("updateTimeLimit", 5);
            json.put("cacheNumLimit", 1000);
            json.put("searchDepth", 6);
            json.put("priorityFolder", "");
            json.put("dataPath", data.getAbsolutePath());
            json.put("isDefaultAdmin", false);
            json.put("isLoseFocusClose", true);
            json.put("openLastFolderKeyCode", 17);
            json.put("runAsAdminKeyCode", 16);
            try (BufferedWriter buffW = new BufferedWriter(new FileWriter(settings))) {
                buffW.write(json.toJSONString());
            } catch (IOException ignored) {

            }
        }

        SettingsFrame.initSettings();

        //清空tmp
        mainInstance.deleteDir(SettingsFrame.tmp.getAbsolutePath());

        File target;
        InputStream fileMonitorDll64 = MainClass.class.getResourceAsStream("/fileMonitor64.dll");
        InputStream fileMonitorDll32 = MainClass.class.getResourceAsStream("/fileMonitor32.dll");
        InputStream fileSearcherDll64 = MainClass.class.getResourceAsStream("/fileSearcher64.exe");
        InputStream fileSearcherDll32 = MainClass.class.getResourceAsStream("/fileSearcher32.exe");
        InputStream fileOpener64 = MainClass.class.getResourceAsStream("/fileOpener64.exe");
        InputStream fileOpener32 = MainClass.class.getResourceAsStream("/fileOpener32.exe");

        target = new File("fileMonitor.dll");
        if (!target.exists()) {
            File dllMonitor;
            if (name.contains("x64")) {
                mainInstance.copyFile(fileMonitorDll64, target);
                System.out.println("已加载64位fileMonitor");
                dllMonitor = new File("fileMonitor64.dll");
            } else {
                mainInstance.copyFile(fileMonitorDll32, target);
                System.out.println("已加载32位fileMonitor");
                dllMonitor = new File("fileMonitor32.dll");
            }
            dllMonitor.renameTo(target);
        }
        target = new File("fileSearcher.exe");
        if (!target.exists()) {
            File dllSearcher;
            if (name.contains("x64")) {
                mainInstance.copyFile(fileSearcherDll64, target);
                System.out.println("已加载64为fileSearcher");
                dllSearcher = new File("fileSearcher64.exe");
            } else {
                mainInstance.copyFile(fileSearcherDll32, target);
                System.out.println("已加载32位fileSearcher");
                dllSearcher = new File("fileSearcher32.exe");
            }
            dllSearcher.renameTo(target);
        }
        target = new File(SettingsFrame.dataPath);
        if (!target.exists()) {
            target.mkdir();
        }
        target = new File("fileOpener.exe");
        if (!target.exists()) {
            File fileOpener;
            if (name.contains("x64")) {
                mainInstance.copyFile(fileOpener64, target);
                System.out.println("已加载64位fileOpener");
                fileOpener = new File("fileOpener64.exe");
            } else {
                mainInstance.copyFile(fileOpener32, target);
                System.out.println("已加载32位fileOpener");
                fileOpener = new File("fileOpener32.exe");
            }
            fileOpener.renameTo(target);
        }

        if (!caches.exists()) {
            try {
                caches.createNewFile();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "创建缓存文件失败，程序正在退出");
                mainExit = true;
            }
        }
        if (!SettingsFrame.tmp.exists()) {
            SettingsFrame.tmp.mkdir();
        }
        File fileAdded = new File(SettingsFrame.tmp.getAbsolutePath() + "\\fileAdded.txt");
        File fileRemoved = new File(SettingsFrame.tmp.getAbsolutePath() + "\\fileRemoved.txt");
        if (!fileAdded.exists()) {
            try {
                fileAdded.createNewFile();
            } catch (IOException ignored) {

            }
        }
        if (!fileRemoved.exists()) {
            try {
                fileRemoved.createNewFile();
            } catch (IOException ignored) {

            }
        }
        taskBar = new TaskBar();
        taskBar.showTaskBar();


        File[] roots = File.listRoots();
        ExecutorService fixedThreadPool = Executors.newFixedThreadPool(roots.length + 4);


        data = new File(SettingsFrame.dataPath);
        if (data.isDirectory() && data.exists()) {
            if (Objects.requireNonNull(data.listFiles()).length != 30) {
                System.out.println("检测到data文件损坏，正在搜索并重建");
                search.setManualUpdate(true);
            }
        } else {
            System.out.println("无data文件，正在搜索并重建");
            search.setManualUpdate(true);
        }

        FileSystemView sys = FileSystemView.getFileSystemView();
        if (isAdmin()) {
            for (File root : roots) {
                String dirveType = sys.getSystemTypeDescription(root);
                if (dirveType.equals("本地磁盘")) {
                    fixedThreadPool.execute(() -> FileMonitor.INSTANCE.monitor(root.getAbsolutePath(), SettingsFrame.tmp.getAbsolutePath(), SettingsFrame.tmp.getAbsolutePath() + "\\CLOSE"));
                }
            }
        } else {
            System.out.println("无管理员权限，文件监控功能已关闭");
            taskBar.showMessage("警告", "无管理员权限，文件监控功能已关闭");
        }


        fixedThreadPool.execute(() -> {
            //检测文件改动线程
            String filesToAdd;
            String filesToRemove;
            BufferedReader readerAdd = null;
            BufferedReader readerRemove = null;
            try {
                readerAdd = new BufferedReader(new InputStreamReader(new FileInputStream(new File(SettingsFrame.tmp.getAbsolutePath() + "\\fileAdded.txt"))));
                readerRemove = new BufferedReader(new InputStreamReader(new FileInputStream(new File(SettingsFrame.tmp.getAbsolutePath() + "\\fileRemoved.txt"))));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            //分割字符串
            while (!mainExit) {
                if (!search.isManualUpdate()) {
                    try {
                        if (readerAdd != null) {
                            while ((filesToAdd = readerAdd.readLine()) != null) {
                                if (!filesToAdd.contains(SettingsFrame.dataPath)) {
                                    search.addFileToLoadBin(filesToAdd);
                                    System.out.println("添加" + filesToAdd);
                                }
                            }
                        }
                        if (readerRemove != null) {
                            while ((filesToRemove = readerRemove.readLine()) != null) {
                                if (!filesToRemove.contains(SettingsFrame.dataPath)) {
                                    search.addToRecycleBin(filesToRemove);
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {

                }
            }
            if (readerAdd != null) {
                try {
                    readerAdd.close();
                } catch (IOException ignored) {

                }
            }
            if (readerRemove != null) {
                try {
                    readerRemove.close();
                } catch (IOException ignored) {

                }
            }
        });


        fixedThreadPool.execute(() -> {
            // 时间检测线程
            long count = 0;
            long gcCount = 0;
            while (!mainExit) {
                boolean isUsing = searchBar.isUsing();
                count++;
                gcCount++;
                if (count >= (SettingsFrame.updateTimeLimit << 10) && !isUsing && !search.isManualUpdate()) {
                    count = 0;
                    if (search.isUsable() && (!searchBar.isUsing())) {
                        search.mergeFileToList();
                    }
                }
                if (gcCount > 600000){
                    System.out.println("正在释放内存空间");
                    System.gc();
                    gcCount = 0;
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException ignore) {

                }
            }
        });


        //搜索线程
        fixedThreadPool.execute(() -> {
            while (!mainExit) {
                if (search.isManualUpdate()) {
                    search.setUsable(false);
                    search.updateLists(SettingsFrame.ignorePath, SettingsFrame.searchDepth);
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException ignored) {

                }
            }
        });

        while (true) {
            // 主循环开始
            try {
                Thread.sleep(1);
            } catch (InterruptedException ignored) {

            }
            if (mainExit) {
                File CLOSEDLL = new File(SettingsFrame.tmp.getAbsolutePath() + "\\CLOSE");
                try {
                    CLOSEDLL.createNewFile();
                } catch (IOException ignored) {

                }
                try {
                    Thread.sleep(20000);
                } catch (InterruptedException ignored) {

                }
                fixedThreadPool.shutdown();
                System.exit(0);
            }
        }
    }

    public static void main(String[] args) {
        mainInstance.entry();
    }
}
