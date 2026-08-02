package com.philia.flashsale.authentication.serviceclient.application;

import com.philia.flashsale.authentication.serviceclient.domain.ServiceClient;
import java.util.Set;

public interface AuthenticateServiceClientUseCase {
    ServiceClient authenticate(String clientId, String rawSecret, String grantType, Set<String> scopes);
}
