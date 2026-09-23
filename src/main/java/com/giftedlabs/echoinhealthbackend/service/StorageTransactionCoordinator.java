package com.giftedlabs.echoinhealthbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Coordinates object storage with database transactions.
 *
 * <p>R2 is not part of the PostgreSQL transaction. If a database commit fails after an object
 * has been uploaded, this compensating action removes the object so failed requests do not leak
 * storage or consume a tenant's quota indefinitely.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StorageTransactionCoordinator {

    private final FileStorageService fileStorageService;

    public void deleteOnRollback(String filePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED && !fileStorageService.deleteFile(filePath)) {
                    log.error("Could not remove object {} after database transaction rollback", filePath);
                }
            }
        });
    }
}
