package com.aleiye.lassock.live.hill.source.text.cluser;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aleiye.common.exception.AuthWrongException;
import com.aleiye.lassock.live.exception.SignRemovedException;
import com.aleiye.lassock.live.hill.source.text.CluserSign;
import com.aleiye.lassock.live.hill.source.text.PickPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 事件驱动
 *
 * @author ruibing.zhao
 * @version 2.1.2
 * @since 2015年5月18日
 */
public abstract class TextCluser implements Cluser, Runnable {

    private static final Logger _LOG = LoggerFactory.getLogger(TextCluser.class);

    protected final AtomicBoolean done = new AtomicBoolean(false);
    protected final CluserSign sign;
    protected CluserListener listener;

    // 等待执行队列(单元对应Shade)
    private final BlockingQueue<TextCluser> normals;
    // 发生错误的Shade
    private final BlockingQueue<TextCluser> errors;
    // 应急备用通道(用于在关闭时保存唤醒阻塞无处存放的Shade)
    private final List<TextCluser> emergency;

    public TextCluser(CluserSign sign, BlockingQueue<TextCluser> normals, BlockingQueue<TextCluser> errors,
                      List<TextCluser> emergency) {
        this.sign = sign;
        this.normals = normals;
        this.errors = errors;
        this.emergency = emergency;
    }

    // 状态
    private CluserState state = CluserState.NORMAL;
    // 结束状态保持次数
    private int endConut = 0;
    // 异常状态保持次数
    private int errConut = 0;

    // 采集策略 默认为行采集策略
    protected PickPolicy pickPolic = new LinesPickPolicy();

    @Override
    public void run() {
        try {
            this.pickPolic.pick(this);
        } catch (IOException e) {
            this.setState(CluserState.ERR);
        } catch (InterruptedException e) {
            ;
        } catch (SignRemovedException e) {
            this.setState(CluserState.ERR);
        } catch (AuthWrongException e) {
            this.setState(CluserState.ERR);
        } catch (Exception e) {
            _LOG.error("pick error", e);
        }
    }

    // 移动偏移
    public void seek(long offset) {
        ;
    }

    // 是否可采集信息
    public abstract boolean canPick();

    // 采集信息
    public abstract CluserState selfCheck();

    public int getEndConut() {
        return endConut;
    }

    public int getErrConut() {
        return errConut;
    }

    @Override
    public boolean isOpen() {
        return this.done.get();
    }

    // 状态设置 如果当前设置状态是保持状态,状态自增
    @Override
    public void setState(CluserState state) {
        this.state = state;
        switch (state) {
            case END:
                errConut = 0;
                endConut++;
                break;
            case ERR:
                errConut++;
                endConut = 0;
                break;

            default:
                errConut = 0;
                endConut = 0;
                break;
        }
    }

    @Override
    public CluserState getState() {
        return state;
    }

    @Override
    public void setListener(CluserListener listener) {
        this.listener = listener;
    }

    @Override
    public void returnQueue() {
        try {
            if (getState() == CluserState.ERR) {
                errors.put(this);
            } else {
                normals.put(this);
            }
        } catch (InterruptedException e) {
            emergency.add(this);
        }
    }

    @Override
    public void open() throws IOException {
        if (done.compareAndSet(false, true)) {
            doOpen();
        } else {
            throw new IllegalStateException("Cluser was opend!");
        }
    }

    @Override
    public void close() throws IOException {
        if (done.compareAndSet(true, false))
            doClose();
        else
            throw new IllegalStateException("Cluser is not opend!");
    }

    protected abstract void doOpen() throws IOException;

    protected abstract void doClose();

    /**
     * 块数采集策略
     *
     * @author ruibing.zhao
     * @version 2.1.2
     * @since 2015年5月25日
     */
    public static class LinesPickPolicy implements PickPolicy {

        public void pick(TextCluser shade) throws IOException, SignRemovedException, InterruptedException, AuthWrongException {
            if (shade.canPick()) {
                boolean hasNext = shade.next();
                //如果有数据的话,则读取10000条,之后在提交线程
                int limit = 10000;
                while (hasNext && limit > 0) {
                    hasNext = shade.next();
                    limit--;
                }
                if (!hasNext) {
                    Thread.sleep(100);
                }
            } else {
                Thread.sleep(100);
            }
        }
    }

    public CluserSign getSign() {
        return sign;
    }
}
