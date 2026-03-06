# Ascension SMP — Agent Instructions

> **File:** `<java_project_root>/.agent/instructions.md`
> The AI crew reads this before touching any source file.

---

## 1. Project Context

- **Stack:** Fabric Loader · Java 21 · Minecraft 1.21.x · Dedicated Server only.
- This project is **incomplete**. The Cartographer must audit existing code before suggesting additions. Known gaps include:
  - Mythical weapons — not yet implemented.
  - Nether and End orders — baseline structure only; no abilities are functional.
  - Earth order — demigod abilities largely working but may need restructuring to match the vision doc (see `Ascension SMP For Agents.pdf`).

---

## 2. Server-Side Constraint (Non-Negotiable)

- **Zero** imports from `net.minecraft.client.*` anywhere under `src/main/java/`.
- All code must compile and run on a dedicated server with no client present.
- Visual feedback (e.g. VFXBuilder animations) must be triggered via server-to-client packets or vanilla-compatible entity data (block displays / particles).

---

## 3. The "Golden" Architecture — Use Existing Systems First

| Concern                 | Canonical System                        | Rule                                                                                                              |
| ----------------------- | --------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| Ability checks          | `AbilityManager.java`                   | Never read `AscensionData` directly for ability logic; use broadcast/match methods.                               |
| Spells                  | `SpellRegistry` + `Spell` interface     | Every spell must extend `Spell` and be registered in `SpellRegistry`.                                             |
| VFX                     | `VFXBuilder.java`                       | All custom animations go through `VFXBuilder`. Do not use GeckoLib, FancyCore, or any external animation library. |
| Player persistence      | `AscensionData.java`                    | Use existing getter/setter patterns for influence, rank, and equipped orders.                                     |
| Persistent world data   | `PersistentStateManager`                | Keyed to `ServerWorld` or `MinecraftServer` — never static maps.                                                  |
| Transient per-tick data | `EntityDataSaver` (Mixin into `Entity`) | For data that does not need to survive restarts.                                                                  |

---

## 4. Registry Lifecycle

- Register all game objects (blocks, items, entities, data components) during `ModInitializer.onInitialize()` — **never** in static initialisers or field declarations that execute before the Fabric lifecycle.
- Follow the strict order: **registries → data → events**.
- The Docs Specialist must verify the `RegistryKey` for any new block or entity against the latest Fabric API before merging.

---

## 5. Mixin Guidelines

- Use Mixins **only** as a last resort when no Fabric API hook exists.
- Every Mixin must include a comment explaining why no event alternative was available.
- Injection footprint must be minimal:
  - Prefer `@Inject` over `@Overwrite`.
  - Prefer `HEAD` / `RETURN` or `TAIL` over arbitrary `INVOKE` shift targets.
  - Never use `@Redirect` unless absolutely necessary — it breaks compatibility with other mods.
- All Mixin classes live exclusively under `com.ascension.mixin`, subdivided by category (e.g. `combat`, `world`, `entity`).

---

## 6. Task Scheduler & Cleanup (Critical)

Every scheduled or repeating task (via `TaskScheduler`, `ServerTickEvents`, or any custom tick mechanism) **must**:

1. Register a cleanup hook in **both**:
   - `ServerPlayConnectionEvents.DISCONNECT` — for per-player tasks.
   - `ServerLifecycleEvents.SERVER_STOPPING` — for world/global tasks.
2. The cleanup hook must cancel the task and release all associated server-side state (block entities, entity references, NBT handles).
3. If the feature manages block displays, particle sources, or geyser tasks, the cleanup hook must run **before** chunk unload.

> **Rationale:** Orphaned block display entities and lingering tick tasks accumulate across restarts, causing memory leaks and desync bugs (e.g. the Magma Bubble crash pattern).

---

## 7. Available Libraries

Before implementing any feature, read `build.gradle` and `gradle.properties` to identify available dependencies. Do not duplicate library behaviour with manual Mixins or raw packet construction.

| Library         | Gradle Coordinate                   | Use for                                                                                                        |
| --------------- | ----------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| **DisguiseLib** | `com.github.NucleoidMC:DisguiseLib` | Entity spoofing / shapeshifting / packet spoofing. Use its public API — never write manual S2C entity packets. |
| **SGUI**        | _(check build.gradle)_              | Custom book and chest GUI interfaces.                                                                          |
| _(others)_      | _(check build.gradle)_              | Consult the Landscape Report for the full dependency list.                                                     |

For canonical usage examples of project-specific libraries, refer to `LibReferences.java` in the root package. Treat it as the style guide for libraries such as DisguiseLib and SGUI.

---

## 8. Coordination Protocol

1. **Cartographer** — must identify the specific Java file and line number of the relevant `AbilityManager` broadcast point before any code change is proposed. Also responsible for auditing existing code to determine what is missing or needs restructuring.
2. **Lead Developer** — proposes code changes only after the Cartographer's report.
3. **Docs Specialist** — verifies `RegistryKey` values for all new blocks or entities against the latest Fabric API before implementation.
4. **QA Tester** — must provide `./gradlew runGameTest` log output as proof of completion. A green test run is required before the next feature begins.

---

## 9. Feature Sequencing

- Features are implemented **one at a time**, in strict sequential order.
- Parallel implementation is **forbidden** to prevent concurrent edits to shared files such as `AbilityManager.java`.
- The QA Tester must confirm a green `runGameTest` run before work on the next feature starts.
