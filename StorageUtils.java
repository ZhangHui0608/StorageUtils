package com.backaudio.updatelib.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.storage.StorageManager;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Created by ZhangHui on 2020/8/24.
 */
public class StorageUtils {

    private static final String TAG = "StorageUtils";

    /**
     * 通过反射调用获取内置存储和外置sd卡根路径(通用)
     *
     * @param removable 是否可移除，false返回内部存储，true返回外置sd卡
     */
    public static String getSdCardPath(Context context, boolean removable) {
        if (context == null) {
            return "";
        }
        StorageManager mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        Class<?> storageVolumeClazz;
        try {
            storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isRemovable = storageVolumeClazz.getMethod("isRemovable");
            Object result = getVolumeList.invoke(mStorageManager);
            if (result != null) {
                final int length = Array.getLength(result);
                for (int i = 0; i < length; i++) {
                    Object storageVolumeElement = Array.get(result, i);
                    String path = (String) getPath.invoke(storageVolumeElement);
                    Object is_removable = isRemovable.invoke(storageVolumeElement);
                    if (is_removable != null && removable == (Boolean) is_removable) {
                        return path;
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 获取U盘路径
     *
     */
    @SuppressLint("PrivateApi")
    public static String getUPanPath(Context context) {
        String path = "";
        try {
            StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
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
                        String description = (String) volumeInfoClazz.getMethod("getDescription").invoke(VolumeInfoElement);
                        Log.e(TAG, "state: " + state + ", path: " + path + ", description: " + description);
                        if (path.contains("/mnt/media_rw/")) {
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
}
