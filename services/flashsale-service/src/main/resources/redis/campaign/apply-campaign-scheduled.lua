-- Compare-and-apply a complete scheduled snapshot atomically.
local current_version = redis.call('HGET', KEYS[1], 'aggregateVersion')
if current_version ~= false and tonumber(ARGV[2]) <= tonumber(current_version) then
    return {'NOOP_STALE'}
end

redis.call('HSET', KEYS[1],
    'campaignId', ARGV[1],
    'aggregateVersion', ARGV[2],
    'state', 'SCHEDULED',
    'startsAt', ARGV[3],
    'endsAt', ARGV[4],
    'updatedAt', ARGV[5],
    'recoveryRequired', 'false')
redis.call('HSET', KEYS[2], ARGV[6], ARGV[11])
redis.call('HSET', KEYS[3],
    'variantId', ARGV[6],
    'inventoryAllocationId', ARGV[7],
    'skuSnapshot', ARGV[8],
    'saleUnitPrice', ARGV[9],
    'currency', ARGV[10],
    'allocatedQuantity', ARGV[11],
    'perUserLimit', ARGV[12])
redis.call('ZREM', KEYS[4], ARGV[1])
return {'APPLIED'}
