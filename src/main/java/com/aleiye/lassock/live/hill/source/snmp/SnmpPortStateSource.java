package com.aleiye.lassock.live.hill.source.snmp;

import com.aleiye.lassock.api.Course;
import com.aleiye.lassock.api.SnmpPortStatisticalIndicators;
import com.aleiye.lassock.live.model.Mushroom;
import com.aleiye.lassock.live.model.MushroomBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by root on 2016/5/3.
 */
public class SnmpPortStateSource extends SnmpStandardSource{
    private static final Logger LOGGER = LoggerFactory.getLogger(SnmpPortStateSource.class);

    //端口名称
    private String preNameOid;
    private String sufNameOid;

    //端口ip
    private String prefixOid;
    private String suffixOid;

    //配置状态
    private String preConStateOid;
    private String sufConStateOid;

    //当前状态
    private String preCurStateOid;
    private String sufCurStateOid;

    /**
     * 端口统计信息
     * @param snmp
     * @param target
     * @throws Exception
     */
    private void portState(Snmp snmp, Target target) throws Exception {

        // 端口名称
        Map<String, String> portNamesMap = mapSwitch(query(snmp, target, preNameOid, sufNameOid));
        if (portNamesMap.size() == 0) {
            LOGGER.warn("No such instances|objects target:" + target.getAddress() + " type:" + PDU.GETBULK + " OID:"
                    + preNameOid + "-" + sufNameOid);
            return;
        }

        // 查找端口-IP映射
        Map<String, String> portIpMap = portIpMap(snmp, target);

        //端口配置状态
        Map<String, String> portConStateMap = mapSwitch(query(snmp, target, preConStateOid, sufConStateOid));

        //端口当前状态
        Map<String, String> portCutStateMap = mapSwitch(query(snmp, target, preCurStateOid, sufCurStateOid));


        Iterator<Map.Entry<String, String>> iterator = portNamesMap.entrySet().iterator();
        Long curTime = System.currentTimeMillis();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            Map<String, Object> map = new HashMap<String, Object>();
            String port = entry.getKey();
            map.put(SnmpPortStatisticalIndicators.DRIVER_IP.getName(), this.param.getHost());
            map.put(SnmpPortStatisticalIndicators.PORT_NAME.getName(),entry.getValue());
            map.put(SnmpPortStatisticalIndicators.PORT_IP.getName(), portIpMap.get(port));
            map.put(SnmpPortStatisticalIndicators.CON_STATE.getName(), portConStateMap.get(port));
            map.put(SnmpPortStatisticalIndicators.CUR_STATE.getName(), portCutStateMap.get(port));
            map.put(SnmpPortStatisticalIndicators.CURRENT_TIME.getName(), curTime);

            Mushroom generalMushroom = MushroomBuilder.withBody(map, null);
            generalMushroom.getHeaders().put("target", this.param.getHost());
            putMushroom(generalMushroom);
        }

    }

    /**
     * 获取port-ip映射
     *
     * @param snmp
     * @param target
     * @return
     * @throws Exception
     */
    private Map<String, String> portIpMap(Snmp snmp, Target target) throws Exception {
        Map<String, String> map = query(snmp, target, prefixOid, suffixOid);
        Map<String, String> result = new HashMap<String, String>(map.size());
        Iterator<Map.Entry<String, String>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            result.put(entry.getValue(), entry.getKey().replace(prefixOid + ".", ""));
        }
        return result;
    }


    @Override
    protected void doConfigure(Course context) throws Exception {
        super.doConfigure(context);
        preNameOid = param.getOids().get(0);
        sufNameOid = param.getOids().get(1);
        prefixOid = param.getOids().get(2);
        suffixOid = param.getOids().get(3);
        preConStateOid = param.getOids().get(4);
        sufConStateOid = param.getOids().get(5);
        preCurStateOid = param.getOids().get(6);
        sufCurStateOid = param.getOids().get(7);

    }

    @Override
    protected void doSend() throws Exception {
        portState(this.snmp, this.target);
    }
}
