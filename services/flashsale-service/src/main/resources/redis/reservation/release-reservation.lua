-- Durable release has already decided the PostgreSQL state. This script only reconciles
-- the Redis hot-path projection and is safe to repeat after a crash.
local reservation = KEYS[1]
local stock = KEYS[2]
local user_quantity = KEYS[3]
local expirations = KEYS[4]
local variant_id = ARGV[1]
local quantity = tonumber(ARGV[2])
local reservation_id = ARGV[3]
local target_status = ARGV[4]

if quantity == nil or quantity <= 0 then return 0 end
if redis.call('EXISTS', reservation) == 0 then redis.call('ZREM', expirations, reservation_id); return 0 end
if redis.call('HGET', reservation, 'status') == 'CONFIRMED' then return -1 end
if redis.call('HGET', reservation, 'quotaReleased') == 'true' then redis.call('ZREM', expirations, reservation_id); return 0 end

redis.call('HSET', reservation, 'quotaReleased', 'true', 'status', target_status)
redis.call('HINCRBY', stock, variant_id, quantity)
local held = tonumber(redis.call('HGET', user_quantity, variant_id)) or 0
local remaining = held - quantity
if remaining <= 0 then redis.call('HDEL', user_quantity, variant_id) else redis.call('HSET', user_quantity, variant_id, remaining) end
redis.call('ZREM', expirations, reservation_id)
return 1
