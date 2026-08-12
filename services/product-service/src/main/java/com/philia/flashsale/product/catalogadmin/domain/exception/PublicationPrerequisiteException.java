package com.philia.flashsale.product.catalogadmin.domain.exception;

public final class PublicationPrerequisiteException extends CatalogDomainException {

    public PublicationPrerequisiteException(String message) {
        super("PUBLICATION_PREREQUISITE_FAILED", message);
    }
}
