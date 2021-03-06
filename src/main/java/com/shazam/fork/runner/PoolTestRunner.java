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
package com.shazam.fork.runner;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.shazam.fork.Configuration;
import com.shazam.fork.model.*;
import com.shazam.fork.listeners.ForkXmlTestRunListener;
import com.shazam.fork.system.io.FileManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import static com.shazam.fork.listeners.TestRunListenersFactory.getForkXmlTestRunListener;
import static com.shazam.fork.Utils.namedExecutor;

public class PoolTestRunner implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(PoolTestRunner.class);
    public static final String DROPPED_BY = "DroppedBy-";

    private final Configuration configuration;
    private final FileManager fileManager;
    private final Pool pool;
    private final LinkedList<TestClass> testClasses;
    private final CountDownLatch poolCountDownLatch;
    private final DeviceTestRunnerFactory deviceTestRunnerFactory;

    public PoolTestRunner(Configuration configuration,
                          FileManager fileManager,
                          DeviceTestRunnerFactory deviceTestRunnerFactory, Pool pool,
                          LinkedList<TestClass> testClasses,
                          CountDownLatch poolCountDownLatch) {
        this.configuration = configuration;
        this.fileManager = fileManager;
        this.pool = pool;
        this.testClasses = testClasses;
        this.poolCountDownLatch = poolCountDownLatch;
        this.deviceTestRunnerFactory = deviceTestRunnerFactory;
    }

    public void run() {
        ExecutorService concurrentDeviceExecutor = null;
        try {
            int devicesInPool = pool.size();
            concurrentDeviceExecutor = namedExecutor(devicesInPool, "DeviceExecutor-%d");
            CountDownLatch deviceCountDownLatch = new CountDownLatch(devicesInPool);
            for (Device device : pool.getDevices()) {
                Runnable deviceTestRunner = deviceTestRunnerFactory.createDeviceTestRunner(pool, testClasses,
                        deviceCountDownLatch, device);
                concurrentDeviceExecutor.execute(deviceTestRunner);
            }
            deviceCountDownLatch.await();
        } catch (InterruptedException e) {
            logger.warn("Pool {} was interrupted while running", pool.getName());
        } finally {
            failAnyDroppedClasses(pool, testClasses);
            if (concurrentDeviceExecutor != null) {
                concurrentDeviceExecutor.shutdown();
            }
            logger.info("Pool {} finished", pool.getName());
            poolCountDownLatch.countDown();
            logger.info("Pools remaining: {}", poolCountDownLatch.getCount());
        }
    }

    /**
     * Only generate XML files for dropped classes the console listener and logcat listeners aren't relevant to
     * dropped tests.
     * <p/>
     * In particular, not triggering the console listener will probably make the flaky report better.
     */
    private void failAnyDroppedClasses(Pool pool, Queue<TestClass> testClassQueue) {
        HashMap<String, String> emptyHash = new HashMap<>();
        TestClass nextTest;
        while ((nextTest = testClassQueue.poll()) != null) {
            String className = nextTest.getName();
            String poolName = pool.getName();
            ForkXmlTestRunListener xmlGenerator = getForkXmlTestRunListener(fileManager, configuration.getOutput(),
                    poolName, DROPPED_BY + poolName, nextTest);

            Collection<TestMethod> methods = nextTest.getUnignoredMethods();
            xmlGenerator.testRunStarted(poolName, methods.size());
            for (TestMethod method : methods) {
                String methodName = method.getName();
                TestIdentifier identifier = new TestIdentifier(className, methodName);
                xmlGenerator.testStarted(identifier);
                xmlGenerator.testFailed(identifier, poolName + " DROPPED");
                xmlGenerator.testEnded(identifier, emptyHash);
            }
            xmlGenerator.testRunFailed("DROPPED BY " + poolName);
            xmlGenerator.testRunEnded(0, emptyHash);
        }
    }
}
