package com.philia.flashsale.flashsale.reservation.application.port.in;

import com.philia.flashsale.flashsale.reservation.application.command.ReserveCampaignQuotaCommand;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationDecisionResult;

/** Driving port for the Flash Sale reservation admission use case. */
public interface ReserveCampaignQuotaUseCase {
    ReservationDecisionResult reserve(ReserveCampaignQuotaCommand command);
}
