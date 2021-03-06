package com.aleiye.lassock.live.hill.source.text;

import java.io.IOException;

import com.aleiye.common.exception.AuthWrongException;
import com.aleiye.lassock.live.exception.SignRemovedException;
import com.aleiye.lassock.live.hill.source.text.cluser.TextCluser;

/**
 * 采集策略接口
 * 
 * @author ruibing.zhao
 * @since 2015年5月25日
 * @version 2.1.2
 */
public interface PickPolicy {
	void pick(TextCluser shade) throws IOException, SignRemovedException, InterruptedException, AuthWrongException;
}
