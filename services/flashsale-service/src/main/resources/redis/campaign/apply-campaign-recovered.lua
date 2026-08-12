-- Apply a complete recovery snapshot with a monotonic version guard.
local current_version = redis.call('HGET', KEYS[1], 'aggregateVersion')
local current_state = redis.call('HGET', KEYS[1], 'state')
if current_version ~= false then
    if tonumber(ARGV[2]) < tonumber(current_version) then
        return {'NOOP_STALE'}
    end
    if tonumber(ARGV[2]) == tonumber(current_version) and current_state ~= 'RECOVERY_REQUIRED' then
        return {'NOOP_STALE'}
    end
end

redis.call('HSET', KEYS[1],
    'campaignId', ARGV[1],
    'aggregateVersion', ARGV[2],
    'state', ARGV[3],
    'startsAt', ARGV[4],
    'endsAt', ARGV[5],
    'updatedAt', ARGV[6],
    'recoveryRequired', 'false')
redis.call('HSET', KEYS[3],
    'variantId', ARGV[7],
    'inventoryAllocationId', ARGV[8],
    'skuSnapshot', ARGV[9],
    'saleUnitPrice', ARGV[10],
    'currency', ARGV[11],
    'allocatedQuantity', ARGV[12],
    'perUserLimit', ARGV[13])

-- Never reset a usable counter during recovery; initialize it only after loss or a marker.
local existing_remaining = redis.call('HGET', KEYS[2], ARGV[7])
if existing_remaining == false then
    redis.call('HSET', KEYS[2], ARGV[7], ARGV[12])
end
redis.call('ZREM', KEYS[4], ARGV[1])
return {'APPLIED'}
