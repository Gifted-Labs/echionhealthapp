package com.giftedlabs.echoinhealthbackend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageTransactionCoordinatorTest {

    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final StorageTransactionCoordinator coordinator =
            new StorageTransactionCoordinator(fileStorageService);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deletesUploadedObjectWhenDatabaseTransactionRollsBack() {
        when(fileStorageService.deleteFile("org/user/report.docx")).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();

        coordinator.deleteOnRollback("org/user/report.docx");
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(fileStorageService).deleteFile("org/user/report.docx");
    }

    @Test
    void keepsUploadedObjectWhenDatabaseTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();

        coordinator.deleteOnRollback("org/user/report.docx");
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(fileStorageService, never()).deleteFile("org/user/report.docx");
    }
}
