-- Activate only an existing compatible projection; never create quota from an activation fact.
local current_version = redis.call('HGET', KEYS[1], 'aggregateVersion')
local current_state = redis.call('HGET', KEYS[1], 'state')
if current_version ~= false and tonumber(ARGV[2]) <= tonumber(current_version) then
    return {'NOOP_STALE'}
end

if current_version == false or current_state == false
        or current_state == 'RECOVERY_REQUIRED'
        or (current_state ~= 'SCHEDULED' and current_state ~= 'ACTIVE') then
    redis.call('HSET', KEYS[1],
        'campaignId', ARGV[1],
        'aggregateVersion', ARGV[2],
        'state', 'RECOVERY_REQUIRED',
        'startsAt', ARGV[3],
        'endsAt', ARGV[4],
        'updatedAt', ARGV[5],
        'recoveryRequired', 'true')
    redis.call('ZADD', KEYS[2], ARGV[5], ARGV[1])
    return {'RECOVERY_REQUIRED'}
end

local stored_start = redis.call('HGET', KEYS[1], 'startsAt')
local stored_end = redis.call('HGET', KEYS[1], 'endsAt')
if stored_start ~= ARGV[3] or stored_end ~= ARGV[4] then
    redis.call('HSET', KEYS[1],
        'aggregateVersion', ARGV[2],
        'state', 'RECOVERY_REQUIRED',
        'startsAt', ARGV[3],
        'endsAt', ARGV[4],
        'updatedAt', ARGV[5],
        'recoveryRequired', 'true')
    redis.call('ZADD', KEYS[2], ARGV[5], ARGV[1])
    return {'RECOVERY_REQUIRED'}
end

redis.call('HSET', KEYS[1],
    'aggregateVersion', ARGV[2],
    'state', 'ACTIVE',
    'updatedAt', ARGV[5],
    'recoveryRequired', 'false')
redis.call('ZREM', KEYS[2], ARGV[1])
return {'APPLIED'}
