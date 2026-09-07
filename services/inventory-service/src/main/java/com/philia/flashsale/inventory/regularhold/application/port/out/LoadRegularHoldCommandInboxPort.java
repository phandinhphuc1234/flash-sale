package com.philia.flashsale.inventory.regularhold.application.port.out;

import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldCommandInboxEntry;
import java.util.Optional;
import java.util.UUID;

public interface LoadRegularHoldCommandInboxPort {
    Optional<RegularHoldCommandInboxEntry> findByCommandId(UUID commandId);
    Optional<RegularHoldCommandInboxEntry> findBySourcePosition(String topic, int partition, long offset);
}
