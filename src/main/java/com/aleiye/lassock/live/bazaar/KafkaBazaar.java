package com.aleiye.lassock.live.bazaar;

import com.aleiye.event.constants.EventKey;
import com.aleiye.event.exception.InvalidEventContentException;
import com.aleiye.event.factory.AleiyeEventFactory;
import com.aleiye.lassock.conf.Context;
import com.aleiye.lassock.lifecycle.LifecycleState;
import com.aleiye.lassock.live.model.Mushroom;
import com.aleiye.lassock.util.CloseableUtils;
import com.aleiye.lassock.util.ConfigUtils;
import com.aleiye.zkclient.standard.CuratorClient;
import com.aleiye.zkclient.standard.CuratorFactory;
import com.aleiye.zkpath.constants.ZKPathConstants;

import com.fasterxml.uuid.EthernetAddress;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedGenerator;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerClosedException;
import kafka.producer.ProducerConfig;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.RetryUntilElapsed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * KAFKA消费消息
 *
 * @author ruibing.zhao
 * @since 2015年10月19日
 */
public class KafkaBazaar extends AbstractBazaar {

    static Logger _LOG = LoggerFactory.getLogger(KafkaBazaar.class);

    public static final String ALEIYE_SYSTEM_KAFKA_PATH = ZKPathConstants.ALEIYE_SYSTEM_KAFKA_PATH;

    // 存储系统当前topic格式定义的path
    public static final String KAFKA_TOPIC_DEF_PATH = ZKPathConstants.KAFKA_TOPIC_DEF_PATH;

    public static final String KAFKA_BROKER_DEF_PATH = ZKPathConstants.KAFKA_BROKER_DEF_PATH;

    public static final String KAFKA_PARTITION_DEF_PATH = ZKPathConstants.KAFKA_PARTITION_DEF_PATH;

    private EthernetAddress addr = EthernetAddress.fromInterface();

    private TimeBasedGenerator uuidGenerator = Generators.timeBasedGenerator(addr);

    private boolean autoCreateId = ConfigUtils.getConfig().getBoolean("system.auto.create.id");

    private CuratorFramework client;

    CuratorClient curator;

    private PathChildrenCache pathChildrenCache;

    private Producer<String, byte[]> producer = null;

    private int messageCount = 0;

    private String topic = null;

    private ProducerConfig producerConfig;

    private int numPartition = 3;

    public KafkaBazaar() {
        super();
    }

    @Override
    public void configure(Context context) {
        String zkConnect = context.getString("zkhost");
        RetryUntilElapsed retryPolicy = new RetryUntilElapsed(3000, Integer.MAX_VALUE);
        client = CuratorFactory.createFramework(zkConnect, retryPolicy);
        // CuratorFrameworkFactory.Builder zkBuilder =
        // CuratorFrameworkFactory.builder().connectString(zkConnect)
        // .retryPolicy(retryPolicy);
        // client = zkBuilder.build();
        client.start();
        try {
            client.blockUntilConnected();
            curator = CuratorFactory.create(client);
        } catch (InterruptedException e) {
            this.lifecycleState = LifecycleState.ERROR;
            throw new IllegalArgumentException("Config for bazaar with name:" + this.getName());
        }
    }

    public void start() {
        try {
            if (!curator.exists(KAFKA_TOPIC_DEF_PATH)) {
                curator.createNode(KAFKA_TOPIC_DEF_PATH, "C_0".getBytes());
            }
            // 启动topic的监听
            pathChildrenCache = new PathChildrenCache(client, ALEIYE_SYSTEM_KAFKA_PATH, true);

            pathChildrenCache.getListenable().addListener(new PathChildrenCacheListener() {
                @SuppressWarnings("incomplete-switch")
                @Override
                public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
                    switch (event.getType()) {
                        case CHILD_ADDED:
                            changeConfig(event.getData().getPath(), event.getData().getData());
                            break;
                        case CHILD_UPDATED:
                            changeConfig(event.getData().getPath(), event.getData().getData());
                            break;
                    }
                }
            });
            pathChildrenCache.start();
            Thread.sleep(10 * 1000);
            createProducer();
            super.start();
        } catch (Exception e) {
            _LOG.error("get system kafka define error", e);
            System.exit(1);
        }
    }

    private void changeConfig(String path, byte[] data) {
        if (data == null || data.length <= 0) {
            return;
        }
        try {
            if ((path).equals(KAFKA_TOPIC_DEF_PATH)) {
                topic = new String(data, "UTF-8");
            } else if ((path).equals(KAFKA_BROKER_DEF_PATH)) {
                Properties p = ConfigUtils.getKafkaProp();
                p.setProperty("metadata.broker.list", new String(data, "UTF-8"));
                producerConfig = new ProducerConfig(p);
                createProducer();
            } else if ((path).equals(KAFKA_PARTITION_DEF_PATH)) {
                numPartition = Integer.parseInt(new String(data, "UTF-8"));
            }
        } catch (Exception e) {
            _LOG.error(" get topic error", e);
        }
    }

    public void createProducer() throws InterruptedException {
        if (producer != null) {
            producer.close();
        }
        while (producerConfig == null) {
            Thread.sleep(5 * 1000);
        }
        producer = new Producer<>(producerConfig);
    }

    @Override
    public void stop() {
        if (producer != null) {
            producer.close();
        }
        CloseableUtils.closeQuietly(pathChildrenCache);
        CloseableUtils.closeQuietly(client);
        super.stop();
    }

    @Override
    public void process() throws Exception {
        Mushroom mushroom = null;
        try {
            mushroom = this.getBasket().take();
            sendMassage(mushroom);
            messageCount++;
            if (messageCount >= numPartition) {
                messageCount = 0;
            }
            mushroom.incrementCompleteCount();
        } catch (InterruptedException e) {
            _LOG.error("get message error", e);
        } catch (ProducerClosedException e) {
            mushroom.incrementFailedCount();
            _LOG.error("kafka serve change", e);
            try {
                this.getBasket().push(mushroom);
            } catch (InterruptedException e1) {
                _LOG.error("put data into queue error", e1);
            }
        }

    }

    private void sendMassage(Mushroom mr) {

        AleiyeEventFactory.Builder eventBuilder = AleiyeEventFactory.builder();
        try {
            eventBuilder.setContent(mr.getBody());
            eventBuilder.addParam(mr.getHeaders());
            eventBuilder.addParam(EventKey.COLLECTTIME, String.valueOf(System.currentTimeMillis()));
            //增加唯一Id,防止传输重复
            if (autoCreateId) {
                eventBuilder.addParam(EventKey.ID, uuidGenerator.generate().toString());
            }
            KeyedMessage<String, byte[]> message = new KeyedMessage<String, byte[]>(topic,
                    String.valueOf((char) ('a' + messageCount)), eventBuilder.build());

            producer.send(message);
        } catch (InvalidEventContentException e) {
            _LOG.error("builder aleiyeEvent error", e);
        }
    }
}
