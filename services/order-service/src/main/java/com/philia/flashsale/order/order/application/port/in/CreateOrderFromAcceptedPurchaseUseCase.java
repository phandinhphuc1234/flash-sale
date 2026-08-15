package com.philia.flashsale.order.order.application.port.in;

import com.philia.flashsale.order.order.application.command.CreateOrderFromAcceptedPurchaseCommand;
import com.philia.flashsale.order.order.application.result.OrderCreationResult;

/** Inbound business boundary for recording one accepted purchase as one Order. */
public interface CreateOrderFromAcceptedPurchaseUseCase {
    OrderCreationResult create(CreateOrderFromAcceptedPurchaseCommand command);
}
