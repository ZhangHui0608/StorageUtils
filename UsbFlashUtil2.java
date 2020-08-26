package com.ttc.tool.utils;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.storage.StorageManager;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Date:2020/06/18
 *
 * 获取U盘的根路径
 */
public class UsbFlashUtil {
    private static UsbFlashUtil instance;
    private String usbPath = null;
    private Application application;

    public static UsbFlashUtil getInstance() {
        if (null == instance){
            synchronized (UsbFlashUtil.class){
                if (null == instance){
                    instance = new UsbFlashUtil();
                }
            }
        }
        return instance;
    }

    /**
     * 注册监听U盘拔插广播
     */
    public void registerBroadcast(Application application){
        this.application = application;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_SHARED);//如果SDCard未安装,并通过USB大容量存储共享返回
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);//表明sd对象是存在并具有读/写权限
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);//SDCard已卸掉,如果SDCard是存在但没有被安装
        filter.addAction(Intent.ACTION_MEDIA_CHECKING);  //表明对象正在磁盘检查
        filter.addAction(Intent.ACTION_MEDIA_EJECT);  //物理的拔出 SDCARD
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);  //完全拔出
        filter.addDataScheme("file"); // 必须要有此行，否则无法收到广播
        this.application.getApplicationContext().registerReceiver(receiverUsb, filter);

        //在本app未启动前已经插着U盘的情况下，获取U盘路径
        List<String> list = getPathListByStorageManager();//根据StorageManager获取U盘路径
//        List<String> list = getPathByMount();//根据mount命令获取U盘路径
        if (list.size() > 0)UsbFlashUtil.this.usbPath = list.get(0);
    }

    /**
     * 注销广播
     */
    public void unregisterBroadcast(){
        this.application.getApplicationContext().unregisterReceiver(receiverUsb);
    }
    /**
     * 获取U盘根路径
     */
    public String getUsbPath() {return UsbFlashUtil.this.usbPath;}

    /**
     * 检测U盘插入和拔出状态
     */
    private BroadcastReceiver receiverUsb = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {//可以读到/mnt/usb_storage/USB_DISK2/udisk0这个目录
                //U盘插入
                String path = intent.getData().getPath();
                path = getCorrectPath(path);//获取正确的，完整的路径
                UsbFlashUtil.this.usbPath = path;
                Util.logE("------>U盘路径："+UsbFlashUtil.this.usbPath);
                if (diskListenerList.size() > 0){
                    for (int i = 0; i < diskListenerList.size(); i++) {
                        if (null != diskListenerList.get(i))diskListenerList.get(i).onConnect();
                    }
                }
            } else if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action) || Intent.ACTION_MEDIA_EJECT.equals(action)) {
                //U盘拔出
                UsbFlashUtil.this.usbPath = "";
                if (diskListenerList.size() > 0){
                    for (int i = 0; i < diskListenerList.size(); i++) {
                        if (null != diskListenerList.get(i))diskListenerList.get(i).onDisconnect();
                    }
                }
            }
        }
    };

    /**
     * 获取正确的U盘路径，才可以读写文件
     * 有些板子直接拿到的U盘路径是/mnt/usb_storage/udisk0（正确），/mnt/usb_storage/USB_DISK2（不完整）
     * 部分板子会出现"/mnt/usb_storage/USB_DISK2"的不完整路径，需要再追加子目录文件名，
     * 如追加上udisk0："/mnt/usb_storage/USB_DISK2/udisk0"，有的U盘命名过名字，追加的不一定是udisk0
     */
    private String getCorrectPath(String path) {
        if (!TextUtils.isEmpty(path)){
            int lastSeparator = path.lastIndexOf(File.separator);
            String endStr = path.substring(lastSeparator + 1, path.length());
            if (!TextUtils.isEmpty(endStr) && (endStr.contains("USB_DISK") || endStr.contains("usb_disk"))){//不区分大小写
                File file = new File(path);
                if (file.exists() && file.listFiles().length == 1 && file.listFiles()[0].isDirectory()){
                    path = file.listFiles()[0].getAbsolutePath();
                }
            }
        }
        return path;
    }


    /**
     * 根据StorageManager获取Usb插入的U盘路径
     * 可以获取内部存储、sd卡以及所有usb路径
     * 获取到的路径可能是不完整的，需要判断追加
     */
    private List<String> getPathListByStorageManager() {
        List<String> pathList = new ArrayList<>();
        try {
            StorageManager storageManager = (StorageManager)this.application.getSystemService(Context.STORAGE_SERVICE);
            Method method_volumeList = StorageManager.class.getMethod("getVolumeList");
            method_volumeList.setAccessible(true);
            Object[] volumeList = (Object[]) method_volumeList.invoke(storageManager);
            if (volumeList != null) {
                for (int i = 0; i < volumeList.length; i++) {
                    try {
                        String path = (String) volumeList[i].getClass().getMethod("getPath").invoke(volumeList[i]);
                        boolean isRemovable = (boolean) volumeList[i].getClass().getMethod("isRemovable").invoke(volumeList[i]);
                        String state = (String) volumeList[i].getClass().getMethod("getState").invoke(volumeList[i]);
//                        Util.logE("isRemovable:"+isRemovable+" / state:"+state+" / path:"+path);
                        if (isRemovable && "mounted".equalsIgnoreCase(state) && path.contains("usb_storage")){
                            pathList.add(getCorrectPath(path));//将正确的路径添加到集合中
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pathList;
    }

    /**
     * 使用mount命令获取usb插入的U盘路径
     * 可以获取内部存储、外部存储、tf卡、otg、系统分区等路径，获取到的U盘路径是完整的正确的
     * 限制条件是机子必须得解开root
     */
    public static List<String> getPathByMount() {
        List<String> usbMemoryList = new ArrayList<>();
        try {
            Runtime runtime = Runtime.getRuntime();
            // 运行mount命令，获取命令的输出，得到系统中挂载的所有目录
            Process process = runtime.exec("mount");
            InputStream is = process.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            String line;
            BufferedReader br = new BufferedReader(isr);
            while ((line = br.readLine()) != null) {
                // 将常见的linux分区过滤掉
                if (line.contains("proc") || line.contains("tmpfs") || line.contains("media") || line.contains("asec")
					|| line.contains("secure") || line.contains("system") || line.contains("cache")|| line.contains("sys")
					|| line.contains("data") || line.contains("shell") || line.contains("root") || line.contains("acct")
					|| line.contains("misc") || line.contains("obb")) {
                    continue;
                }
//                Util.logE("==========>line:"+line);
                if (!TextUtils.isEmpty(line) && line.contains("usb_storage")){//根据情况过来需要的字段
                    String items[] = line.split(" ");
                    if (null != items && items.length > 1) {
                        String path = items[1];
//                        Util.logE("------->path:"+path);
                        // 添加一些判断，确保是sd卡，如果是otg等挂载方式，可以具体分析并添加判断条件
                        if (path != null && !usbMemoryList.contains(path) && path.contains("sd"))usbMemoryList.add(items[1]);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return usbMemoryList;
    }


    /**
     * U盘连接状态回调
     */
    public interface IUDiskListener{
        void onConnect();
        void onDisconnect();
    }
    private List<IUDiskListener> diskListenerList = new ArrayList<>();
    public void setUDiskListener(IUDiskListener uDiskListener){
        diskListenerList.add(uDiskListener);
    }
    public void removeUDiskListener(IUDiskListener uDiskListener){
        diskListenerList.remove(uDiskListener);
    }
}
