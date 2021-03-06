/*
 * Copyright 2014 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.shazam.fork.system.adb;

import com.android.ddmlib.AndroidDebugBridge;
import org.apache.commons.io.FileUtils;

import java.io.File;

/**
 * Utilities for executing instrumentation tests on devices.
 */
public final class SpoonUtils {
    private SpoonUtils() {
        // No instances.
    }

    /**
     * Get an {@link com.android.ddmlib.AndroidDebugBridge} instance given an SDK path.
     * @param sdk the path to the SDK
     * @return the bridge instance
     */
    public static AndroidDebugBridge initAdb(File sdk) {
        AndroidDebugBridge.init(false);
        File adbPath = FileUtils.getFile(sdk, "platform-tools", "adb");
        AndroidDebugBridge adb = AndroidDebugBridge.createBridge(adbPath.getAbsolutePath(), true);
        waitForAdb(adb);
        return adb;
    }

    private static void waitForAdb(AndroidDebugBridge adb) {
        for (int i = 1; i < 10; i++) {
            try {
                Thread.sleep(i * 100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (adb.isConnected()) {
                return;
            }
        }
        throw new RuntimeException("Unable to connect to adb.");
    }
}
