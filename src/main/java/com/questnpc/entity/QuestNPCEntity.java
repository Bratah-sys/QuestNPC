package com.questnpc.entity;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.ai.BoundedStrollGoal;
import com.questnpc.entity.ai.ScheduleFollowGoal;
import com.questnpc.entity.schedule.ScheduleEntry;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.PathSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.constant.DefaultAnimations;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Квестовый NPC — PathfinderMob с ручной привязкой к точке патруля.
 * По умолчанию стоит на месте. Патруль активируется через меню (палка + спец-палка).
 */
public class QuestNPCEntity extends PathfinderMob implements GeoEntity, Merchant {

    /** Радиус патруля вокруг привязанного блока. */
    public static final int PATROL_RADIUS = 16;

    // --- Дефолтные значения настроек ---
    public static final double DEFAULT_PATROL_SPEED = 0.35D;
    public static final int DEFAULT_DELAY_MIN = 3;
    public static final int DEFAULT_DELAY_MAX = 10;

    // --- Дефолтные размеры хитбокса (как у жителя) ---
    private static final EntityDimensions DEFAULT_DIMENSIONS = EntityDimensions.scalable(0.6F, 1.95F);

    // --- Текущие размеры хитбокса (меняются при смене модели) ---
    private EntityDimensions currentDimensions = DEFAULT_DIMENSIONS;

    // --- Клиентское хранение узлов навигационного пути (заполняется через PathSyncPacket) ---
    private List<Vec3> clientPathNodes = Collections.emptyList();

    // Серверная сторона: отслеживание смены пути для синхронизации
    private int lastSyncedPathNodeCount = -1;
    private int lastSyncedNextNodeIndex = -1;

    // --- Динамическая цель патруля ---
    private BoundedStrollGoal boundedStrollGoal = null;

    // --- Настраиваемые параметры патруля ---
    private double patrolSpeed = DEFAULT_PATROL_SPEED;
    private int patrolDelayMin = DEFAULT_DELAY_MIN;
    private int patrolDelayMax = DEFAULT_DELAY_MAX;

    // --- Торговля ---
    public static final int MAX_TRADE_SETS = 5;
    public static final int MAX_OFFERS_PER_SET = 10;
    public static final String DEFAULT_TRADE_SET_NAME = "Default";

    private boolean tradingEnabled = false;
    private final List<TradeSet> tradeSets = new ArrayList<>();

    private Player tradingPlayer;
    private MerchantOffers tradeOffers;
    private long lastTick = -1; // Время последнего тика — для restock-логики при смене дня / time set
    // --- Расписание ---
    public static final int MAX_SCHEDULE_ENTRIES = 10;

    private final List<ScheduleEntry> schedule = new ArrayList<>();
    private boolean scheduleEnabled = false;

    // --- Клиентская копия расписания (для рендера /npc_vis), синкается через ScheduleSyncPacket ---
    private List<ScheduleEntry> clientSchedule = new ArrayList<>();
    private boolean clientScheduleEnabled = false;

    // --- Экипировка (v2.8.0): кастомное хранилище, НЕ vanilla armorItems ---
    // Сделано отдельно от LivingEntity.armorItems специально, чтобы броня НЕ отрендерилась
    // на NPC (vanilla armor layer читает getItemBySlot(), который мы не переопределяем).
    public static final int EQUIPMENT_SLOTS = 4; // 0=HEAD, 1=CHEST, 2=LEGS, 3=FEET
    // Фиксированный UUID для AttributeModifier — НЕ менять, иначе старая броня не удалится из ARMOR.
    private static final UUID EQUIPMENT_ARMOR_MODIFIER_UUID =
            UUID.fromString("b1a2c3d4-e5f6-7890-abcd-ef0123456789");
    private final ItemStack[] equipment = {
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
    };

    // --- Квесты (v2.9.0, foundation) — research §3.4 NBT-схема ---
    /** Лимит квестов на одного NPC — согласован 2026-05-25 (research §0.5 п.4). */
    public static final int MAX_QUESTS = 20;
    private boolean questsEnabled = false;
    private final List<com.questnpc.entity.quest.QuestDefinition> quests = new ArrayList<>();

    // --- Per-NPC cache для DistanceToStructureCondition (v2.9.3, runtime-only) ---
    /** TTL кэша ближайшей структуры — 60 секунд (1200 тиков), research §3.7. */
    private static final long STRUCTURE_CACHE_TTL_TICKS = 1200L;
    /** {@code structureId → (BlockPos|null, gameTime)}. Не сохраняется в NBT. */
    private final java.util.Map<net.minecraft.resources.ResourceLocation, CachedStructureInfo>
            nearestStructureCache = new java.util.HashMap<>();

    /** Snapshot ближайшей структуры по типу. {@code pos} = null означает «не найдено в радиусе поиска». */
    private record CachedStructureInfo(@Nullable net.minecraft.core.BlockPos pos, long timestamp) {}

    public boolean isQuestsEnabled() { return questsEnabled; }
    public void setQuestsEnabled(boolean v) { this.questsEnabled = v; }

    public List<com.questnpc.entity.quest.QuestDefinition> getQuests() {
        return Collections.unmodifiableList(quests);
    }

    /** @return true если добавили; false если уже {@link #MAX_QUESTS} квестов на NPC. */
    public boolean addQuest(com.questnpc.entity.quest.QuestDefinition q) {
        if (q == null || quests.size() >= MAX_QUESTS) return false;
        quests.add(q);
        return true;
    }

    public void removeQuest(int index) {
        if (index >= 0 && index < quests.size()) quests.remove(index);
    }

    public void clearQuests() { quests.clear(); }

    /**
     * Полная замена списка квестов (для будущего {@code UpdateNPCQuestsPacket} этапа 2).
     * В этапе 1 не вызывается, но метод существует для готовности сетевой интеграции.
     * Делает deep-copy через NBT save/load, чтобы snapshot не разделял ссылки с UI.
     */
    public void setQuestsFromSnapshot(List<com.questnpc.entity.quest.QuestDefinition> snapshot) {
        quests.clear();
        if (snapshot == null) return;
        for (com.questnpc.entity.quest.QuestDefinition src : snapshot) {
            if (src == null || quests.size() >= MAX_QUESTS) continue;
            quests.add(com.questnpc.entity.quest.QuestDefinition.load(src.save()));
        }
    }

    /**
     * Возвращает позицию ближайшей структуры данного типа от позиции NPC. Cache-aware:
     * результат держится {@link #STRUCTURE_CACHE_TTL_TICKS} тиков, потом перезапрашивается.
     * {@code null} результат — структура не найдена в радиусе поиска (тоже кэшируется).
     *
     * <p>Кэш runtime-only — пуст после перезагрузки мира, регенерируется при первом запросе.
     * Используется в {@link com.questnpc.entity.quest.condition.DistanceToStructureCondition#isMet}.
     */
    public @Nullable net.minecraft.core.BlockPos getNearestStructureCached(
            net.minecraft.resources.ResourceLocation structureId,
            ServerLevel lvl) {
        long now = lvl.getGameTime();
        CachedStructureInfo cached = nearestStructureCache.get(structureId);
        if (cached != null && (now - cached.timestamp) < STRUCTURE_CACHE_TTL_TICKS) {
            return cached.pos; // может быть null — это валидное «не найдено»
        }

        // Cache miss — дорогой запрос
        net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.Structure> registry =
                lvl.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        net.minecraft.world.level.levelgen.structure.Structure structure = registry.get(structureId);
        net.minecraft.core.BlockPos pos;
        if (structure == null) {
            pos = null;
        } else {
            pos = com.questnpc.entity.quest.condition.DistanceToStructureCondition
                    .findNearestStructureDirect(lvl, registry, structure, this.blockPosition());
        }
        nearestStructureCache.put(structureId, new CachedStructureInfo(pos, now));
        return pos;
    }

    public ItemStack getQuestNPCEquipment(int slot) {
        return (slot >= 0 && slot < EQUIPMENT_SLOTS) ? equipment[slot] : ItemStack.EMPTY;
    }

    public void setQuestNPCEquipment(int slot, ItemStack stack) {
        if (slot < 0 || slot >= EQUIPMENT_SLOTS) return;
        equipment[slot] = (stack != null) ? stack : ItemStack.EMPTY;
        applyEquipmentArmor();
    }

    /** Снимок экипировки для передачи на клиент через OpenNPCMenuPacket. */
    public ItemStack[] copyEquipmentSnapshot() {
        ItemStack[] out = new ItemStack[EQUIPMENT_SLOTS];
        for (int i = 0; i < EQUIPMENT_SLOTS; i++) out[i] = equipment[i].copy();
        return out;
    }

    /** Пересчитывает суммарный armor бонус и обновляет AttributeModifier на Attributes.ARMOR. */
    private void applyEquipmentArmor() {
        AttributeInstance attr = this.getAttribute(Attributes.ARMOR);
        if (attr == null) return;
        attr.removeModifier(EQUIPMENT_ARMOR_MODIFIER_UUID);
        int total = 0;
        for (ItemStack s : equipment) {
            if (!s.isEmpty() && s.getItem() instanceof ArmorItem a) {
                total += a.getDefense();
            }
        }
        if (total > 0) {
            attr.addPermanentModifier(new AttributeModifier(
                    EQUIPMENT_ARMOR_MODIFIER_UUID,
                    "QuestNPC Equipment",
                    total,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    @Override
    public void setTradingPlayer(@org.jetbrains.annotations.Nullable Player player) {
        this.tradingPlayer = player;
    }

    @Override
    public @org.jetbrains.annotations.Nullable Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    @Override
    public net.minecraft.world.item.trading.MerchantOffers getOffers() {
        // CRIT-002: загружаем список сделок только один раз, кэш инвалидируется в mobInteract.
        if (this.tradeOffers == null && !this.level().isClientSide && this.tradingPlayer instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {

            net.minecraft.nbt.ListTag nbtOffers = this.getActiveTradeOffers(serverPlayer);
            net.minecraft.world.item.trading.MerchantOffers newOffers = new net.minecraft.world.item.trading.MerchantOffers();

            for (int i = 0; i < nbtOffers.size(); i++) {
                net.minecraft.nbt.CompoundTag tag = nbtOffers.getCompound(i);
                net.minecraft.nbt.CompoundTag vanillaTag = tag.copy();

                // Маппинг QuestNPC-NBT (input1/input2/output) → ванильный MerchantOffer (buy/buyB/sell).
                if (tag.contains("input1")) vanillaTag.put("buy", tag.get("input1"));
                if (tag.contains("input2")) vanillaTag.put("buyB", tag.get("input2"));
                if (tag.contains("output")) vanillaTag.put("sell", tag.get("output"));
                // Sentinel "∞": админский UI пишет maxUses=0, а legacy сделки могут не иметь ключа вовсе.
                // Оба случая означают «без лимита» — транслируем в Integer.MAX_VALUE для ванильного MerchantOffer.
                int rawMax = vanillaTag.getInt("maxUses");
                if (rawMax <= 0) vanillaTag.putInt("maxUses", Integer.MAX_VALUE);

                newOffers.add(new net.minecraft.world.item.trading.MerchantOffer(vanillaTag));
            }
            this.tradeOffers = newOffers;
        }
        return this.tradeOffers != null ? this.tradeOffers : new net.minecraft.world.item.trading.MerchantOffers();
    }

    @Override
    public void overrideOffers(MerchantOffers merchantOffers) {
        // LOW-001 (v2.8.2): vanilla Merchant API requires this. QuestNPC stores offers
        // in its own TradeSets NBT structure (synced via UpdateTradeOffersPacket and
        // NBT save/load), so vanilla overrideOffers is intentionally a no-op.
    }

    @Override
    public void notifyTrade(net.minecraft.world.item.trading.MerchantOffer offer) {
        // Шаг 1: Инкремент в "живом" объекте (чтобы визуально блокировалось в текущем открытом интерфейсе)
        offer.increaseUses();

        if (!this.level().isClientSide) {
            // LOW-009 (v2.8.2): расширенный контекст — NPC id, player, items, текущее uses.
            QuestNPCLogger.debug("Trade completed: NPC {} with player {} ({} -> {}), uses={}",
                    this.getId(),
                    this.tradingPlayer != null ? this.tradingPlayer.getName().getString() : "?",
                    offer.getBaseCostA().getItem(),
                    offer.getResult().getItem(),
                    offer.getUses());

            // Шаг 2: Проверяем, что у нас есть кэш сделок
            if (this.tradeOffers != null) {
                // Находим точный порядковый номер текущей сделки в нашем списке
                int index = this.tradeOffers.indexOf(offer);

                // Шаг 3: Если индекс найден, точечно обновляем первоисточник в NBT
                if (index != -1) {
                    if (this.tradingPlayer instanceof net.minecraft.server.level.ServerPlayer sp) {
                        net.minecraft.nbt.ListTag nbtOffers = this.getActiveTradeOffers(sp);

                        // Защита: проверяем, что индекс не выходит за границы списка NBT
                        if (nbtOffers != null && index < nbtOffers.size()) {
                            net.minecraft.nbt.CompoundTag tag = nbtOffers.getCompound(index);

                            // Записываем обновленный uses прямо в NBT-структуру сущности
                            tag.putInt("uses", offer.getUses());
                            QuestNPCLogger.debug("Успешно сохранено в NBT по индексу " + index + ": uses = " + offer.getUses());
                        }
                    }
                } else {
                    QuestNPCLogger.warn("Сделка не найдена в кэше tradeOffers! Изменения не сохранены в NBT.");
                }
            }
        }
    }

    @Override
    public void notifyTradeUpdated(ItemStack itemStack) {

    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int i) {

    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return this.level().isClientSide();
    }

    /**
     * Именованный набор сделок. У одного NPC может быть до {@link #MAX_TRADE_SETS} наборов.
     * Формат содержимого {@code offers} совпадает с legacy {@code TradeOffers}:
     * input1/input2/output/maxUses/refilable/uses.
     */
    public static final class TradeSet {
        public String name;
        public ListTag offers;

        public TradeSet(String name, ListTag offers) {
            this.name = name != null ? name : DEFAULT_TRADE_SET_NAME;
            this.offers = offers != null ? offers : new ListTag();
        }
    }

    // --- GeckoLib ---
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // --- Модель NPC (пустая строка = дефолт VillagerModel, "minecraft:zombie" = зомби, и т.д.) ---
    private static final EntityDataAccessor<String> DATA_MODEL_TYPE =
            SynchedEntityData.defineId(QuestNPCEntity.class, EntityDataSerializers.STRING);

    // --- Синхронизированные данные (доступны на клиенте для дебаг-рендерера) ---

    private static final EntityDataAccessor<Optional<BlockPos>> DATA_BOUND_BLOCK =
            SynchedEntityData.defineId(QuestNPCEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    private static final EntityDataAccessor<Optional<BlockPos>> DATA_TARGET_POS =
            SynchedEntityData.defineId(QuestNPCEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    public QuestNPCEntity(EntityType<? extends QuestNPCEntity> type, Level level) {
        super(type, level);
        // Квестовый NPC никогда не должен исчезать естественным путём —
        // флаг persistenceRequired отключает soft/hard despawn в Mob.checkDespawn().
        // Дублируется override'ом removeWhenFarAway() на случай, если флаг будет сброшен.
        this.setPersistenceRequired();
        QuestNPCLogger.debug(
                "QuestNPCEntity создан: позиция [{}, {}, {}], dimension [{}]",
                (int) this.getX(), (int) this.getY(), (int) this.getZ(),
                level.dimension().location()
        );
    }

    /**
     * Квестовый NPC не должен никогда исчезать при удалении игрока.
     * Override'им vanilla-логику {@link net.minecraft.world.entity.Mob#checkDespawn()},
     * которая по умолчанию для {@code MobCategory.CREATURE} удаляет мобов
     * дальше 32 блоков (soft) и 128 блоков (hard).
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // -------------------------------------------------------------------------
    // Атрибуты
    // -------------------------------------------------------------------------

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D);
    }

    // -------------------------------------------------------------------------
    // Синхронизированные данные
    // -------------------------------------------------------------------------

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_MODEL_TYPE, "");
        this.entityData.define(DATA_BOUND_BLOCK, Optional.empty());
        this.entityData.define(DATA_TARGET_POS, Optional.empty());
    }

    // -------------------------------------------------------------------------
    // Публичный API привязки
    // -------------------------------------------------------------------------

    @Nullable
    public BlockPos getBoundBlockPos() {
        return this.entityData.get(DATA_BOUND_BLOCK).orElse(null);
    }

    public boolean isBound() {
        return this.entityData.get(DATA_BOUND_BLOCK).isPresent();
    }

    public Optional<BlockPos> getTargetPos() {
        return this.entityData.get(DATA_TARGET_POS);
    }

    public void setTargetPos(BlockPos pos) {
        this.entityData.set(DATA_TARGET_POS, Optional.of(pos));
    }

    public void setBoundBlockPos(BlockPos pos) {
        this.entityData.set(DATA_BOUND_BLOCK, Optional.of(pos));
    }

    public static int getPatrolRadius() {
        return PATROL_RADIUS;
    }

    // -------------------------------------------------------------------------
    // Настраиваемые параметры патруля
    // -------------------------------------------------------------------------

    public double getPatrolSpeed() {
        return patrolSpeed;
    }

    /**
     * Устанавливает скорость патруля и применяет к атрибуту MOVEMENT_SPEED.
     */
    public void setPatrolSpeed(double speed) {
        this.patrolSpeed = speed;
        // Применяем к атрибуту — goal использует speedModifier=1.0, поэтому реальная скорость = baseValue
        if (this.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
        }
    }

    public int getPatrolDelayMin() {
        return patrolDelayMin;
    }

    public int getPatrolDelayMax() {
        return patrolDelayMax;
    }

    /**
     * Устанавливает диапазон задержки перед прогулкой (в секундах).
     * Обновляет BoundedStrollGoal если он активен.
     */
    public void setPatrolDelay(int minSeconds, int maxSeconds) {
        this.patrolDelayMin = minSeconds;
        this.patrolDelayMax = maxSeconds;
        if (boundedStrollGoal != null) {
            boundedStrollGoal.setDelayRange(minSeconds * 20, maxSeconds * 20);
        }
    }

    // -------------------------------------------------------------------------
    // Торговля
    // -------------------------------------------------------------------------

    public boolean getTradingEnabled() {
        return tradingEnabled;
    }

    public void setTradingEnabled(boolean enabled) {
        this.tradingEnabled = enabled;
    }

    /**
     * Возвращает список наборов сделок. Гарантированно содержит хотя бы один набор
     * (если NPC никогда не настраивался — возвращает пустой список; вызывающий код
     * должен быть готов к этому или использовать {@link #getFirstTradeSet()}).
     */
    public List<TradeSet> getTradeSets() {
        return tradeSets;
    }

    /**
     * Заменяет текущие наборы сделок. Клампится до {@link #MAX_TRADE_SETS}.
     * Если на входе пусто — создаётся один набор с именем "Default".
     */
    public void setTradeSets(List<TradeSet> sets) {
        tradeSets.clear();
        if (sets != null) {
            int n = Math.min(sets.size(), MAX_TRADE_SETS);
            for (int i = 0; i < n; i++) {
                TradeSet s = sets.get(i);
                if (s == null) continue;
                tradeSets.add(new TradeSet(s.name, s.offers));
            }
        }
        if (tradeSets.isEmpty()) {
            tradeSets.add(new TradeSet(DEFAULT_TRADE_SET_NAME, new ListTag()));
        }
    }

    @Nullable
    public TradeSet getTradeSetByName(String name) {
        if (name == null) return null;
        for (TradeSet s : tradeSets) {
            if (name.equals(s.name)) return s;
        }
        return null;
    }

    @Nullable
    public TradeSet getFirstTradeSet() {
        return tradeSets.isEmpty() ? null : tradeSets.get(0);
    }

    // -------------------------------------------------------------------------
    // Расписание
    // -------------------------------------------------------------------------

    public List<ScheduleEntry> getSchedule() {
        return schedule;
    }

    public void setSchedule(List<ScheduleEntry> entries) {
        schedule.clear();
        if (entries != null) {
            int n = Math.min(entries.size(), MAX_SCHEDULE_ENTRIES);
            for (int i = 0; i < n; i++) {
                ScheduleEntry e = entries.get(i);
                if (e != null) schedule.add(e);
            }
        }
        // v2.5.4 (BUG-006): останавливаем навигацию, чтобы NPC не продолжал идти к точке удалённого слота.
        if (!this.level().isClientSide) {
            this.getNavigation().stop();
        }
    }

    public boolean isScheduleEnabled() {
        return scheduleEnabled;
    }

    // --- Клиентская копия (только для debug-рендера) ---

    public List<ScheduleEntry> getClientSchedule() {
        return clientSchedule;
    }

    public void setClientSchedule(List<ScheduleEntry> entries) {
        this.clientSchedule = entries != null ? entries : new ArrayList<>();
    }

    public boolean isClientScheduleEnabled() {
        return clientScheduleEnabled;
    }

    public void setClientScheduleEnabled(boolean enabled) {
        this.clientScheduleEnabled = enabled;
    }

    public void setScheduleEnabled(boolean enabled) {
        this.scheduleEnabled = enabled;
        // v2.5.4 (BUG-006): останавливаем навигацию при смене состояния расписания.
        if (!this.level().isClientSide) {
            this.getNavigation().stop();
        }
    }

    /**
     * Возвращает активный слот расписания, соответствующий текущему игровому времени,
     * либо {@code null}, если расписание выключено или нет подходящих слотов.
     */
    @Nullable
    public ScheduleEntry getActiveScheduleEntry() {
        if (!scheduleEnabled || schedule.isEmpty()) return null;
        int timeOfDay = (int) (this.level().getDayTime() % 24000L);
        if (timeOfDay < 0) timeOfDay += 24000;
        for (ScheduleEntry e : schedule) {
            if (e.containsTime(timeOfDay)) return e;
        }
        return null;
    }

    /**
     * Возвращает список сделок, соответствующий активному слоту расписания.
     * Если расписание выключено или активный слот не связан с торговлей — возвращает
     * сделки первого набора. Нужен как точка входа для будущего игрового экрана торговли.
     */
    public ListTag getActiveTradeOffers(@Nullable net.minecraft.server.level.ServerPlayer player) {
        ScheduleEntry active = getActiveScheduleEntry();
        if (active != null) {
            String setName = null;
            if (active.type == ScheduleEntry.Type.TRADE) {
                setName = active.tradeSet;
            } else if (active.type == ScheduleEntry.Type.ACTIVITY && active.interactTrade) {
                setName = active.interactTradeSet;
            }
            if (setName != null && !setName.isEmpty()) {
                TradeSet s = getTradeSetByName(setName);
                if (s != null) return s.offers;
            }
        }
        TradeSet first = getFirstTradeSet();
        return first != null ? first.offers : new ListTag();
    }

    /**
     * Заглушка под воспроизведение анимаций из слотов расписания.
     * Полноценная интеграция с GeckoLib {@code triggerAnim} запланирована на v2.6.
     */
    public void playScheduleAnimation(String animName) {
        // v2.5.0: placeholder. Триггер GeckoLib-анимации подключим в v2.6
        // вместе с доработкой QuestNPCRenderer / QuestNPCGeoModel.
    }

    // -------------------------------------------------------------------------
    // Модель NPC
    // -------------------------------------------------------------------------

    /**
     * Возвращает ResourceLocation модели NPC как строку.
     * Пустая строка = дефолтная модель (VillagerModel).
     */
    public String getModelEntityType() {
        return this.entityData.get(DATA_MODEL_TYPE);
    }

    /**
     * Устанавливает модель NPC. Пустая строка = дефолт.
     * Также обновляет хитбокс (dimensions) в соответствии с целевым EntityType.
     */
    public void setModelEntityType(String type) {
        this.entityData.set(DATA_MODEL_TYPE, type != null ? type : "");
        updateDimensionsForModel(type);
    }

    /**
     * Обновляет dimensions на основе модели. Вызывается из setModelEntityType и onSyncedDataUpdated.
     */
    private void updateDimensionsForModel(String modelType) {
        EntityDimensions oldDimensions = this.currentDimensions;

        if (modelType == null || modelType.isEmpty()) {
            this.currentDimensions = DEFAULT_DIMENSIONS;
        } else if (modelType.startsWith("custom:")) {
            // Кастомные .geo.json модели — используем дефолтные размеры
            this.currentDimensions = DEFAULT_DIMENSIONS;
        } else {
            ResourceLocation rl = ResourceLocation.tryParse(modelType);
            if (rl != null) {
                EntityType<?> targetType = ForgeRegistries.ENTITY_TYPES.getValue(rl);
                if (targetType != null) {
                    this.currentDimensions = targetType.getDimensions();
                } else {
                    QuestNPCLogger.warn("NPC {}: не удалось получить dimensions для '{}' — fallback на дефолт",
                            this.getId(), modelType);
                    this.currentDimensions = DEFAULT_DIMENSIONS;
                }
            } else {
                QuestNPCLogger.warn("NPC {}: невалидный ResourceLocation '{}' — fallback на дефолт",
                        this.getId(), modelType);
                this.currentDimensions = DEFAULT_DIMENSIONS;
            }
        }

        this.refreshDimensions();

        QuestNPCLogger.debug("NPC {}: хитбокс обновлён {}x{} -> {}x{}",
                this.getId(),
                oldDimensions.width, oldDimensions.height,
                this.currentDimensions.width, this.currentDimensions.height);
    }

    /**
     * Возвращает текущие dimensions (учитывают смену модели).
     */
    public EntityDimensions getCurrentDimensions() {
        return this.currentDimensions;
    }

    // -------------------------------------------------------------------------
    // GeckoLib
    // -------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Контроллер движения: ходьба или idle (используется при кастомных .geo.json моделях)
        controllers.add(new AnimationController<>(this, "Movement", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(DefaultAnimations.WALK);
            }
            return state.setAndContinue(DefaultAnimations.IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    // -------------------------------------------------------------------------
    // Динамические размеры хитбокса
    // -------------------------------------------------------------------------

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.currentDimensions;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        // Пропорционально высоте: 85% от высоты хитбокса (аналогично большинству мобов)
        return dimensions.height * 0.85F;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        // При изменении DATA_MODEL_TYPE на клиенте — обновляем dimensions
        if (DATA_MODEL_TYPE.equals(key)) {
            updateDimensionsForModel(this.entityData.get(DATA_MODEL_TYPE));
        }
    }

    // -------------------------------------------------------------------------
    // Управление патрулём
    // -------------------------------------------------------------------------

    /**
     * Активирует ИИ бродилки (BoundedStrollGoal). Вызывается при назначении точки патруля.
     */
    public void activatePatrol() {
        if (boundedStrollGoal == null) {
            boundedStrollGoal = new BoundedStrollGoal(this,
                    patrolDelayMin * 20, patrolDelayMax * 20);
            this.goalSelector.addGoal(2, boundedStrollGoal);
            BlockPos center = getBoundBlockPos();
            if (center != null) {
                QuestNPCLogger.info("NPC {} активировал ИИ бродилки — центр [{}, {}, {}], радиус {}",
                        this.getId(), center.getX(), center.getY(), center.getZ(), PATROL_RADIUS);
            }
        }
    }

    /**
     * Деактивирует ИИ бродилки и останавливает навигацию.
     */
    public void deactivatePatrol() {
        if (boundedStrollGoal != null) {
            this.goalSelector.removeGoal(boundedStrollGoal);
            boundedStrollGoal = null;
            this.getNavigation().stop();
        }
    }

    // -------------------------------------------------------------------------
    // Синхронизация навигационного пути (сервер → клиент)
    // -------------------------------------------------------------------------

    /**
     * Возвращает узлы пути на клиенте (заполняется через {@link PathSyncPacket}).
     */
    public List<Vec3> getClientPathNodes() {
        return clientPathNodes;
    }

    /**
     * Устанавливает узлы пути на клиенте. Вызывается из {@link PathSyncPacket#handle}.
     */
    public void setClientPathNodes(List<Vec3> nodes) {
        this.clientPathNodes = nodes;
    }

    /**
     * Проверяет на сервере, изменился ли путь, и отправляет PathSyncPacket если да.
     * Вызывается из tick() на сервере.
     */
    private void syncPathToClients() {
        Path path = this.getNavigation().getPath();
        int nodeCount = path != null ? path.getNodeCount() : 0;
        int nextIndex = path != null ? path.getNextNodeIndex() : 0;

        // Отправляем только при смене пути
        if (nodeCount == lastSyncedPathNodeCount && nextIndex == lastSyncedNextNodeIndex) return;
        lastSyncedPathNodeCount = nodeCount;
        lastSyncedNextNodeIndex = nextIndex;

        List<Vec3> nodes;
        if (path == null || nodeCount == 0) {
            nodes = Collections.emptyList();
        } else {
            nodes = new ArrayList<>();
            // Только узлы впереди NPC (от текущего до последнего)
            for (int i = nextIndex; i < nodeCount; i++) {
                var node = path.getNode(i);
                // +0.5 для центрирования, +0.1 по Y чтобы линия была чуть выше земли
                nodes.add(new Vec3(node.x + 0.5, node.y + 0.1, node.z + 0.5));
            }
        }

        ModNetwork.INSTANCE.send(
                PacketDistributor.TRACKING_ENTITY.with(() -> this),
                new PathSyncPacket(this.getId(), nodes)
        );
    }

    // -------------------------------------------------------------------------
    // ИИ-цели — NPC стоит на месте, патруль добавляется динамически
    // -------------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        QuestNPCLogger.debug("QuestNPCEntity.registerGoals() — регистрация ИИ-целей");
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ScheduleFollowGoal(this));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    // -------------------------------------------------------------------------
    // Tick — предохранитель от выхода за пределы зоны
    // -------------------------------------------------------------------------

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        // Проверка на палку (админку) — оставляем PASS, чтобы админ-панель открывалась как раньше
        if (player.getItemInHand(hand).is(net.minecraft.world.item.Items.STICK)) {
            return InteractionResult.PASS;
        }

        // ОТКРЫТИЕ МЕНЮ ДЛЯ ИГРОКА
        // На КЛИЕНТЕ мы открываем наш новый экран-выбор (Хаб)
        if (this.level().isClientSide) {
            net.minecraft.client.Minecraft.getInstance().setScreen(
                    new com.questnpc.client.gui.NPCInteractionScreen(this)
            );
            return InteractionResult.SUCCESS;
        }

        // На СЕРВЕРЕ мы запускаем твою старую логику (проверка расписания, сброс кэша, блокировка NPC),
        // но НЕ вызываем openTradingScreen здесь! Мы вызовем его, когда игрок нажмет на кнопку.
        if (this.getTradingEnabled()) {
            // Проверка расписания
            if (this.isScheduleEnabled()) {
                com.questnpc.entity.schedule.ScheduleEntry active = this.getActiveScheduleEntry();
                boolean canTradeNow = active != null && (
                        active.type == com.questnpc.entity.schedule.ScheduleEntry.Type.TRADE ||
                                (active.type == com.questnpc.entity.schedule.ScheduleEntry.Type.ACTIVITY && active.interactTrade)
                );
                if (!canTradeNow) return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            // NPC занят другим игроком
            if (this.getTradingPlayer() != null && this.getTradingPlayer() != player) {
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            // Подготавливаем данные на сервере (запоминаем игрока, чистим кэш), чтобы они были готовы
            this.tradeOffers = null;
            this.setTradingPlayer(player);
        }

        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /**
     * Этот метод теперь запускает ТОЛЬКО финальное открытие окна торгов на сервере.
     * Мы вызовем его из экрана игрока.
     */
    public void startServerTrading(Player player) {
        if (this.level().isClientSide) return;

        if (this.getTradingEnabled()) {
            // Проверка расписания
            if (this.isScheduleEnabled()) {
                com.questnpc.entity.schedule.ScheduleEntry active = this.getActiveScheduleEntry();
                boolean canTradeNow = active != null && (
                        active.type == com.questnpc.entity.schedule.ScheduleEntry.Type.TRADE ||
                                (active.type == com.questnpc.entity.schedule.ScheduleEntry.Type.ACTIVITY && active.interactTrade)
                );
                if (!canTradeNow) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cТорговец сейчас занят!"), true);
                    return;
                }
            }

            // NPC занят другим игроком
            if (this.getTradingPlayer() != null && this.getTradingPlayer() != player) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cТорговец уже занят другим игроком!"), true);
                return;
            }

            // Обнуляем старый список сделок для пересборки из NBT
            this.tradeOffers = null;

            // 1. Запоминаем игрока
            this.setTradingPlayer(player);

            // 2. Вызываем ванильный метод открытия интерфейса.
            // ВАЖНО: он сам пошлет пакет на клиент для закрытия текущего экрана (нашего Хаба)
            // и открытия окна торговли.
            this.openTradingScreen(player, this.getDisplayName(), 1);

            QuestNPCLogger.debug("Торговля успешно открыта через Хаб для {}", player.getName().getString());
        }
    }

    /**
     * Stage 5 (v2.9.4): server-side entry-point для player-side quest UI.
     * Вызывается из {@link com.questnpc.network.OpenNPCQuestsPacket#handle} когда игрок
     * кликает «Квесты» в хабе. Собирает три списка квестов и шлёт {@link com.questnpc.network.OpenPlayerQuestListPacket}.
     *
     * <p>Decision §5 (2026-05-25): once-per-player — completed квесты исключаются.
     */
    public void startServerQuestInteraction(net.minecraft.world.entity.player.Player player) {
        if (this.level().isClientSide) return;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        if (!this.questsEnabled || this.quests.isEmpty()) return;

        net.minecraft.core.BlockPos npcPos = this.blockPosition();
        final com.questnpc.entity.QuestNPCEntity self = this;

        sp.getCapability(com.questnpc.capability.PlayerQuestProvider.CAP).ifPresent(prog -> {
            java.util.List<com.questnpc.network.QuestSnapshots.QuestSnapshot> offerable = new java.util.ArrayList<>();
            java.util.List<com.questnpc.network.QuestSnapshots.QuestProgressSnapshot> active = new java.util.ArrayList<>();
            java.util.List<com.questnpc.network.QuestSnapshots.QuestSnapshot> turnInReady = new java.util.ArrayList<>();

            for (com.questnpc.entity.quest.QuestDefinition q : this.quests) {
                com.questnpc.capability.QuestKey key = new com.questnpc.capability.QuestKey(self.getUUID(), q.getId());

                if (prog.isCompleted(key)) continue; // once-per-player

                if (prog.isActive(key)) {
                    if (q.isReadyToTurnIn(prog, key, sp)) {
                        turnInReady.add(buildSnapshot(q));
                    } else {
                        active.add(buildProgressSnapshot(q, prog, key));
                    }
                } else if (q.canOfferTo(sp, npcPos, self)) {
                    offerable.add(buildSnapshot(q));
                }
            }

            com.questnpc.network.ModNetwork.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                    new com.questnpc.network.OpenPlayerQuestListPacket(
                            self.getId(),
                            self.getDisplayName().getString(),
                            offerable, active, turnInReady));
        });
    }

    private static com.questnpc.network.QuestSnapshots.QuestSnapshot buildSnapshot(com.questnpc.entity.quest.QuestDefinition q) {
        java.util.List<String> objs = new java.util.ArrayList<>();
        for (com.questnpc.entity.quest.QuestObjective o : q.getObjectives()) {
            objs.add(com.questnpc.events.QuestChatHelper.describeObjective(o));
        }
        java.util.List<String> rewards = new java.util.ArrayList<>();
        for (com.questnpc.entity.quest.QuestReward r : q.getRewards()) {
            rewards.add(com.questnpc.events.QuestChatHelper.describeReward(r));
        }
        return new com.questnpc.network.QuestSnapshots.QuestSnapshot(
                q.getId(), q.getTitle(), q.getDescription(), objs, rewards);
    }

    private static com.questnpc.network.QuestSnapshots.QuestProgressSnapshot buildProgressSnapshot(
            com.questnpc.entity.quest.QuestDefinition q,
            com.questnpc.capability.PlayerQuestProgress prog,
            com.questnpc.capability.QuestKey key) {
        java.util.List<com.questnpc.network.QuestSnapshots.ObjectiveProgressSnapshot> objs = new java.util.ArrayList<>();
        for (com.questnpc.entity.quest.QuestObjective o : q.getObjectives()) {
            // BringObjective шлёт «текущее по инвентарю» — но мы тут не имеем player'a в этом helper'е.
            // Для simplicity: если Bring — текущее = 0, max = count; UI просто покажет «0/N». Реальный прогресс
            // Bring проверяется в момент сдачи через isReadyToTurnIn.
            long current;
            if (o instanceof com.questnpc.entity.quest.objective.BringObjective) {
                current = 0L; // Bring не отслеживается через progress map
            } else {
                current = prog.getProgress(key, o.getId());
            }
            objs.add(new com.questnpc.network.QuestSnapshots.ObjectiveProgressSnapshot(
                    com.questnpc.events.QuestChatHelper.describeObjective(o),
                    current,
                    o.getMaxProgress()));
        }
        java.util.List<String> rewards = new java.util.ArrayList<>();
        for (com.questnpc.entity.quest.QuestReward r : q.getRewards()) {
            rewards.add(com.questnpc.events.QuestChatHelper.describeReward(r));
        }
        return new com.questnpc.network.QuestSnapshots.QuestProgressSnapshot(
                q.getId(), q.getTitle(), q.getDescription(), objs, rewards);
    }

    public void restockAllTrades() {
        for (TradeSet set : this.tradeSets) {
            for (int i = 0; i < set.offers.size(); i++) {
                net.minecraft.nbt.CompoundTag tag = set.offers.getCompound(i);
                // Если refilable не указан (старые торги) или он равен true
                if (!tag.contains("refilable") || tag.getBoolean("refilable")) {
                    tag.putInt("uses", 0); // Обнуляем использование
                }
            }
        }
        this.tradeOffers = null; // Инвалидируем кэш
        QuestNPCLogger.debug("NPC " + this.getId() + " пополнил свои запасы!");
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;

        // CRIT-002: чистим tradingPlayer когда игрок реально перестал торговать
        // (закрыл экран, умер, разлогинился, ушёл за 8 блоков).
        if (this.tradingPlayer != null) {
            boolean stillTrading = this.tradingPlayer.isAlive()
                    && this.tradingPlayer.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu
                    && this.tradingPlayer.distanceToSqr(this) < 64.0;
            if (!stillTrading) {
                this.tradingPlayer = null;
                this.tradeOffers = null;
            }
        }

        // ================================================================
        // УМНАЯ ЛОГИКА ПОПОЛНЕНИЯ (Защита от /time set)
        // ================================================================
        long currentTime = this.level().getDayTime();

        if (this.lastTick == -1) {
            // Первый запуск (или после обновления): просто запоминаем текущее время
            this.lastTick = currentTime;
        } else {
            // Ситуация 1: Время пошло вспять (кто-то написал /time set 0 или /time set day)
            boolean timeWentBackwards = currentTime < this.lastTick;

            // Ситуация 2: Наступил новый день естественно (или через /time add 1d)
            boolean newDayStarted = (currentTime / 24000L) > (this.lastTick / 24000L);

            // Если произошло любое из этих событий — сбрасываем лимиты торгов
            if (timeWentBackwards || newDayStarted) {
                this.restockAllTrades();
                QuestNPCLogger.debug("Торги обновлены! Время вспять: " + timeWentBackwards + ", Новый день: " + newDayStarted);
            }

            // ВАЖНО: Всегда обновляем память актуальным временем
            this.lastTick = currentTime;
        }
        // ================================================================

        // Синхронизация пути с клиентами для debug-визуализации
        syncPathToClients();

        if (!isBound()) return;

        // Safety net не должен мешать расписанию
        if (isScheduleEnabled() && getActiveScheduleEntry() != null) return;

        BlockPos center = getBoundBlockPos();
        double limitSq = (PATROL_RADIUS * 1.5) * (PATROL_RADIUS * 1.5);
        if (this.distanceToSqr(Vec3.atCenterOf(center)) > limitSq) {
            this.getNavigation().moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 1.0D);
        }
    }

    // -------------------------------------------------------------------------
    // NBT
    // -------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        // === НОВЫЙ КОД (Таймер торгов) ===
        tag.putLong("LastTick", this.lastTick);
        // ==================================

        BlockPos bound = getBoundBlockPos();
        if (bound != null) {
            tag.putInt("BoundBlockX", bound.getX());
            tag.putInt("BoundBlockY", bound.getY());
            tag.putInt("BoundBlockZ", bound.getZ());
            QuestNPCLogger.debug(
                    "NPC {}: сохранён привязанный блок [{}, {}, {}]",
                    this.getId(), bound.getX(), bound.getY(), bound.getZ()
            );
        }
        // Настройки патруля
        tag.putDouble("PatrolSpeed", patrolSpeed);
        tag.putInt("PatrolDelayMin", patrolDelayMin);
        tag.putInt("PatrolDelayMax", patrolDelayMax);

        // Модель NPC
        String modelType = getModelEntityType();
        if (!modelType.isEmpty()) {
            tag.putString("ModelEntityType", modelType);
        }

        // Торговля — новый формат: список именованных наборов
        tag.putBoolean("TradingEnabled", tradingEnabled);
        if (!tradeSets.isEmpty()) {
            ListTag setsTag = new ListTag();
            for (TradeSet set : tradeSets) {
                CompoundTag setTag = new CompoundTag();
                setTag.putString("Name", set.name != null ? set.name : DEFAULT_TRADE_SET_NAME);
                setTag.put("Offers", set.offers != null ? set.offers : new ListTag());
                setsTag.add(setTag);
            }
            tag.put("TradeSets", setsTag);
        }

        // Расписание
        tag.putBoolean("ScheduleEnabled", scheduleEnabled);
        if (!schedule.isEmpty()) {
            ListTag scheduleTag = new ListTag();
            for (ScheduleEntry e : schedule) {
                scheduleTag.add(e.save());
            }
            tag.put("Schedule", scheduleTag);
        }

        // Экипировка (v2.8.0): кастомный CompoundTag, НЕ vanilla armorItems.
        boolean anyEquipped = false;
        for (ItemStack s : equipment) if (!s.isEmpty()) { anyEquipped = true; break; }
        if (anyEquipped) {
            CompoundTag eq = new CompoundTag();
            String[] keys = {"Head", "Chest", "Legs", "Feet"};
            for (int i = 0; i < EQUIPMENT_SLOTS; i++) {
                if (!equipment[i].isEmpty()) {
                    eq.put(keys[i], equipment[i].save(new CompoundTag()));
                }
            }
            tag.put("Equipment", eq);
        }

        // Квесты (v2.9.0 foundation) — research §3.4.
        tag.putBoolean("QuestsEnabled", questsEnabled);
        if (!quests.isEmpty()) {
            ListTag questsList = new ListTag();
            for (com.questnpc.entity.quest.QuestDefinition q : quests) {
                questsList.add(q.save());
            }
            tag.put("Quests", questsList);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        // === НОВЫЙ КОД (Таймер торгов) ===
        if (tag.contains("LastTick")) {
            this.lastTick = tag.getLong("LastTick");
        } else {
            this.lastTick = -1;
        }
        // ==================================

        // Настройки патруля (загружаем перед activatePatrol, чтобы goal получил правильные значения)
        if (tag.contains("PatrolSpeed")) {
            patrolSpeed = tag.getDouble("PatrolSpeed");
        }
        if (tag.contains("PatrolDelayMin")) {
            patrolDelayMin = tag.getInt("PatrolDelayMin");
        }
        if (tag.contains("PatrolDelayMax")) {
            patrolDelayMax = tag.getInt("PatrolDelayMax");
        }
        // Применяем скорость к атрибуту
        if (this.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(patrolSpeed);
        }

        // Модель NPC
        if (tag.contains("ModelEntityType")) {
            setModelEntityType(tag.getString("ModelEntityType"));
        }

        // Торговля — читаем новый формат, а при его отсутствии мигрируем v2.4.1 TradeOffers
        if (tag.contains("TradingEnabled")) {
            tradingEnabled = tag.getBoolean("TradingEnabled");
        }
        tradeSets.clear();
        if (tag.contains("TradeSets", Tag.TAG_LIST)) {
            ListTag setsTag = tag.getList("TradeSets", Tag.TAG_COMPOUND);
            for (int i = 0; i < setsTag.size() && i < MAX_TRADE_SETS; i++) {
                CompoundTag setTag = setsTag.getCompound(i);
                String name = setTag.getString("Name");
                if (name.isEmpty()) name = DEFAULT_TRADE_SET_NAME;
                ListTag setOffers = setTag.getList("Offers", Tag.TAG_COMPOUND);
                tradeSets.add(new TradeSet(name, setOffers));
            }
        } else if (tag.contains("TradeOffers", Tag.TAG_LIST)) {
            ListTag legacy = tag.getList("TradeOffers", Tag.TAG_COMPOUND);
            tradeSets.add(new TradeSet(DEFAULT_TRADE_SET_NAME, legacy));
            QuestNPCLogger.info("NPC {}: мигрированы {} сделок из legacy TradeOffers в набор '{}'",
                    this.getId(), legacy.size(), DEFAULT_TRADE_SET_NAME);
        }
        if (tradeSets.isEmpty()) {
            tradeSets.add(new TradeSet(DEFAULT_TRADE_SET_NAME, new ListTag()));
        }

        // Расписание
        schedule.clear();
        if (tag.contains("ScheduleEnabled")) {
            scheduleEnabled = tag.getBoolean("ScheduleEnabled");
        }
        if (tag.contains("Schedule", Tag.TAG_LIST)) {
            ListTag scheduleTag = tag.getList("Schedule", Tag.TAG_COMPOUND);
            for (int i = 0; i < scheduleTag.size() && i < MAX_SCHEDULE_ENTRIES; i++) {
                schedule.add(ScheduleEntry.load(scheduleTag.getCompound(i)));
            }
        }

        QuestNPCLogger.debug(
                "NPC {}: загружены настройки из NBT: speed={}, delay={}-{}с, model={}",
                this.getId(), patrolSpeed, patrolDelayMin, patrolDelayMax, getModelEntityType()
        );

        if (tag.contains("BoundBlockX")) {
            BlockPos bound = new BlockPos(
                    tag.getInt("BoundBlockX"),
                    tag.getInt("BoundBlockY"),
                    tag.getInt("BoundBlockZ")
            );
            this.entityData.set(DATA_BOUND_BLOCK, Optional.of(bound));
            QuestNPCLogger.debug(
                    "NPC {}: загружен привязанный блок [{}, {}, {}]",
                    this.getId(), bound.getX(), bound.getY(), bound.getZ()
            );
            // Восстановление патруля после загрузки мира
            activatePatrol();
        }

        // Экипировка (v2.8.0): читаем из кастомного CompoundTag.
        for (int i = 0; i < EQUIPMENT_SLOTS; i++) equipment[i] = ItemStack.EMPTY;
        if (tag.contains("Equipment", Tag.TAG_COMPOUND)) {
            CompoundTag eq = tag.getCompound("Equipment");
            String[] keys = {"Head", "Chest", "Legs", "Feet"};
            for (int i = 0; i < EQUIPMENT_SLOTS; i++) {
                if (eq.contains(keys[i], Tag.TAG_COMPOUND)) {
                    equipment[i] = ItemStack.of(eq.getCompound(keys[i]));
                }
            }
        }
        applyEquipmentArmor();

        // Квесты (v2.9.0 foundation) — research §3.4.
        if (tag.contains("QuestsEnabled")) {
            this.questsEnabled = tag.getBoolean("QuestsEnabled");
        }
        quests.clear();
        if (tag.contains("Quests", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Quests", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && i < MAX_QUESTS; i++) {
                try {
                    com.questnpc.entity.quest.QuestDefinition q =
                            com.questnpc.entity.quest.QuestDefinition.load(list.getCompound(i));
                    if (q != null) quests.add(q);
                } catch (Exception ex) {
                    QuestNPCLogger.warn("NPC {}: failed to load quest at index {}: {}",
                            this.getId(), i, ex.getMessage());
                }
            }
        }
    }
}
