package com.philia.flashsale.payment.payment.application.model.recovery;

/** Durable recovery work categories approved by the Payment data model. */
public enum RecoveryWorkType {
    CREATE_SESSION,
    REFRESH_SESSION,
    EXPIRE_SESSION;

    public static RecoveryWorkType from(String value) {
        try {
            return value == null ? null : valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
