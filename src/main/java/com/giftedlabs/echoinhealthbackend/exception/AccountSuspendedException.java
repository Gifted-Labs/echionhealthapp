package com.giftedlabs.echoinhealthbackend.exception;

/**
 * Sign-in refused because the user's hospital has been suspended by a platform operator.
 *
 * <p>Distinct from a locked or deactivated individual account: nothing is wrong with this user,
 * and the message must say so, or support fields calls about credentials that are in fact fine.
 */
public class AccountSuspendedException extends RuntimeException {

    public AccountSuspendedException(String message) {
        super(message);
    }
}
