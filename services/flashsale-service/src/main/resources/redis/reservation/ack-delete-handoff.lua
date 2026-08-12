-- A handoff is removed only after its terminal PostgreSQL outcome is committed.
local stream = KEYS[1]
local group = ARGV[1]
local entry_id = ARGV[2]

local acknowledged = redis.call('XACK', stream, group, entry_id)
if acknowledged == 1 then
    return redis.call('XDEL', stream, entry_id)
end
return 0
