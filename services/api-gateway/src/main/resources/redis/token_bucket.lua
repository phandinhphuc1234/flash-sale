local MAX_SAFE_INTEGER = 9007199254740991

local function tuple(status, value)
    return { status, value, "0" }
end

local function error_tuple(reason)
    return tuple("ERROR", reason)
end

local function is_canonical_unsigned_decimal(value)
    if type(value) ~= "string" then
        return false
    end
    return value == "0" or value:match("^[1-9][0-9]*$") ~= nil
end

local function parse_canonical_integer(value)
    if not is_canonical_unsigned_decimal(value) then
        return nil
    end

    local number = tonumber(value)
    if number == nil or number < 0 or number > MAX_SAFE_INTEGER then
        return nil
    end

    return number
end

local function parse_positive_argument(value)
    local number = parse_canonical_integer(value)
    if number == nil or number <= 0 then
        return nil
    end
    return number
end

local function integer_text(value)
    return string.format("%.0f", value)
end

local bucket_key = KEYS[1]
if bucket_key == nil or bucket_key == "" then
    return error_tuple("INVALID_ARGUMENT")
end

local capacity_credit = parse_positive_argument(ARGV[1])
local refill_tokens = parse_positive_argument(ARGV[2])
local request_cost_credit = parse_positive_argument(ARGV[3])
local full_refill_ms = parse_positive_argument(ARGV[4])
local state_ttl_ms = parse_positive_argument(ARGV[5])

if capacity_credit == nil
        or refill_tokens == nil
        or request_cost_credit == nil
        or full_refill_ms == nil
        or state_ttl_ms == nil
        or request_cost_credit > capacity_credit then
    return error_tuple("INVALID_ARGUMENT")
end

local redis_time = redis.call("TIME")
local now_ms = (tonumber(redis_time[1]) * 1000) + math.floor(tonumber(redis_time[2]) / 1000)

local key_type = redis.call("TYPE", bucket_key)
if type(key_type) == "table" then
    key_type = key_type["ok"]
end

local ttl_ms = redis.call("PTTL", bucket_key)
local working_credit
local last_refill_ms

if key_type == "none" then
    if ttl_ms ~= -2 then
        return error_tuple("INVALID_STATE")
    end
    working_credit = capacity_credit
    last_refill_ms = now_ms
elseif key_type == "hash" then
    if ttl_ms < 0 then
        return error_tuple("INVALID_STATE")
    end

    local state = redis.call("HMGET", bucket_key, "credit", "last_refill_ms")
    local stored_credit = parse_canonical_integer(state[1])
    local stored_last_refill_ms = parse_canonical_integer(state[2])

    if stored_credit == nil
            or stored_last_refill_ms == nil
            or stored_credit > capacity_credit
            or stored_last_refill_ms <= 0
            or stored_last_refill_ms > now_ms then
        return error_tuple("INVALID_STATE")
    end

    working_credit = stored_credit
    last_refill_ms = stored_last_refill_ms
else
    return error_tuple("INVALID_STATE")
end

local elapsed_ms = now_ms - last_refill_ms
if elapsed_ms < 0 then
    return error_tuple("INVALID_STATE")
end

if working_credit < capacity_credit and elapsed_ms > 0 then
    if elapsed_ms >= full_refill_ms then
        working_credit = capacity_credit
    else
        local refill_credit = elapsed_ms * refill_tokens
        if refill_credit >= request_cost_credit then
            local missing_credit = capacity_credit - working_credit
            if refill_credit >= missing_credit then
                working_credit = capacity_credit
            else
                working_credit = working_credit + refill_credit
            end
        end
    end
end

if working_credit >= request_cost_credit then
    local remaining_credit = working_credit - request_cost_credit
    redis.call(
            "HSET",
            bucket_key,
            "credit",
            integer_text(remaining_credit),
            "last_refill_ms",
            integer_text(now_ms))
    redis.call("PEXPIRE", bucket_key, state_ttl_ms)
    return tuple("ALLOWED", integer_text(remaining_credit))
end

return tuple("REJECTED", integer_text(working_credit))
