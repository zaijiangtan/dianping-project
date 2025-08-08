-- 获取锁中的标识，并判断与当前线程标识是否一致
if (redis.call('GET', KEYS[1]) == ARGV[1]) then
    -- 一致，则删除锁
    return redis.call('DEL', KEYS[1])
end
-- 不一致，则直接返回
return 0

-- Redisson 加锁lua脚本
--[[
if ((redis.call('exists', KEYS[1]) == 0) or (redis.call('hexists', KEYS[1], ARGV[2]) == 1)) then
    redis.call('hincrby', KEYS[1], ARGV[2], 1);
    redis.call('pexpire', KEYS[1], ARGV[1]);
    return nil;
end ;
return redis.call('pttl', KEYS[1]);
]]

-- Redisson 解锁lua脚本
--[[
KEYS[1]：锁的 Redis 键（如 mylock）
KEYS[2]：用于发布释放信号的 channel（通常是 {lockKey}:channel）
ARGV[1]：发布内容，一般是线程 ID
ARGV[2]：锁剩余的续期时间（用于重设过期）
ARGV[3]：当前线程 ID（UUID + threadId）
ARGV[4]：发布命令，通常为 "publish"，即调用 redis.call("publish", channel, message)
]]
--[[
if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then
    return nil;
end ;
local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1);
if (counter > 0) then
    redis.call('pexpire', KEYS[1], ARGV[2]);
    return 0;
else
    redis.call('del', KEYS[1]);
    redis.call(ARGV[4], KEYS[2], ARGV[1]);
    return 1;
end ;
return nil;
]]
