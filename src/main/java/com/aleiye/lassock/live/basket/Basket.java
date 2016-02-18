package com.aleiye.lassock.live.basket;

import com.aleiye.lassock.common.NamedComponent;
import com.aleiye.lassock.lifecycle.LifecycleAware;
import com.aleiye.lassock.model.Mushroom;

/**
 * 采集缓存对列(竹篮)
 * 
 * @author ruibing.zhao
 * @since 2015年5月22日
 * @version 2.1.2
 */
public interface Basket extends LifecycleAware, NamedComponent {

	/**
	 * 放入
	 * 
	 * @param generalMushroom
	 * @throws InterruptedException
	 */
	void push(Mushroom generalMushroom) throws InterruptedException;

	/**
	 * 取出
	 * 
	 * @return
	 */
	Mushroom take() throws InterruptedException;
}
