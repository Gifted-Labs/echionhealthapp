package com.giftedlabs.echoinhealthbackend.dto.org;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationBrandingResponse {
    private String organizationId;
    private String organizationName;
    private String hospitalName;
    private String address;
    private String phone;
    private String email;
    private String website;
    private String letterheadUrl;
    private boolean usingDefaultLetterhead;
    private String defaultLetterheadLabel;
}
