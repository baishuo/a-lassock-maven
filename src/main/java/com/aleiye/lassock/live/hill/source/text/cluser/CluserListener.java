package com.aleiye.lassock.live.hill.source.text.cluser;

import com.aleiye.common.exception.AuthWrongException;
import com.aleiye.lassock.live.model.Mushroom;

/**
 * 游标监听器
 * 
 * @author ruibing.zhao
 * @since 2016年4月15日
 * @version 1.0
 */
public interface CluserListener {
	void picked(Mushroom mushroom) throws InterruptedException, AuthWrongException;
}
