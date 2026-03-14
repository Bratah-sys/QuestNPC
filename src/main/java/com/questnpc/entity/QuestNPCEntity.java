package com.questnpc.entity;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.ai.BoundedStrollGoal;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.PathSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Квестовый NPC — PathfinderMob с ручной привязкой к точке патруля.
 * По умолчанию стоит на месте. Патруль активируется через меню (палка + спец-палка).
 */
public class QuestNPCEntity extends PathfinderMob {

    /** Радиус патруля вокруг привязанного блока. */
    public static final int PATROL_RADIUS = 16;

    // --- Дефолтные значения настроек ---
    public static final double DEFAULT_PATROL_SPEED = 0.3D;
    public static final int DEFAULT_DELAY_MIN = 3;
    public static final int DEFAULT_DELAY_MAX = 10;

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

    // --- Синхронизированные данные (доступны на клиенте для дебаг-рендерера) ---

    private static final EntityDataAccessor<Optional<BlockPos>> DATA_BOUND_BLOCK =
            SynchedEntityData.defineId(QuestNPCEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    private static final EntityDataAccessor<Optional<BlockPos>> DATA_TARGET_POS =
            SynchedEntityData.defineId(QuestNPCEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    public QuestNPCEntity(EntityType<? extends QuestNPCEntity> type, Level level) {
        super(type, level);
        QuestNPCLogger.debug(
                "QuestNPCEntity создан: позиция [{}, {}, {}], dimension [{}]",
                (int) this.getX(), (int) this.getY(), (int) this.getZ(),
                level.dimension().location()
        );
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

    private void clearBoundBlock() {
        this.entityData.set(DATA_BOUND_BLOCK, Optional.empty());
        this.entityData.set(DATA_TARGET_POS, Optional.empty());
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
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    // -------------------------------------------------------------------------
    // Tick — предохранитель от выхода за пределы зоны
    // -------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;

        // Синхронизация пути с клиентами для debug-визуализации
        syncPathToClients();

        if (!isBound()) return;

        BlockPos center = getBoundBlockPos();
        // Ушёл слишком далеко → срочный возврат к центру
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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

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
        QuestNPCLogger.debug(
                "NPC {}: загружены настройки из NBT: speed={}, delay={}-{}с",
                this.getId(), patrolSpeed, patrolDelayMin, patrolDelayMax
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
    }
}
