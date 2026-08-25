-- Applies the durable PostgreSQL confirmation to Redis without changing quota.
-- Repeating the same command is a no-op; expiry processing must not restore quota
-- after the reservation has reached CONFIRMED.
local reservation = KEYS[1]
local expirations = KEYS[2]
local reservation_id = ARGV[1]

if redis.call('EXISTS', reservation) == 0 then
    return 'NOT_FOUND'
end

local status = redis.call('HGET', reservation, 'status')
if status == 'CONFIRMED' then
    redis.call('ZREM', expirations, reservation_id)
    return 'ALREADY_CONFIRMED'
end
if status ~= 'RESERVED' then
    return 'NOT_CONFIRMABLE'
end

redis.call('HSET', reservation, 'status', 'CONFIRMED', 'quotaReleased', 'false')
redis.call('ZREM', expirations, reservation_id)
return 'CONFIRMED'
