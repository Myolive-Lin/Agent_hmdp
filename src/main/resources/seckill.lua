local vourcherId = ARGV[1]
local userId = ARGV[2]

local stockKey = 'seckill:stock:' ..vourcherId
local orderKey = 'seckill:order:' ..vourcherId

local stock = redis.call("GET", stockKey)

if (not stock ) or (tonumber(stock) <= 0 ) then
    return 1
end


if redis.call("SISMEMBER", orderKey, userId) == 1 then
    return 2
end

redis.call("DECR", stockKey)
redis.call("SADD", orderKey, userId)

return 0