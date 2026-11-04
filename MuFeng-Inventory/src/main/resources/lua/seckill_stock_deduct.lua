-- 1. 校验用户是否已参与过本次秒杀（一人一单防重）
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -2
end

-- 2. 校验当前秒杀划拨库存是否充足
local currentStock = tonumber(redis.call('GET', KEYS[1]))
local deductNum = tonumber(ARGV[2])

if (not currentStock) or (currentStock < deductNum) then
    return -1
end

-- 3. 原子扣减库存并记录已购用户
local remainStock = redis.call('DECRBY', KEYS[1], deductNum)
redis.call('SADD', KEYS[2], ARGV[1])

return remainStock