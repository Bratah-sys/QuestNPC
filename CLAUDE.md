# QuestNPC — рабочий журнал для Claude Code

> Минимально необходимый контекст для продолжения работы над модом.
> Полная инвентаризация: `Docks/сводки/roadmap-v2.5.2.md` (актуальная), `Docks/сводки/release-v2.5.3.md` (чейнжлог).

---

## Текущий статус

**Версия:** `0.3.1-alpha-menurefactor-v2.5.5` (ветка `feature/menu-rework`)
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

Исправлено в **v2.5.4**: BUG-001, BUG-002, BUG-003, BUG-006, BUG-010.
Исправлено в **v2.5.5**: BUG-009, BUG-011, BUG-012, BUG-013, BUG-014.

| ID | Severity | Файл | Суть |
|---|---|---|---|
| BUG-004 | low | `entity/ai/ScheduleFollowGoal.java` | `playScheduleAnimation(...)` вызывается каждый тик в Type.ANIMATION (спам при будущей интеграции) |
| BUG-005 | low | `entity/ai/ScheduleFollowGoal.java` | `Flag.LOOK` конфликтует с `LookAtPlayerGoal` — приоритет 1 навсегда блокирует взгляд |
| BUG-007 | low | `network/RequestPatrolChangePacket.java` | Silent reject при отсутствии палки — игрок не получает фидбек |
| BUG-008 | low | `network/RenameNPCPacket.java` | Нет sanitization §-кодов — можно впихнуть цветовые форматы |

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

**2026-04-12 — v2.5.5** — патч 5 low-severity багов (cheap wins).

**Сделано:**
- **BUG-009** — добавлен `ItemCatalogScreen.invalidateCache()` + `ResourceManagerReloadListener` через `RegisterClientReloadListenersEvent` в `QuestNPC.java`. F3+T теперь очищает статический кэш каталога, следующий open пересобирает индекс.
- **BUG-011** — `DEFAULT_PATROL_SPEED` изменён с `0.3D` на `0.35D` — совпадает с `createAttributes().MOVEMENT_SPEED`. Устранён рассинхрон между новорождённым NPC и загруженным из NBT.
- **BUG-012** — из `NPCMenuScreen.init()` убран `closeSent = false;`. Поле инициализируется один раз в декларации, ресайз окна больше не реанимирует флаг → `CloseMenuPacket` не отправляется повторно.
- **BUG-013** — в `NPCTradingScreen.commitNameEdit()` добавлен скан `setNames` на коллизию; на дубликат — revert + `message.questnpc.trade_set_name_taken` (новый lang key ru/en).
- **BUG-014** — `QuestNPCEntity.clearBoundBlock()` удалён целиком (zero callers).
- Версия bumped v2.5.4 → v2.5.5: `gradle.properties`, `QuestNPC.MOD_VERSION`, `en_us.json`, `ru_ru.json`.

**Осталось:**
- WIP-001 (player-side trading screen) — главный блокер v2.6
- 4 low-severity бага (BUG-004, 005, 007, 008)
- WIP-004 (GeckoLib triggerAnim из Type.ANIMATION) — требует edge-trigger обёртки вокруг BUG-004
- Остальные заглушки — по приоритету из roadmap §5
