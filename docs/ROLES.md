# Roles and their operations

This is the authoritative statement of who can do what in Echion Health.

Before this document existed, the rules lived only as copy-pasted role lists inside eight separate
`@PreAuthorize` annotations, with no single place that stated them — which is why the question
"what can each role actually do?" had no good answer. The lists now come from
[`RoleGroups`](../src/main/java/com/giftedlabs/echoinhealthbackend/security/RoleGroups.java), the
capability catalogue from
[`Permission`](../src/main/java/com/giftedlabs/echoinhealthbackend/security/Permission.java), and
`RolePermissionAlignmentTest` fails the build if this file falls out of step with either.

Applications can read the same answer for the signed-in user from `GET /api/auth/permissions`.

## The six roles

| Role | Who they are |
| --- | --- |
| `SONOGRAPHER` | Performs ultrasound scans and drafts reports. |
| `RADIOLOGIST` | Reviews, finalises and signs reports. |
| `PHYSICIAN` | Referring or specialist clinician working within reporting workflows. |
| `HOSPITAL_ADMIN` | Administers one hospital: its people, branding and plan. Not a platform role. |
| `ADMIN` | Echion Health staff with cross-tenant read access and platform role assignment. |
| `SUPER_ADMIN` | Full platform control: tenant lifecycle, billing, impersonation, maintenance. |

`HOSPITAL_ADMIN` is scoped to a single hospital and can never see or touch another. `ADMIN` and
`SUPER_ADMIN` belong to the Echion Health platform organization rather than to any hospital.

## Capability matrix

`Yes` means the role holds the capability outright. `Granted` means it depends on a per-user
setting, explained below the table.

| Capability | Key | Sonographer | Radiologist | Physician | Hospital admin | Admin | Super admin |
| --- | --- | :-: | :-: | :-: | :-: | :-: | :-: |
| Create and edit reports | `reports:write` | Yes | Yes | Yes | Yes | Yes | Yes |
| Upload source documents | `reports:upload` | Yes | Yes | Yes | Yes | Yes | Yes |
| Export PDF / DOCX / HL7 | `reports:export` | Yes | Yes | Yes | Yes | Yes | Yes |
| Finalise and sign a report | `reports:finalize` | Granted | Yes | Yes | Yes | — | Yes |
| AI generation and impressions | `ai:generate` | Yes | Yes | Yes | Yes | Yes | Yes |
| Manage templates | `templates:manage` | Yes | Yes | Yes | Yes | Yes | Yes |
| Manage folders | `folders:manage` | Yes | Yes | Yes | Yes | Yes | Yes |
| Share scans (SonoShare) | `collaboration:share` | Yes | Yes | Yes | Yes | Yes | Yes |
| Hospital branding and letterhead | `branding:manage` | — | — | — | Yes | Yes | Yes |
| Manage users in own hospital | `users:manage` | — | — | — | Yes | Yes | Yes |
| View audit log for own hospital | `audit:view` | — | — | — | Yes | Yes | Yes |
| View analytics for own hospital | `analytics:view` | — | — | — | Yes | Yes | Yes |
| View own plan and quota | `billing:view` | — | — | — | Yes | Yes | Yes |
| Assign platform roles | `users:assignPlatformRoles` | — | — | — | — | Yes | Yes |
| Read across all tenants | `tenants:viewAll` | — | — | — | — | Yes | Yes |
| Delete a user | `users:delete` | — | — | — | — | — | Yes |
| Change a tenant's plan or add-ons | `billing:manage` | — | — | — | — | — | Yes |
| Create and suspend organizations | `tenants:manage` | — | — | — | — | — | Yes |
| Impersonate a user | `platform:impersonate` | — | — | — | — | — | Yes |
| System maintenance | `platform:maintain` | — | — | — | — | — | Yes |

### Signature permission

A sonographer holds `reports:finalize` only when a hospital admin has enabled
`canUploadSignature` on their account, via
`PUT /api/admin/users/{id}/signature-permission`. This is intentional: signing a report is a
clinical attestation, and which sonographers may do it is a decision for each hospital rather than
a property of the role.

`GET /api/auth/permissions` reflects the grant, so the UI can show or hide the finalise control
without probing for a 403.

## What each role sees

**Sonographer, radiologist, physician** — SonoVault, SonoScribe and SonoShare for their own
organization. They cannot reach `/admin/**` at all.

**Hospital admin** — everything a clinician can do, plus `/admin/**` scoped to their own hospital:
users, audit log, analytics, branding, and a read-only view of their plan and usage. Attempting to
act on another hospital returns `403`.

**Admin** — cross-tenant read access and the ability to assign platform roles. Cannot change a
tenant's plan, delete users, suspend organizations, or impersonate.

**Super admin** — everything, including `/admin/platform/**`: the tenant list, tenant
provisioning and suspension, billing changes, system health, the cross-tenant audit log, and
impersonation.

## Impersonation

A super admin can open a session as any non-super-admin user via
`POST /api/admin/platform/impersonate/{userId}`, supplying a reason. The constraints are
deliberate:

- The token expires after 30 minutes and **no refresh token is issued**, so the session cannot be
  renewed or quietly kept alive.
- Another `SUPER_ADMIN` cannot be impersonated, nor can a locked or deactivated account.
- Every audit row written during the session records **both** identities, so an action is never
  attributed to the clinician alone.
- `GET /api/auth/profile` returns `impersonating: true` and the real actor's email, so the UI can
  show a persistent banner and an exit control.
- Starting and stopping a session are themselves audit events, with the reason attached.

Stop a session with `POST /api/admin/platform/impersonate/stop`, either from the super admin's own
session naming the `impersonationId`, or from inside the impersonated session itself.

## Organization suspension

A suspended hospital blocks sign-in for every one of its members. `ADMIN` and `SUPER_ADMIN` are
exempt, so suspending a tenant can never lock the operator out of the console they would use to
reverse it. Suspension deletes nothing and is fully reversible via
`PATCH /api/admin/platform/organizations/{id}/status`.

## Adding a role or changing access

1. Add the role to `Role`, and to the relevant sets in `RoleGroups`.
2. Grant it capabilities in `Permission`.
3. Update the matrix above.

`RolePermissionAlignmentTest` fails if a controller hardcodes a role list instead of referencing
`RoleGroups`, or if a capability exists in code but is missing from this file.
