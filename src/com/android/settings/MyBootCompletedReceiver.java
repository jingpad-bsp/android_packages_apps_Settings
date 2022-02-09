/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.os.PowerManager;
import java.io.File;
import java.util.Objects;
import android.os.SystemProperties;
import android.os.Environment;
import android.os.EnvironmentEx;

/*
* Receive the anomaly info from {@link StatsManager}
*/
public class MyBootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "MyBootCompletedReceiver";


    @Override
    public void onReceive(Context context, Intent intent) {
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {

			//delete update.zip after upgrade, no matter success or fail.
		    PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		    Log.d(TAG, "isLastShutdownOTA : " + powerManager.isLastShutdownOTA());

		    if(powerManager.isLastShutdownOTA() == 1) {
		        new Thread() {
		            @Override
		            public void run() {
				        try {
						        //internal sdcard
						        File internalFile = new File("data/media/0/update.zip");
						        if (internalFile.exists()) {
						        	internalFile.delete();								
						        }

						        //external sdcard
						        String storageState="";
						        String storageDirectory="";
						        storageState = EnvironmentEx.getExternalStoragePathState();
						        storageDirectory = EnvironmentEx.getExternalStoragePath().getAbsolutePath();
						        
						        if (storageState.equals(Environment.MEDIA_MOUNTED)) {
						            File file = new File(storageDirectory + "/update.zip");
						            if (file.exists()) {
						            	file.delete();									
						            }
						        }

				        } catch(Exception e) {
				                e.printStackTrace();
			            }

		            }
		        }.start();
		    }
			
        }
    }
}
