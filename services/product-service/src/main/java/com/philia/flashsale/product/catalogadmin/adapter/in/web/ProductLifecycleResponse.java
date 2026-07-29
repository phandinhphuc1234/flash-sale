package com.philia.flashsale.product.catalogadmin.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

/** Stable response for publish/deactivate/archive lifecycle commands. */
record ProductLifecycleResponse(UUID id, String status, Instant publishedAt, long version) { }
