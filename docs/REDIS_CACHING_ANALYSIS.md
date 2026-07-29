# Redis vs Caffeine — analysis and implementation plan

**Date:** 2026-07-29 · **Question asked:** *"Caffeine is in-memory so I'm scared it will shoot my
infrastructure cost — should I move to Redis?"*

---

## 1. Short answer

**Moving to Redis will increase your infrastructure cost, not reduce it.** Caffeine runs inside
the JVM you already pay for. Redis is an additional service with its own bill.

But you should still adopt Redis — for a different reason. **Three pieces of state in this
application are per-instance, and all three break the moment you run a second instance.** Two of
them are security issues. That is the case for Redis here, not cost.

There is also a real memory risk in the current caching, but it is one specific cache, it is not
Caffeine's fault, and **moving it to Redis would make it worse**. It should be deleted instead.

---

## 2. What Caffeine actually costs you today

`CacheConfig` defines five caches. Measured against what each one stores:

| Cache | Max entries | What one entry holds | Estimated total |
|---|---|---|---|
| `authUsers` | 10,000 | `AuthenticatedUser` — 3 fields (2 UUID strings + role enum), ~250 bytes | **~2.5 MB** |
| `users` | 1,000 | `User` entity, ~1 KB | **~1 MB** |
| `notificationCounts` | 1,000 | a single `Long` | **~50 KB** |
| `dashboardStats` | 10 | one stats DTO | negligible |
| `templates` | 500 | **an entire user's template library** | **unbounded — see below** |

Four of the five total roughly **4 MB**. That is not a cost problem on any plausible plan. Caffeine
is not what will shoot your bill.

### The one that is a genuine risk — and it isn't a cost risk

```java
@Cacheable(value = TEMPLATES, key = "#userId")
public List<TemplateResponse> getAllTemplates(String userId) { ... }
```

`maximumSize(500)` counts **entries, not bytes**. Each entry is one user's *complete* template
list, including `defaultFindings` and `defaultImpression` text for every template. At the 5,000
templates-per-user scale UR-076 specifies, a single entry can be tens of megabytes. Five hundred
of them is gigabytes — an out-of-memory crash, not a cost overrun.

**Moving this to Redis makes it strictly worse.** You would serialize tens of megabytes, ship it
over the network, and deserialize it, on every call — replacing a heap read with a network round
trip proportional to library size.

**It should simply be deleted.** This cache existed to avoid the old in-memory full-library scan.
That scan is gone: search is now a single indexed query measured at **18ms p50 / 24ms p95 at 5,000
templates**. The cache is now guarding against a cost that no longer exists.

---

## 3. The actual case for Redis: three pieces of per-instance state

None of these are about cost. All three are correctness or security, and all three are invisible
on one instance and broken on two.

### 3.1 Authentication cache eviction does not cross instances — **security**

`CustomUserDetailsService` caches the authenticated principal for **14 minutes**.
`AdminService` evicts it when an admin deactivates or locks a user — but `@CacheEvict` only clears
the local JVM's copy.

> Deactivate a compromised user on instance A. Instance B keeps authenticating them for up to 14
> minutes.

On one instance this is correct. On two it is a hole in UR-013 (deactivated users can't log in)
and UR-086 (server-side RBAC).

### 3.2 Brute-force lockout is per-instance — **security**

```java
private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();
```

`LoginAttemptService` holds failed-login state in a plain in-process map. Consequences:

- With N instances behind a load balancer, an attacker gets **N × 5** attempts before lockout,
  because each instance counts independently.
- **Lockout state is lost on every deploy or restart**, resetting all attackers to zero.

This undercuts the intent of the 5-attempt / 15-minute policy even on a single instance, because
of the restart behaviour.

### 3.3 SSE notifications only reach the instance that holds the connection — **functional**

```java
private final Map<String, CopyOnWriteArrayList<SseEmitter>> userEmitters = new ConcurrentHashMap<>();
```

`NotificationService` keeps live SSE connections in memory. A notification created on instance A
is pushed only to emitters registered on instance A. A radiologist whose browser is connected to
instance B **never receives it** — silently, with no error.

That breaks UR-062 (notification on new feedback) at multi-instance, and it fails quietly, which
is the worst failure mode for a clinical hand-off.

### Not a problem

`AiPromptTemplateService.templateCache` caches immutable prompt files read from the classpath.
Per-instance is correct; leave it alone.

---

## 4. Is it advisable?

**Right now:** you are on a single container (`Dockerfile`, single `ENTRYPOINT`, no clustering).
None of the three issues above can bite you yet, and Redis would be pure added cost and operational
surface.

**Before you run a second instance:** yes, and it is not optional. UR-096 ("architecture supports
500+ users across orgs") implies horizontal scaling, and the moment you scale out, 3.1 and 3.2
become live security defects rather than theoretical ones.

**Cost reality check** — check current pricing yourself, these are orders of magnitude only:

| Option | Rough cost | Notes |
|---|---|---|
| Caffeine (today) | **$0** | Uses heap you already pay for |
| Managed Redis, small tier | **~$5–20/month** | Railway, Upstash, ElastiCache, etc. |
| Redis container you run | container memory + your time | Cheapest in dollars, most expensive in operations |

So the honest framing: **Redis buys you correctness at multi-instance for roughly the price of a
coffee per month.** It does not save money, and nothing in the current caching is costing you money.

---

## 5. Recommended plan

### Phase 0 — Free, do this regardless of Redis

**Delete the `TEMPLATES` whole-list cache.** This is the only unbounded-memory risk in the system
and the change is a net deletion.

- Remove `@Cacheable(value = TEMPLATES, key = "#userId")` from `TemplateService.getAllTemplates`.
- Remove the eight now-pointless `@CacheEvict(value = TEMPLATES, ...)` annotations.
- Drop `TEMPLATES` from `CacheConfig`.
- Keep the existing `VaultSearchPerformancePostgresTest` as the guard — it already proves the
  uncached path is fast.

**Expected effect:** removes a potential multi-gigabyte heap risk, deletes ~10 annotations, and
costs nothing measurable in latency. Do this before worrying about Redis at all.

### Phase 1 — Only when a second instance is on the roadmap

Move the three shared-state concerns, in this order (most severe first):

**1. Login attempt tracking → Redis.** Natural fit: `INCR` + `EXPIRE` is atomic server-side, so
the counter is correct across instances without any locking. Replace the `ConcurrentHashMap` with
a `StringRedisTemplate`; the service interface does not change, so nothing else moves.

**2. Auth principal cache → Redis.** Switch the `authUsers` cache to a Redis `CacheManager` so
`@CacheEvict` clears it for every instance at once. Keep the TTL below the access-token lifetime as
it is today.

*Cheaper alternative if you want to defer Redis:* shorten the `authUsers` TTL to ~60 seconds. It
increases database reads but bounds the stale-authentication window to a minute. Weigh that against
the read load before choosing.

**3. SSE notifications → Redis pub/sub.** Each instance subscribes to a channel and fans out to
its own local emitters. `NotificationService.sendRealTimeNotification` publishes instead of writing
directly to emitters; the local emitter map stays, it just becomes the delivery mechanism rather
than the routing table.

**Leave on Caffeine:** `users`, `dashboardStats`, `notificationCounts`. Brief staleness is harmless
for all three, and keeping them local avoids a network hop on hot paths.

### Phase 2 — Operational requirements before this goes to production

- Redis must be **TLS-encrypted in transit and authenticated** — it will hold authenticated
  principals and session-adjacent data. This is PHI-adjacent infrastructure under UR-080/UR-081.
- **Decide the failure mode.** If Redis is unavailable, does login fail closed (secure, outage) or
  fall back to local state (available, weaker lockout)? Make this an explicit, documented decision
  rather than whatever the default happens to be.
- Set an eviction policy (`allkeys-lru`) and a memory cap so Redis cannot grow unbounded.
- Do **not** cache report or template *content* in Redis without revisiting the encryption-at-rest
  question already open from the audit (§8.4) — that would put clinical text in a second datastore.

---

## 6. What I would do

1. **Do Phase 0 now.** It removes a real OOM risk, costs nothing, and is a deletion.
2. **Do not add Redis until you are actually about to scale out.** On one instance it adds cost,
   a new failure mode, and operational surface, and buys nothing.
3. **When you do plan a second instance, treat Phase 1 as a blocker on that work**, not a
   follow-up. Items 3.1 and 3.2 become live security defects the moment the second instance takes
   traffic, and 3.3 fails silently in a clinical workflow.

The thing to correct is the mental model, not the cache: Caffeine is not costing you money, and
Redis will not save you any. Adopt it when you need shared state — which is soon, but not today.
