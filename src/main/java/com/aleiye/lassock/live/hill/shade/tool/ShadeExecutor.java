package com.aleiye.lassock.live.hill.shade.tool;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.aleiye.lassock.lifecycle.LifecycleState;

public class ShadeExecutor {
	// 周期性的任务执行的线程池
	private static ScheduledExecutorService service;

	private ShadeExecutor() {}

	private static LifecycleState lifecycleState;

	public static void start() {
		service = Executors.newScheduledThreadPool(12);
		lifecycleState = LifecycleState.START;
	}

	public static void shutdown() {
		service.shutdown();
		lifecycleState = LifecycleState.STOP;
	}

	public static boolean isStarted() {
		return lifecycleState == LifecycleState.START;
	}

	public static ScheduledExecutorService getService() {
		return service;
	}
}
