package com.giftedlabs.echoinhealthbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Many-to-many mapping between reports and folders
 */
@Entity
@Table(name = "report_folders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportFolder {

    @EmbeddedId
    private ReportFolderId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("reportId")
    @JoinColumn(name = "report_id")
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("folderId")
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportFolderId implements Serializable {
        @Column(name = "report_id")
        private String reportId;

        @Column(name = "folder_id")
        private String folderId;
    }
}
