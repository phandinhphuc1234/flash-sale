-- Atomically validates an active Campaign, reserves quota, stores replay state, and XADDs handoff.
local now = tonumber(ARGV[1])
local campaign_id = ARGV[2]
local variant_id = ARGV[3]
local user_id = ARGV[4]
local quantity = tonumber(ARGV[5])
local key_hash = ARGV[6]
local request_hash = ARGV[7]
local purchase_request_id = ARGV[8]
local reservation_id = ARGV[9]
local event_id = ARGV[10]
local accepted_at = ARGV[11]
local expires_at = ARGV[12]
local retained_until = ARGV[13]
local traceparent = ARGV[14]
local tracestate = ARGV[15]

if quantity == nil or quantity <= 0 then
    return {'VARIANT_NOT_ELIGIBLE'}
end

local existing_request_hash = redis.call('HGET', KEYS[5], 'requestHash')
if existing_request_hash ~= false then
    if existing_request_hash ~= request_hash then
        return {'IDEMPOTENCY_CONFLICT'}
    end
    local replay_reservation_id = redis.call('HGET', KEYS[5], 'reservationId')
    local replay_reservation_key = 'fs:{hot}:campaign:' .. campaign_id .. ':reservation:' .. replay_reservation_id
    return {
        'ACCEPTED_REPLAY', redis.call('HGET', KEYS[5], 'purchaseRequestId'), replay_reservation_id,
        redis.call('HGET', KEYS[5], 'eventId'), campaign_id, variant_id, user_id,
        redis.call('HGET', replay_reservation_key, 'inventoryAllocationId'),
        redis.call('HGET', replay_reservation_key, 'skuSnapshot'),
        redis.call('HGET', replay_reservation_key, 'unitPrice'),
        redis.call('HGET', replay_reservation_key, 'currency'),
        redis.call('HGET', replay_reservation_key, 'quantity'), request_hash, key_hash,
        redis.call('HGET', replay_reservation_key, 'acceptedAt'),
        redis.call('HGET', replay_reservation_key, 'expiresAt'),
        redis.call('HGET', KEYS[5], 'retainedUntil'),
        redis.call('HGET', replay_reservation_key, 'traceparent'),
        redis.call('HGET', replay_reservation_key, 'tracestate')
    }
end

if redis.call('EXISTS', KEYS[1]) == 0 then
    return {'CAMPAIGN_UNKNOWN'}
end
if redis.call('HGET', KEYS[1], 'recoveryRequired') == 'true' or redis.call('HGET', KEYS[1], 'state') == 'RECOVERY_REQUIRED' then
    return {'CAMPAIGN_RECOVERY_REQUIRED'}
end
local state = redis.call('HGET', KEYS[1], 'state')
if state ~= 'ACTIVE' then
    return {'CAMPAIGN_NOT_ACTIVE'}
end
local starts_at = tonumber(redis.call('HGET', KEYS[1], 'startsAt'))
local ends_at = tonumber(redis.call('HGET', KEYS[1], 'endsAt'))
if starts_at == nil or ends_at == nil then
    return {'CAMPAIGN_RECOVERY_REQUIRED'}
end
if now < starts_at then
    return {'CAMPAIGN_NOT_STARTED'}
end
if now >= ends_at then
    return {'CAMPAIGN_ENDED'}
end

if redis.call('EXISTS', KEYS[3]) == 0 or redis.call('HGET', KEYS[3], 'variantId') ~= variant_id then
    return {'VARIANT_NOT_ELIGIBLE'}
end
local remaining = tonumber(redis.call('HGET', KEYS[2], variant_id))
local per_user_limit = tonumber(redis.call('HGET', KEYS[3], 'perUserLimit'))
local user_quantity = tonumber(redis.call('HGET', KEYS[4], variant_id)) or 0
if remaining == nil or per_user_limit == nil then
    return {'VARIANT_NOT_ELIGIBLE'}
end
if quantity > remaining then
    return {'SOLD_OUT'}
end
if user_quantity > per_user_limit - quantity then
    return {'PURCHASE_LIMIT_EXCEEDED'}
end

redis.call('HINCRBY', KEYS[2], variant_id, -quantity)
redis.call('HINCRBY', KEYS[4], variant_id, quantity)
redis.call('HSET', KEYS[5], 'requestHash', request_hash, 'purchaseRequestId', purchase_request_id,
    'reservationId', reservation_id, 'eventId', event_id, 'retainedUntil', retained_until)
redis.call('PEXPIREAT', KEYS[5], retained_until)
redis.call('HSET', KEYS[6], 'purchaseRequestId', purchase_request_id, 'reservationId', reservation_id,
    'eventId', event_id, 'campaignId', campaign_id, 'variantId', variant_id, 'userId', user_id,
    'inventoryAllocationId', redis.call('HGET', KEYS[3], 'inventoryAllocationId'),
    'skuSnapshot', redis.call('HGET', KEYS[3], 'skuSnapshot'),
    'unitPrice', redis.call('HGET', KEYS[3], 'saleUnitPrice'),
    'currency', redis.call('HGET', KEYS[3], 'currency'), 'quantity', quantity,
    'requestHash', request_hash, 'idempotencyKeyHash', key_hash, 'acceptedAt', accepted_at,
    'expiresAt', expires_at, 'retainedUntil', retained_until, 'status', 'RESERVED',
    'quotaReleased', 'false', 'traceparent', traceparent, 'tracestate', tracestate)
redis.call('PEXPIREAT', KEYS[6], retained_until)
redis.call('ZADD', KEYS[7], expires_at, reservation_id)
local stream_id = redis.call('XADD', KEYS[8], '*', 'purchaseRequestId', purchase_request_id,
    'reservationId', reservation_id, 'eventId', event_id, 'campaignId', campaign_id,
    'variantId', variant_id, 'userId', user_id,
    'inventoryAllocationId', redis.call('HGET', KEYS[3], 'inventoryAllocationId'),
    'skuSnapshot', redis.call('HGET', KEYS[3], 'skuSnapshot'),
    'unitPrice', redis.call('HGET', KEYS[3], 'saleUnitPrice'), 'currency', redis.call('HGET', KEYS[3], 'currency'),
    'quantity', quantity, 'requestHash', request_hash, 'idempotencyKeyHash', key_hash,
    'acceptedAt', accepted_at, 'expiresAt', expires_at, 'retainedUntil', retained_until,
    'traceparent', traceparent, 'tracestate', tracestate)
redis.call('HSET', KEYS[5], 'handoffEntryId', stream_id)
redis.call('HSET', KEYS[6], 'handoffEntryId', stream_id)

return {'ACCEPTED_NEW', purchase_request_id, reservation_id, event_id, campaign_id, variant_id, user_id,
    redis.call('HGET', KEYS[3], 'inventoryAllocationId'), redis.call('HGET', KEYS[3], 'skuSnapshot'),
    redis.call('HGET', KEYS[3], 'saleUnitPrice'), redis.call('HGET', KEYS[3], 'currency'), quantity,
    request_hash, key_hash, accepted_at, expires_at, retained_until, traceparent, tracestate}
