package com.giftedlabs.echoinhealthbackend.dto.collaboration;

import com.giftedlabs.echoinhealthbackend.entity.Designation;
import com.giftedlabs.echoinhealthbackend.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A colleague the signed-in user can share a scan with.
 *
 * <p>Deliberately minimal: enough to identify a person in a recipient picker, and nothing more.
 * The share dialog previously had no endpoint to populate from at all.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareableColleagueResponse {

    private String id;
    private String fullName;
    private String email;
    private Role role;
    private String department;
    private Designation designation;
}
