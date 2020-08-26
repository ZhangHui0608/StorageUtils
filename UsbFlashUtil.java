package com.backaudio.books.utils;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.storage.StorageManager;
import android.text.TextUtils;
import android.util.Log;


import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Date:2020/08/19
 * Author:zhanghui
 * <p>
 * 获取U盘的根路径
 */
public class UsbFlashUtil {
    private final static String TAG = "UsbFlashUtil";

    public static final String ACTION_VOLUME_STATE_CHANGED = "android.os.storage.action.VOLUME_STATE_CHANGED";
    public static final String EXTRA_VOLUME_STATE = "android.os.storage.extra.VOLUME_STATE";

    public static final int STATE_UNMOUNTED = 0; //卸载
    public static final int STATE_CHECKING = 1; //状态监测
    public static final int STATE_MOUNTED = 2; //挂载完成
    public static final int STATE_MOUNTED_READ_ONLY = 3;
    public static final int STATE_FORMATTING = 4;
    public static final int STATE_EJECTING = 5;
    public static final int STATE_UNMOUNTABLE = 6;
    public static final int STATE_REMOVED = 7;
    public static final int STATE_BAD_REMOVAL = 8;

    public static final int TYPE_PUBLIC = 0;
    public static final int TYPE_PRIVATE = 1;
    public static final int TYPE_EMULATED = 2;

    private static UsbFlashUtil instance;
    private String usbPath = null;
    private Application application;

    public static UsbFlashUtil getInstance() {
        if (null == instance) {
            synchronized (UsbFlashUtil.class) {
                if (null == instance) {
                    instance = new UsbFlashUtil();
                }
            }
        }
        return instance;
    }

    /**
     * 注册监听U盘拔插广播
     */
    public void registerBroadcast(Application application) {
        Log.d(TAG, "registerBroadcast: ");
        this.application = application;
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_VOLUME_STATE_CHANGED);
//        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
//        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
//        filter.addDataScheme("file"); // 必须要有此行，否则无法收到广播
        application.getApplicationContext().registerReceiver(receiverUsb, filter);

        //在本app未启动前已经插着U盘的情况下，获取U盘路径
        usbPath = getExternalPathByGetVolumes();
    }

    /**
     * 注销广播
     */
    public void unregisterBroadcast() {
        Log.d(TAG, "unregisterBroadcast: ");
        application.getApplicationContext().unregisterReceiver(receiverUsb);
    }

    /**
     * 获取U盘根路径
     */
    public String getUsbPath() {
        return usbPath;
    }

    /**
     * 检测U盘插入和拔出状态
     */
    private BroadcastReceiver receiverUsb = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.e(TAG, "onReceive: " + action);
            if (action != null) {
                switch (action) {
                    case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                        break;
                    case ACTION_VOLUME_STATE_CHANGED:
                        int state = intent.getIntExtra(EXTRA_VOLUME_STATE, STATE_UNMOUNTED);
                        Log.d(TAG, "volume_state: " + state);
                        if (state == STATE_MOUNTED) {
                            usbPath = getExternalPathByGetVolumes();
                            Log.e(TAG, "------>U盘路径：" + usbPath);
                            if (diskListenerList.size() > 0) {
                                for (int i = 0; i < diskListenerList.size(); i++) {
                                    if (null != diskListenerList.get(i)) {
                                        diskListenerList.get(i).onConnect();
                                    }
                                }
                            }
                        }
                        break;
                    case UsbManager.ACTION_USB_DEVICE_DETACHED:
                        usbPath = "";
                        if (diskListenerList.size() > 0) {
                            for (int i = 0; i < diskListenerList.size(); i++) {
                                if (null != diskListenerList.get(i)) {
                                    diskListenerList.get(i).onDisconnect();
                                }
                            }
                        }
                        break;
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
        if (!TextUtils.isEmpty(path)) {
            int lastSeparator = path.lastIndexOf(File.separator);
            String endStr = path.substring(lastSeparator + 1, path.length());
            if (!TextUtils.isEmpty(endStr) && (endStr.contains("USB_DISK") || endStr.contains("usb_disk"))) {//不区分大小写
                File file = new File(path);
                if (file.exists() && file.listFiles().length == 1 && file.listFiles()[0].isDirectory()) {
                    path = file.listFiles()[0].getAbsolutePath();
                }
            }
        }
        return path;
    }


    /**
     * 根据StorageManager可以获取内部存储、sd卡以及所有usb路径
     * 注：getVolumeList方法获取不到U盘路径
     */
    private String getExternalPathByGetVolumeList() {
        String path = "";
        try {
            StorageManager storageManager = (StorageManager) this.application.getSystemService(Context.STORAGE_SERVICE);
            Method method_volumeList = StorageManager.class.getMethod("getVolumeList");
            method_volumeList.setAccessible(true);
            Object volumeList = method_volumeList.invoke(storageManager);
            if (volumeList != null) {
                Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
                final int length = Array.getLength(volumeList);
                for (int i = 0; i < length; i++) {
                    Object storageVolumeElement = Array.get(volumeList, i);
                    path = (String) storageVolumeClazz.getMethod("getPath").invoke(storageVolumeElement);
                    boolean isRemovable = (boolean) storageVolumeClazz.getMethod("isRemovable").invoke(storageVolumeElement);
                    String state = (String) storageVolumeClazz.getMethod("getState").invoke(storageVolumeElement);
                    String description = (String) storageVolumeClazz.getMethod("getDescription", Context.class).invoke(storageVolumeElement, application);
                    Log.e(TAG, "isRemovable: " + isRemovable + ", state: " + state + ", path: " + path + ", description: " + description);
                    if (isRemovable && "mounted".equalsIgnoreCase(state)) {
                        return path;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return path;
    }

    /**
     * 根据StorageManager获取Usb插入的U盘路径
     * 可以获取内部存储、sd卡以及所有usb路径
     * 获取到的路径可能是不完整的，需要判断追加
     */
    @SuppressLint("PrivateApi")
    private String getExternalPathByGetVolumes() {
        String path = "";
        try {
            StorageManager storageManager = (StorageManager) this.application.getSystemService(Context.STORAGE_SERVICE);
            Method method_volumeList = storageManager.getClass().getMethod("getVolumes");
            method_volumeList.setAccessible(true);
            List<?> volumeList = (List<?>) method_volumeList.invoke(storageManager);
            if (volumeList != null) {
                Class<?> volumeInfoClazz = Class.forName("android.os.storage.VolumeInfo");
                for (int i = 0; i < volumeList.size(); i++) {
                    Object VolumeInfoElement = volumeList.get(i);
                    File file = (File) volumeInfoClazz.getMethod("getPath").invoke(VolumeInfoElement);
                    if (file != null) {
                        path = file.getAbsolutePath();
                        int state = (int) volumeInfoClazz.getMethod("getState").invoke(VolumeInfoElement);
                        int type = (int) volumeInfoClazz.getMethod("getType").invoke(VolumeInfoElement);
                        String description = (String) volumeInfoClazz.getMethod("getDescription").invoke(VolumeInfoElement);
                        Log.e(TAG, "state: " + state + ", type: " + type + ", path: " + path + ", description: " + description);
                        if (type == TYPE_PUBLIC) {
                            return path;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return path;
    }

    private void sendMediaScannerBroadcast(Context context, String path) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_MOUNTED);
        Uri uri = Uri.fromFile(new File(path));
//        Uri uri = FileProvider.getUriForFile(new File(path));
        intent.setData(uri);
        context.sendBroadcast(intent);
    }

    /**
     * U盘连接状态回调
     */
    public interface IUDiskListener {
        void onConnect();

        void onDisconnect();
    }

    private List<IUDiskListener> diskListenerList = new ArrayList<>();

    public void setUDiskListener(IUDiskListener uDiskListener) {
        diskListenerList.add(uDiskListener);
    }

    public void removeUDiskListener(IUDiskListener uDiskListener) {
        diskListenerList.remove(uDiskListener);
    }
}
