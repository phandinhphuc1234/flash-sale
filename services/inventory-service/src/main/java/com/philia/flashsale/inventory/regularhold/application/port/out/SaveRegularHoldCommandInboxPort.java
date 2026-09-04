package com.philia.flashsale.inventory.regularhold.application.port.out;

import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldCommandInboxEntry;

public interface SaveRegularHoldCommandInboxPort {
    RegularHoldCommandInboxEntry save(RegularHoldCommandInboxEntry entry);
}
