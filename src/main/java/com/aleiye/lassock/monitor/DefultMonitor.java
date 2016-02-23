package com.aleiye.lassock.monitor;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import com.aleiye.lassock.api.Intelligence;
import com.aleiye.lassock.lang.Sistem;
import com.aleiye.lassock.live.Live;
import com.aleiye.lassock.live.NamedLifecycle;
import com.aleiye.lassock.live.conf.Context;
import com.aleiye.lassock.util.AkkaUtils;

/**
 * 默认监听
 * 
 * @author ruibing.zhao
 * @since 2016年2月22日
 * @version 1.0
 */
public class DefultMonitor extends NamedLifecycle implements Monitor {
	private boolean enabled = false;
	private String host;
	private int port;
	private String sysName;
	private static final int period = 10000;

	private Live live;
	ActorSystem actorSystem;

	ActorRef greeter;
	//
	Context target;
	// 目标
	ActorSelection selection;

	private Timer timer;

	public DefultMonitor(Live live) {
		this.live = live;
	}

	@Override
	public void configure(Context context) {
		enabled = context.getBoolean("enabled", false);
		host = context.getString("ip", Sistem.getIp());
		port = context.getInteger("port", 9981);
		sysName = context.getString("sysname", "lassock");
		target = new Context(context.getSubProperties("target."));
		timer = new Timer("timer-monitor");
	}

	@Override
	public synchronized void start() {
		// 情报发送
		if (enabled) {
			// 开启AKKA
			actorSystem = AkkaUtils.createActorSystem(host, port, sysName);
			// status 状态服务
			greeter = actorSystem.actorOf(Props.create(StatusActor.class, live), "getStatus");

			if (target.getBoolean("enabled")) {
				String targetHost = target.getString("host");
				int targetPort = target.getInteger("port");
				String targetName = target.getString("sysname");
				String targetActorName = target.getString("actorname");
				selection = actorSystem.actorSelection(AkkaUtils.getRemoteActorPath(targetHost, targetPort, targetName,
						targetActorName));

				TimerTask tt = new TimerTask() {
					@Override
					public void run() {
						List<Intelligence> is = live.getIntelligences();
						selection.tell(is, ActorRef.noSender());
					}
				};
				timer.schedule(tt, 3000, period);
			}
		}
		super.start();
	}

	@Override
	public synchronized void stop() {
		greeter = null;
		actorSystem.shutdown();
		super.stop();
	}

	/**
	 * 状态获了Actor
	 * 
	 * @author ruibing.zhao
	 * @since 2016年2月22日
	 * @version 1.0
	 */
	public static class StatusActor extends UntypedActor {
		LoggingAdapter log = Logging.getLogger(getContext().system(), this);
		Live live;

		public StatusActor(Live live) {
			this.live = live;
		}

		public void onReceive(Object message) throws Exception {
			getSender().tell(Boolean.valueOf(!live.isPaused()), getSender());
		}
	}
}
