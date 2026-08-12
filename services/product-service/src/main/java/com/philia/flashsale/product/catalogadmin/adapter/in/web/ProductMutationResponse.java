package com.philia.flashsale.product.catalogadmin.adapter.in.web;

import java.util.UUID;

/** Small response returned after a successful composition update. */
record ProductMutationResponse(UUID id, long version) { }
