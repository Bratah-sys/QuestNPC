# QuestNPC — рабочий журнал для Claude Code

> Минимально необходимый контекст для продолжения работы над модом.
> Полная инвентаризация: `Docks/сводки/roadmap-v2.5.2.md` (актуальная), `Docks/сводки/release-v2.5.3.md` (чейнжлог).

---

## Текущий статус

**Версия:** `0.3.1-alpha-menurefactor-v2.5.4` (ветка `feature/menu-rework`)
**Стек:** Minecraft 1.20.1 · Forge 47.2.0 · Java 17 · Gradle 8.7 · ForgeGradle 6.x

### Работающие фичи (admin-side, палка + ПКМ)
- Спавн через creative egg (вкладка QuestNPC)
- Переименование (длина 1–32)
- Атрибуты патруля: `patrolSpeed` (0.05–1.0), `patrolDelayMin/Max` (1–120с)
- Сменa точки патруля спец-палкой по блоку
- Смена модели: ванильные сущности + кастомные `.geo.json` из `config/questnpc/custom_models/`
- Торговля: до 5 именованных наборов (`TradeSets` NBT ListTag), визуальный каталог предметов, миграция legacy `TradeOffers` → `Default`
- Расписание: до 10 слотов по времени суток, типы ACTIVITY/TRADE/ANIMATION, привязка набора сделок к слоту, toggle ON/OFF, приоритет над патрулём
- Копирование UUID, удаление (двойной клик)
- Персистентность: `setPersistenceRequired()` + override `removeWhenFarAway()` → NPC не диспавнится

### Заглушки (кнопки/фичи без реализации)
- **Player-side торговля** — взаимодействие игрока с NPC открывает НИЧЕГО (критический блокер)
- `Movement.PATROL` в слотах расписания — ведёт себя как `POINT`
- Dialog · Equipment · Goals · Pose · Rotation · Scale · Change Skin · Import · Export
- GeckoLib `triggerAnim` из Type.ANIMATION слотов (`playScheduleAnimation` — no-op)
- Quest-взаимодействие в слоте (кнопка disabled)
- Patchouli book — зарегистрирован, контента нет

---

## Известные баги (подробности: `roadmap-v2.5.2.md`)

Исправлено в **v2.5.4**: BUG-001, BUG-002, BUG-003, BUG-006, BUG-010 (5 штук).

| ID | Severity | Файл | Суть |
|---|---|---|---|
| BUG-004 | low | `entity/ai/ScheduleFollowGoal.java` | `playScheduleAnimation(...)` вызывается каждый тик в Type.ANIMATION (спам при будущей интеграции) |
| BUG-005 | low | `entity/ai/ScheduleFollowGoal.java` | `Flag.LOOK` конфликтует с `LookAtPlayerGoal` — приоритет 1 навсегда блокирует взгляд |
| BUG-007 | low | `network/RequestPatrolChangePacket.java` | Silent reject при отсутствии палки — игрок не получает фидбек |
| BUG-008 | low | `network/RenameNPCPacket.java` | Нет sanitization §-кодов — можно впихнуть цветовые форматы |
| BUG-009 | low | `client/gui/ItemCatalogScreen.java:77-79` | `static final CACHED_ENTRIES/NAMESPACES/DISPLAY_NAMES` — никогда не чистятся, утечка при разных resource packs |
| BUG-011 | low | `entity/QuestNPCEntity.java` | `createAttributes` ставит `MOVEMENT_SPEED=0.35`, а `DEFAULT_PATROL_SPEED=0.3` — расхождение дефолтов |
| BUG-012 | low | `client/gui/NPCMenuScreen.java:136` | `closeSent=false` в `init()` — при ресайзе окна сессия закрывается повторно |
| BUG-013 | low | `entity/QuestNPCEntity.java` trade sets | Нет проверки уникальности имени набора при переименовании |
| BUG-014 | trivial | `entity/QuestNPCEntity.java` | `clearBoundBlock()` — private, нигде не вызывается (dead code) |

**Release blocker:** WIP-001 (player-side trading). BUG-002 (обход PatrolChange без сессии) закрыт в v2.5.4.

---

## Архитектурные решения v2.5.x

### Наборы сделок (v2.4.0 → v2.4.1)
- NBT: `TradeSets` (ListTag из CompoundTag `{name:String, offers:ListTag}`)
- Legacy миграция: `TradeOffers` из v2.4.1 → автоматически заворачивается в набор `Default` при `load()`
- Привязка к слотам расписания: `ScheduleEntry.tradeSetName` (String), резолвится в рантайме при входе в Type.TRADE-слот
- Общие хелперы сериализации: `UpdateTradeOffersPacket.writeTradeSets/readTradeSets` переиспользуются из `OpenNPCMenuPacket`

### Расписание (v2.5.0 → v2.5.3)
- `ScheduleEntry` (POJO): `{Type(ACTIVITY/TRADE/ANIMATION), Movement(POINT/PATROL), startMin/endMin, BlockPos, animation:String, tradeSetName, interactDialog/interactQuest}`
- NBT: `Schedule` ListTag + `ScheduleEnabled` boolean на сущности
- Время: `getDayTime() % 24000`, tick 0 = 06:00 (wall-clock)
- Runtime-маркеры: `currentEntryIndex`, `getActiveScheduleEntry()` — линейный поиск по списку (N≤10)
- Приоритет goal:
  - `ScheduleFollowGoal` (priority 1, Flag.MOVE+LOOK) — preempts всё
  - `BoundedStrollGoal` (priority 2, Flag.MOVE) — `canUse()` возвращает false пока `scheduleEnabled && активный слот`
  - `FloatGoal(0)`, `LookAtPlayer(4)`, `RandomLookAround(5)`
- Safety-net в `tick()` (force-navigate при дрейфе >24) отключён пока расписание активно — предотвращает клоббер путей

### Персистентность NPC
- `setPersistenceRequired(true)` в `finalizeSpawn` + override `removeWhenFarAway() → false`
- До v2.5.3 NPC терялись через ~30с при удалении игрока дальше 32 блоков

### Сессии админ-меню (v2.2.0+)
- `NPCMenuSessionManager`: `ConcurrentHashMap<UUID, SessionEntry(npcId, timestamp)>`
- Timeout 120с, cleanup каждые 400 тиков, закрытие при `PlayerLoggedOutEvent`
- Валидируют пакеты: `UpdateSettings`, `Rename`, `ChangeModel`, `UpdateTradingEnabled`, `UpdateTradeOffers`, `UpdateSchedule`
- **НЕ валидируют**: `RequestPatrolChange` (BUG-002), `Delete` (BUG-003)

### Кастомные модели (v2.3.0)
- `CustomModelPackResources` — виртуальный `PackResources`, регистрируется через `AddPackFindersEvent`
- `config/questnpc/custom_models/<name>.geo.json` → `questnpc:geo/custom/<name>.geo.json`
- NBT `ModelEntityType = "custom:<name>"` отличает от ванильных entity types
- Валидация имени в `ChangeModelPacket`: regex `[a-zA-Z0-9_\-]+`
- После добавления файлов: F3+T

### Сетевой канал
- `ModNetwork.PROTOCOL_VERSION = "5"`, SimpleChannel
- Пакеты id 0–12: 0 ToggleVis · 1 PathSync · 2 OpenNPCMenu · 3 RequestPatrolChange · 4 UpdateSettings · 5 Rename · 6 Delete · 7 ChangeModel · 8 CloseMenu · 9 UpdateTradingEnabled · 10 UpdateTradeOffers · 11 **OpenTradingScreen (dead)** · 12 UpdateSchedule

---

## Последняя сессия

**2026-04-12 — v2.5.4** — патч 5 багов.

**Сделано:**
- **BUG-002** (high) — в `RequestPatrolChangePacket.handle` добавлена проверка `NPCMenuSessionManager.isSessionActive(...)` по образцу пакетов 4/5/7; спец-палка больше не выдаётся без открытого меню
- **BUG-003** (medium) — в `DeleteNPCPacket.handle` добавлена проверка сессии В ДОПОЛНЕНИЕ к существующей проверке дистанции ≤16 блоков
- **BUG-006** (medium) — `setSchedule()`/`setScheduleEnabled()` теперь вызывают `getNavigation().stop()` (server-only), NPC не инерционно дрейфует к удалённому слоту
- **BUG-010** (medium) — `ScheduleFollowGoal.tick()` строит `Path` через `createPath(BlockPos,0)` и проверяет `canReach()`; при провале — одноразовый warning + 5-сек `UNREACHABLE_RETRY_TICKS` кулдаун
- **BUG-001** (medium) — handler `OpenTradingScreenPacket` очищен от `WIPScreen`, пакет оставлен как будущий канал WIP-001 (id 11, регистрация не тронута)
- Версия bumped v2.5.3 → v2.5.4: `gradle.properties`, `QuestNPC.MOD_VERSION`, `en_us.json`, `ru_ru.json`

**Осталось:**
- WIP-001 (player-side trading screen) — главный блокер v2.6
- 9 low-severity багов (BUG-004, 005, 007, 008, 009, 011, 012, 013, 014)
- WIP-004 (GeckoLib triggerAnim из Type.ANIMATION) — требует edge-trigger обёртки вокруг BUG-004
- Остальные заглушки — по приоритету из roadmap §5
