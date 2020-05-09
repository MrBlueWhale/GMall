package com.xatu.gmall.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.xatu.gmall.entity.OmsOrder;
import com.xatu.gmall.entity.OmsOrderItem;
import com.xatu.gmall.mapper.OrderItemMapper;
import com.xatu.gmall.mapper.OrderMapper;
import com.xatu.gmall.service.CartService;
import com.xatu.gmall.service.OrderService;
import com.xatu.gmall.service.SkuService;
import com.xatu.gmall.util.RedisUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService
{

    @Autowired
    RedisUtil redisUtil;
    @Autowired
    OrderMapper orderMapper;
    @Autowired
    OrderItemMapper orderItemMapper;
    @Reference
    CartService cartService;

    @Override
    public String genTradeCode(String memberId) {
        //根据memberId生成交易码
        Jedis jedis = redisUtil.getJedis();

        String tradeKey = "user:"+memberId+":tradeCode";
        String tradeCode = UUID.randomUUID().toString();

        jedis.setex(tradeKey,60*15,tradeCode);
        jedis.close();
        return tradeCode;
    }

    @Override
    public String checkTradeCode(String memberId,String tradeCode) {
        //根据memberId生成交易码
        Jedis jedis = null;
        try{
            jedis = redisUtil.getJedis();
            String tradeKey = "user:"+memberId+":tradeCode";
            String tradeCodeFromCache = jedis.get(tradeKey);//使用lua脚本在发现key的同时将key删除，防止并发订单攻击
            //对比防重删令牌
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            Long eval = (Long) jedis.eval(script, Collections.singletonList(tradeKey),
                    Collections.singletonList(tradeCode));
/*
            if(StringUtils.isNotBlank(tradeCodeFromCache)&&tradeCode.equals(tradeCodeFromCache)){
                jedis.del(tradeKey);
                return "success";
            }else{
                return "fail";
            }*/
            if(eval!=null&&eval!=0){
                return "success";
             }else{
                 return "fail";
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            jedis.close();
        }
        return "fail";
    }

    @Override
    public void saveOrder(OmsOrder omsOrder) {
        //保存订单表
        orderMapper.insert(omsOrder);
        List<OmsOrderItem> omsOrderItems = omsOrder.getOmsOrderItems();
        //保存订单详情
        for (OmsOrderItem omsOrderItem : omsOrderItems) {
            orderItemMapper.insert(omsOrderItem);
            //删除订单项在购物车中的对应项
           /* cartService.delCart(omsOrderItem.getProductSkuId(),omsOrder.getMemberId());*/
        }


    }


}
