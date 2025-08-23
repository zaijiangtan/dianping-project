-- 1.参数列表
-- 1.1.优惠卷id
local voucherId = ARGV[1]
-- 1.2.用户id
local userId = ARGV[2]

-- 2.数据key
-- 2.1.库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2.订单key
local orderKey = 'seckill:order:' .. voucherId

-- 3.脚本业务
-- 3.1.给库存+1
redis.call('incrby', stockKey, 1)
--3.2.删除用户下单记录
redis.call('srem', orderKey, userId)