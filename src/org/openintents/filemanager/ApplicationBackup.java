/* 
 * Copyright (C) 2008 OpenIntents.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Based on AndDev.org's file browser V 2.0.
 */

package org.openintents.cmfilemanager;

import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.LayoutInflater;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * This class is used to display an activity to the user so they can
 * view the third party applications on their phone and have a button
 * that gives them the ability to backup said applications to the SDCard
 * <br>
 * <p>
 * The location that the backup will be placed is at 
 * <br>/sdcard/AppBackup/
 * <br>
 * note: that /sdcard/filemanager_temp/ should already exists. This is check at start
 * up from the class.
 * 
 * @author Joe Berria <nexesdevelopment@gmail.com>
 */


public class ApplicationBackup extends ListActivity implements OnClickListener {
	private static final String BACKUP_LOC = "/sdcard/AppBackup/";
	private static final int SET_PROGRESS = 0x00;
	private static final int FINISH_PROGRESS = 0x01;
	private static final int FLAG_UPDATED_SYS_APP = 0x80;
	
	private ArrayList<ApplicationInfo> mAppList;
	private TextView mAppLabel;
	private PackageManager mPackMag;
	private ProgressDialog mDialog;
	
	/*
	 * Our handler object that will update the GUI from 
	 * our background thread. 
	 */
	private Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			
			switch(msg.what) {
				case SET_PROGRESS:
					mDialog.setMessage((String)msg.obj);
					break;
				case FINISH_PROGRESS:
					mDialog.cancel();
					Toast.makeText(ApplicationBackup.this, 
								   "Applications have been backed up", 
								   Toast.LENGTH_SHORT).show();
					break;
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.backup_layout);
				
		mAppLabel = (TextView)findViewById(R.id.backup_label);
		Button button = (Button)findViewById(R.id.backup_button_all);
		
		button.setOnClickListener(this);
		
		mAppList = new ArrayList<ApplicationInfo>();
		mPackMag = getPackageManager();
		
		get_downloaded_apps();
		setListAdapter(new TableView());
	}
	
	
	@Override
	public void onClick(View view) {
		mDialog = ProgressDialog.show(ApplicationBackup.this, 
				  					  "Backing up applications",
				  					  "", true, false);
		
		Thread all = new Thread(new BackgroundWork(mAppList));
		all.start();
	}
	
	private void get_downloaded_apps() {
		List<ApplicationInfo> all_apps = mPackMag.getInstalledApplications(
											PackageManager.GET_UNINSTALLED_PACKAGES);
		
		for(ApplicationInfo appInfo : all_apps) {
			if((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && 
			   (appInfo.flags & FLAG_UPDATED_SYS_APP) == 0 && 
			   appInfo.flags != 0)
				
				mAppList.add(appInfo);
		}
		
		mAppLabel.setText("You have " +mAppList.size() + " downloaded apps");
	}


	/*
	 * This private inner class will perform the backup of applications
	 * on a background thread, while updating the user via a message being
	 * sent to our handler object.
	 */
	private class BackgroundWork implements Runnable {
		private static final int BUFFER = 256;
		
		private ArrayList<ApplicationInfo> mDataSource;
		private File mDir = new File(BACKUP_LOC);
		private byte[] mData;
		
		public BackgroundWork(ArrayList<ApplicationInfo> data)  {
			mDataSource = data;
			mData =  new byte[BUFFER];

			/*create dir if needed*/
			File d = new File("/sdcard/filemanager_temp/");
			if(!d.exists()) {
				d.mkdir();
				
				//then create this directory
				mDir.mkdir();
				
			} else {
				if(!mDir.exists())
					mDir.mkdir();
			}
		}

		public void run() {
			BufferedInputStream mBuffIn;
			BufferedOutputStream mBuffOut;
			Message msg;
			int len = mDataSource.size();
			int read = 0;
			
			for(int i = 0; i < len; i++) {
				ApplicationInfo info = mDataSource.get(i);
				String source_dir = info.sourceDir;
				String out_file = source_dir.substring(source_dir.lastIndexOf("/") + 1, source_dir.length());

				try {
					mBuffIn = new BufferedInputStream(new FileInputStream(source_dir));
					mBuffOut = new BufferedOutputStream(new FileOutputStream(BACKUP_LOC + out_file));
					
					while((read = mBuffIn.read(mData, 0, BUFFER)) != -1)
						mBuffOut.write(mData, 0, read);
					
					mBuffOut.flush();
					mBuffIn.close();
					mBuffOut.close();
					
					msg = new Message();
					msg.what = SET_PROGRESS;
					msg.obj = i + " out of " + len + " apps backed up";
					mHandler.sendMessage(msg);
					
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			mHandler.sendEmptyMessage(FINISH_PROGRESS);
		}
	}
	
	private static class AppViewHolder {
		TextView top_view;
		TextView bottom_view;
		ImageView icon;
		ImageView check_mark;
	}
	
	private class TableView extends ArrayAdapter<ApplicationInfo> {
		
		private TableView() {
			super(ApplicationBackup.this, R.layout.tablerow, mAppList);
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			AppViewHolder holder;
			ApplicationInfo info = mAppList.get(position);
			
			if(convertView == null) {
				LayoutInflater inflater = getLayoutInflater();
				convertView = inflater.inflate(R.layout.tablerow, parent, false);
				
				holder = new AppViewHolder();
				holder.top_view = (TextView)convertView.findViewById(R.id.top_view);
				holder.bottom_view = (TextView)convertView.findViewById(R.id.bottom_view);
				holder.check_mark = (ImageView)convertView.findViewById(R.id.multiselect_icon);
				holder.icon = (ImageView)convertView.findViewById(R.id.row_image);
				holder.icon.setMaxHeight(40);
				convertView.setTag(holder);
				
			} else {
				holder = (AppViewHolder) convertView.getTag();
			}
			
			holder.top_view.setText(info.processName);
			holder.bottom_view.setText(info.packageName);
			
			//this should not throw the exception
			try {
				holder.icon.setImageDrawable(mPackMag.getApplicationIcon(info.packageName));
			} catch (NameNotFoundException e) {
				holder.icon.setImageResource(R.drawable.ic_launcher_android_package);
			}
			
			return convertView;
		}
	}
}
