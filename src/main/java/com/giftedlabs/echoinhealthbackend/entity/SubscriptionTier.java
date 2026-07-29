package com.giftedlabs.echoinhealthbackend.entity;

public enum SubscriptionTier {
    BASIC(5, 250, 1000, false),
    PRO(15, 600, 1500, true),
    ULTIMATE(25, 1024, 2000, true);

    private final int maxUsers;
    private final int storageLimitMb;
    private final int aiCreditsPerMonth;
    private final boolean autoGrammarCheckEnabled;

    SubscriptionTier(int maxUsers, int storageLimitMb, int aiCreditsPerMonth, boolean autoGrammarCheckEnabled) {
        this.maxUsers = maxUsers;
        this.storageLimitMb = storageLimitMb;
        this.aiCreditsPerMonth = aiCreditsPerMonth;
        this.autoGrammarCheckEnabled = autoGrammarCheckEnabled;
    }

    public int getMaxUsers() {
        return maxUsers;
    }

    public int getStorageLimitMb() {
        return storageLimitMb;
    }

    public int getAiCreditsPerMonth() {
        return aiCreditsPerMonth;
    }

    public boolean isAutoGrammarCheckEnabled() {
        return autoGrammarCheckEnabled;
    }
}
