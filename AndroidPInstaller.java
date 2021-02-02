/**
 * @param filepathApk 文件路径
 */
private void installPackage(String filepathApk) {
	try {
		PackageInstaller pi = mContext.getPackageManager().getPackageInstaller();
		//给定模式，创建新的参数，创建新安装会话，返回唯一 Id
		int sessionId= pi.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));
		//打开现有会话，主动执行工作
		PackageInstaller.Session session = pi.openSession(sessionId);
		long sizeBytes = 0;
		final File file = new File(filepathApk);
		if (file.isFile()) {
			sizeBytes = file.length();
		}
		InputStream in = null;
		OutputStream out = null;
		in = new FileInputStream(filepathApk);
		//打开一个流，将一个APK文件写入会话
		//指定有效长度系统将预先分配底层磁盘空间以优化磁盘上的放置
		out = session.openWrite("app_store_session", 0, sizeBytes);
		int total = 0;
		byte[] buffer = new byte[65536];
		int len;
		while ((len= in.read(buffer)) != -1) {
			total += len;
			out.write(buffer, 0, len);
		}
		//根据需要调用，用来确保字节已保留到磁盘
		session.fsync(out);
		in.close();
		out.close();
		Log.d("InstallApkViaPackageInstaller - Success: streamed apk " + total + " bytes");
		PendingIntent broadCastTest = PendingIntent.getBroadcast(mContext,sessionId,
				new Intent(ACTION_INSTALL_COMPLETE),
				PendingIntent.FLAG_UPDATE_CURRENT);
		//提交之前必须关闭所有流
		session.commit(broadCastTest.getIntentSender());
		session.close();
	} catch (Exception ex) {
		ex.printStackTrace();
	}
}

/**
 * @param packageName 应用包名
 */
private void uninstallPackage(String packageName) {
	Intent intent = new Intent(mContext, mContext.getClass());
	PendingIntent sender = PendingIntent.getActivity(mContext, 0, intent, 0);
	PackageInstaller mPackageInstaller = mContext.getPackageManager().getPackageInstaller();
	mPackageInstaller.uninstall(packageName, sender.getIntentSender());
}
