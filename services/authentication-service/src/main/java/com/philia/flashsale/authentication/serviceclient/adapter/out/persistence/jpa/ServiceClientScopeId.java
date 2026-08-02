package com.philia.flashsale.authentication.serviceclient.adapter.out.persistence.jpa;

import java.io.Serializable;
import java.util.UUID;

public class ServiceClientScopeId implements Serializable {
    private UUID oauthClientId;
    private String scope;
    public ServiceClientScopeId() { }
    @Override public boolean equals(Object o) { return o instanceof ServiceClientScopeId other && java.util.Objects.equals(oauthClientId, other.oauthClientId) && java.util.Objects.equals(scope, other.scope); }
    @Override public int hashCode() { return java.util.Objects.hash(oauthClientId, scope); }
}
