package com.giftedlabs.echoinhealthbackend.entity;

public enum AiRequestType {
    FULL_REPORT_GENERATION,
    IMPRESSION_SUGGESTION,
    GRAMMAR_CHECK,
    TERMINOLOGY_SEARCH,
    /** Admin connectivity probe; never billed against organization AI credits. */
    PROVIDER_VERIFICATION
}
