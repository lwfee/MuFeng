-- 1. 仅当用户确实在已购集合中时才执行回补（防重复回补导致超卖）
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    redis.call('SREM', KEYS[2], ARGV[1])
    if redis.call('EXISTS', KEYS[1]) == 1 then
        return redis.call('INCRBY', KEYS[1], tonumber(ARGV[2]))
    end
    return 1
end

return 0