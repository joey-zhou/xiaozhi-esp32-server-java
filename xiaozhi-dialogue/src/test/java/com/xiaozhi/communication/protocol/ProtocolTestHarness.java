package com.xiaozhi.communication.protocol;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.ai.llm.service.IntentService;
import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.DeviceRegistry;
import com.xiaozhi.communication.common.InstanceIdHolder;
import com.xiaozhi.communication.common.MessageHandler;
import com.xiaozhi.communication.common.RedisBroadcast;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.communication.server.websocket.WebSocketHandler;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.dialogue.DialogueService;
import com.xiaozhi.dialogue.llm.factory.PersonaFactory;
import com.xiaozhi.dialogue.llm.tool.device.IotService;
import com.xiaozhi.dialogue.llm.tool.mcp.device.DeviceMcpService;
import com.xiaozhi.dialogue.playback.OpusRecorder;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.ScheduledPlayer;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * 设备协议回归套件的装配器：把真实的 WebSocketHandler / MessageHandler / SessionManager /
 * DialogueService / MessageSender / ScheduledPlayer 与各路假体接成一张对象图，
 * 让用例作者只写「驱动 + 断言」，不用关心 17 个字段注入怎么塞。
 *
 * <p>典型用法：
 * <pre>{@code
 * ProtocolTestHarness harness = ProtocolTestHarness.create();
 * FakeDevice device = harness.connect("94:a9:90:2b:dd:18");
 * device.hello();
 * assertThat(device.transport().jsonSignatures()).containsExactly("hello");
 * }</pre>
 *
 * <p>哪些是真的：状态机、消息编解码、会话注册表、音频流生命周期、播放调度、意图判定，
 * 这些正是协议回归要钉的东西，全部走生产代码。
 *
 * <p>哪些是假体（对应盲区，用例不要在这些维度上断言）：
 * <ul>
 *   <li>{@link ScriptedVadService} —— 断句由帧首字节脚本决定，不测 VAD 算法质量；</li>
 *   <li>{@link RecordingAecService} —— 只记参考帧调度契约，不测 ERLE / 估计延迟；</li>
 *   <li>{@link ScriptedSttService} —— 识别结果预置，不测真实分段与标点；</li>
 *   <li>{@link RecordingDeviceRepository} —— 内存仓储，不测事务、缓存失效与领域事件；</li>
 *   <li>{@link TestEventBus} —— 反射派发，保证监听器都被调到，但保证不了跨监听器顺序；</li>
 *   <li>{@link FakeWebSocketTransport} —— 同步发送，没有分片、背压与容器级的缓冲区上限；</li>
 *   <li>PersonaFactory / ChatModel / TTS 为 mock，LLM 与语音合成本身不在本套件范围内；</li>
 *   <li>Redis 广播、跨实例注册表为 mock，多实例真实投递语义测不了。</li>
 * </ul>
 *
 * <p>另外注意：协议链路上有多处虚拟线程（设备状态写库、MCP 初始化、STT 启动、唤醒词落盘），
 * 断言一律走 {@link AwaitHelper}，禁止固定时长 sleep 后立即断言。
 * ScheduledPlayer 的时序天生带抖动，只断言「有/无」「顺序」「不重不漏」，不断言精确帧数与时刻。
 */
class ProtocolTestHarness {

    private static final String INSTANCE_ID = "harness-instance";

    // ===== 真实对象 =====
    private final SessionManager sessionManager = new SessionManager();
    private final MessageHandler messageHandler = new MessageHandler();
    private final DialogueService dialogueService = new DialogueService();
    private final WebSocketHandler webSocketHandler = new WebSocketHandler();
    private final IntentService intentService = new IntentService();
    private final InstanceIdHolder instanceIdHolder = new InstanceIdHolder(INSTANCE_ID);
    private final TestEventBus eventBus = new TestEventBus();
    private final MessageSender messageSender = new MessageSender(eventBus.publisher());

    // ===== 假体 =====
    private final ScriptedVadService vadService = new ScriptedVadService();
    private final RecordingAecService aecService = new RecordingAecService();
    private final ScriptedSttService sttService = new ScriptedSttService();
    private final RecordingDeviceRepository deviceRepository = new RecordingDeviceRepository();

    // ===== mock 协作者 =====
    private final DeviceService deviceService = mock(DeviceService.class);
    private final RoleService roleService = mock(RoleService.class);
    private final IotService iotService = mock(IotService.class);
    private final TtsServiceFactory ttsServiceFactory = mock(TtsServiceFactory.class);
    private final ChatModelFactory chatModelFactory = mock(ChatModelFactory.class);
    private final ToolsGlobalRegistry toolsGlobalRegistry = mock(ToolsGlobalRegistry.class);
    private final PersonaFactory personaFactory = mock(PersonaFactory.class);
    private final DeviceMcpService deviceMcpService = mock(DeviceMcpService.class);
    private final StorageServiceFactory storageServiceFactory = mock(StorageServiceFactory.class);
    private final MessageService messageService = mock(MessageService.class);
    private final DeviceRegistry deviceRegistry = mock(DeviceRegistry.class);
    private final RedisBroadcast redisBroadcast = mock(RedisBroadcast.class);

    /** deviceId → 设备档案，connect 时按此决定设备是否已绑定角色 */
    private final Map<String, DeviceBO> deviceProfiles = new ConcurrentHashMap<>();
    private final Map<Integer, RoleBO> roleProfiles = new ConcurrentHashMap<>();
    private final List<FakeDevice> connected = new ArrayList<>();

    private int transportSequence;

    static ProtocolTestHarness create() {
        ProtocolTestHarness harness = new ProtocolTestHarness();
        harness.wire();
        return harness;
    }

    private ProtocolTestHarness() {
    }

    private void wire() {
        inject(sessionManager,
                "applicationContext", eventBus.applicationContext(),
                "deviceRepository", deviceRepository,
                "deviceRegistry", deviceRegistry,
                "instanceIdHolder", instanceIdHolder);

        inject(dialogueService,
                "personaFactory", personaFactory,
                "messageService", messageSender,
                "vadService", vadService,
                "aecService", aecService,
                "sessionManager", sessionManager,
                "intentService", intentService,
                "eventPublisher", eventBus.publisher(),
                "storageServiceFactory", storageServiceFactory);

        inject(messageHandler,
                "deviceService", deviceService,
                "deviceRepository", deviceRepository,
                "vadService", vadService,
                "sessionManager", sessionManager,
                "dialogueService", dialogueService,
                "iotService", iotService,
                "ttsFactory", ttsServiceFactory,
                "personaFactory", personaFactory,
                "chatModelFactory", chatModelFactory,
                "toolsGlobalRegistry", toolsGlobalRegistry,
                "roleService", roleService,
                "applicationContext", eventBus.applicationContext(),
                "messageService", messageSender,
                "aecService", aecService,
                "deviceRegistry", deviceRegistry,
                "instanceIdHolder", instanceIdHolder,
                "redisBroadcast", redisBroadcast);

        inject(webSocketHandler,
                "sessionManager", sessionManager,
                "messageHandler", messageHandler,
                "deviceMcpService", deviceMcpService);

        requireAllInjected(sessionManager);
        requireAllInjected(dialogueService);
        requireAllInjected(messageHandler);
        requireAllInjected(webSocketHandler);

        eventBus.register(sessionManager);
        eventBus.register(dialogueService);
        eventBus.register(vadService);

        stubDefaults();
    }

    private void stubDefaults() {
        lenient().when(deviceService.getBO(anyString()))
                .thenAnswer(invocation -> deviceProfiles.get(invocation.<String>getArgument(0)));
        lenient().when(roleService.getBO(any()))
                .thenAnswer(invocation -> roleProfiles.get(invocation.<Integer>getArgument(0)));
        lenient().when(personaFactory.buildPersona(any(ChatSession.class)))
                .thenAnswer(invocation -> buildPersona(invocation.getArgument(0)));
        lenient().when(personaFactory.buildPersona(any(ChatSession.class), any(), any()))
                .thenAnswer(invocation -> buildPersona(invocation.getArgument(0)));
        roleProfiles.put(defaultRole().getRoleId(), defaultRole());
    }

    /**
     * 默认的 Persona 装配：真实 ScheduledPlayer + 真实 OpusRecorder（参考帧走假 AEC），
     * STT 用脚本化假体，LLM 相关能力留空。用例需要别的 Persona 时重新 stub {@link #personaFactory()}。
     */
    private Persona buildPersona(ChatSession session) {
        if (session.getPersona() != null) {
            return session.getPersona();
        }
        Player player = session.getPlayer();
        if (player == null) {
            player = new ScheduledPlayer(session, messageSender);
            player.setOpusRecorder(new OpusRecorder(session, messageService, aecService, storageServiceFactory));
            session.setPlayer(player);
        }
        Persona persona = Persona.builder()
                .sessionManager(sessionManager)
                .sessionId(session.getSessionId())
                .sttService(sttService)
                .player(player)
                .build();
        session.setPersona(persona);
        return persona;
    }

    // ========== 设备编排 ==========

    /** 登记一台已绑定角色的设备，connect 之前调用；不调用时 connect 会用默认档案 */
    ProtocolTestHarness withBoundDevice(String deviceId, int roleId) {
        DeviceBO device = new DeviceBO();
        device.setDeviceId(deviceId);
        device.setDeviceName("协议测试设备");
        device.setRoleId(roleId);
        device.setUserId(1);
        device.setType("esp32");
        deviceProfiles.put(deviceId, device);
        roleProfiles.computeIfAbsent(roleId, id -> {
            RoleBO role = new RoleBO();
            role.setRoleId(id);
            role.setUserId(1);
            role.setRoleName("协议测试角色");
            return role;
        });
        return this;
    }

    /** 登记一台未绑定角色的设备（roleId 为 null），用于验证码 / 自动绑定分支 */
    ProtocolTestHarness withUnboundDevice(String deviceId) {
        DeviceBO device = new DeviceBO();
        device.setDeviceId(deviceId);
        device.setType("esp32");
        deviceProfiles.put(deviceId, device);
        return this;
    }

    /** 登记一台数据库里查不到的设备（deviceService.getBO 返回 null） */
    ProtocolTestHarness withUnknownDevice(String deviceId) {
        deviceProfiles.remove(deviceId);
        return this;
    }

    // ========== 驱动 ==========

    /** 建立一条 WebSocket 连接，device-id 走握手头。未登记过的设备按已绑定默认角色处理 */
    FakeDevice connect(String deviceId) {
        if (!deviceProfiles.containsKey(deviceId)) {
            withBoundDevice(deviceId, defaultRole().getRoleId());
        }
        FakeWebSocketTransport transport = newTransport().withHandshakeHeader("device-id", deviceId);
        return connect(deviceId, transport);
    }

    /** 用自定义传输建立连接，可编排握手头缺失、device-id 走 query 参数等场景 */
    FakeDevice connect(String deviceId, FakeWebSocketTransport transport) {
        webSocketHandler.afterConnectionEstablished(transport);
        FakeDevice device = new FakeDevice(this, deviceId, transport);
        connected.add(device);
        return device;
    }

    /** 新建一个未连接的传输，id 自增保证同一 harness 内不重复 */
    FakeWebSocketTransport newTransport() {
        return new FakeWebSocketTransport("protocol-session-" + (++transportSequence));
    }

    // ========== 组件访问 ==========

    WebSocketHandler webSocketHandler() {
        return webSocketHandler;
    }

    MessageHandler messageHandler() {
        return messageHandler;
    }

    SessionManager sessionManager() {
        return sessionManager;
    }

    DialogueService dialogueService() {
        return dialogueService;
    }

    MessageSender messageSender() {
        return messageSender;
    }

    IntentService intentService() {
        return intentService;
    }

    ChatSession session(String sessionId) {
        return sessionManager.getSession(sessionId);
    }

    ScriptedVadService vad() {
        return vadService;
    }

    RecordingAecService aec() {
        return aecService;
    }

    ScriptedSttService stt() {
        return sttService;
    }

    RecordingDeviceRepository deviceRepository() {
        return deviceRepository;
    }

    TestEventBus events() {
        return eventBus;
    }

    PersonaFactory personaFactory() {
        return personaFactory;
    }

    DeviceService deviceService() {
        return deviceService;
    }

    RoleService roleService() {
        return roleService;
    }

    IotService iotService() {
        return iotService;
    }

    TtsServiceFactory ttsServiceFactory() {
        return ttsServiceFactory;
    }

    DeviceMcpService deviceMcpService() {
        return deviceMcpService;
    }

    DeviceRegistry deviceRegistry() {
        return deviceRegistry;
    }

    RedisBroadcast redisBroadcast() {
        return redisBroadcast;
    }

    StorageServiceFactory storageServiceFactory() {
        return storageServiceFactory;
    }

    /** 关闭全部连接，@AfterEach 调用，避免播放线程跨用例残留 */
    void shutdown() {
        for (FakeDevice device : connected) {
            ChatSession session = sessionManager.getSession(device.sessionId());
            if (session != null && session.getPlayer() != null) {
                session.getPlayer().stop();
            }
            device.transport().close();
            webSocketHandler.afterConnectionClosed(device.transport(), org.springframework.web.socket.CloseStatus.NORMAL);
        }
        connected.clear();
    }

    private static RoleBO defaultRole() {
        RoleBO role = new RoleBO();
        role.setRoleId(1);
        role.setUserId(1);
        role.setRoleName("协议测试角色");
        return role;
    }

    // ========== 装配与自检 ==========

    private static void inject(Object target, Object... fieldValuePairs) {
        for (int i = 0; i < fieldValuePairs.length; i += 2) {
            ReflectionTestUtils.setField(target, (String) fieldValuePairs[i], fieldValuePairs[i + 1]);
        }
    }

    /**
     * 对所有 @Resource / @Autowired 字段做一次非空自检。
     * MessageHandler 有 17 个字段注入，生产代码新增字段时装配器不会报错只会在用例里 NPE，
     * 自检把这种失败提前到 create() 并直接点名缺哪个字段。
     */
    private static void requireAllInjected(Object bean) {
        List<String> missing = new ArrayList<>();
        for (Class<?> type = bean.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                boolean injected = field.isAnnotationPresent(Resource.class)
                        || field.isAnnotationPresent(Autowired.class);
                if (!injected) {
                    continue;
                }
                if (ReflectionTestUtils.getField(bean, field.getName()) == null) {
                    missing.add(field.getName());
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(bean.getClass().getSimpleName()
                    + " 的注入字段未装配，请在 ProtocolTestHarness#wire 里补上: " + missing);
        }
    }
}
