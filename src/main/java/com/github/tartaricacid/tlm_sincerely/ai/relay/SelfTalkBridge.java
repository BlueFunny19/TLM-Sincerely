package com.github.tartaricacid.tlm_sincerely.ai.relay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 「Self Talk」（{@code maid_self_talk}）联动桥：把女仆的转告请求交给对方的互聊流程。
 *
 * <p>本模组对 Self Talk 是<b>可选</b>依赖，且对方未发布 maven 仓库，因此这里保持零编译依赖：
 * 仅在运行期检测其已加载后，用反射调用约定好的 API
 * {@code com.maidmod.selftalk.api.SelfTalkApi#relayToMaid(EntityMaid from, EntityMaid to, String text)}
 * 与 {@code com.maidmod.selftalk.api.SelfTalkApi#isAvailable()}。
 *
 * <p>约定：两个方法都是 {@code public static}，必须由服务端主线程调用
 * （调用点见 {@code TellMaidTool}，经 {@code LLMCallback.runOnServerThread} 保证）。
 * 反射结果（含“类不存在”）会被缓存，避免每次调用都做类查找；任何失败都只返回 false，
 * 绝不抛异常、绝不崩服。
 */
public final class SelfTalkBridge {
    private static final Logger LOGGER = LogManager.getLogger("TLM_Sincerely/SelfTalkBridge");

    private static final String TARGET_MOD_ID = "maid_self_talk";
    private static final String API_CLASS = "com.maidmod.selftalk.api.SelfTalkApi";
    private static final String RELAY_METHOD = "relayToMaid";
    private static final String AVAILABLE_METHOD = "isAvailable";

    private static volatile boolean resolved;
    /** 熔断：以下三者为 null 表示降级，不再尝试反射。 */
    private static volatile Class<?> apiClass;
    private static volatile Method relayMethod;
    private static volatile Method availableMethod;
    /** relayToMaid 的返回类型是否为 void（约定为 boolean，但容忍 void 实现）。 */
    private static volatile boolean relayReturnsVoid;

    private SelfTalkBridge() {
    }

    /**
     * 把 {@code text} 转告给 {@code to}，由对方自行回应。
     *
     * @return true 表示对方接收了本次转告；false 表示未安装、API 不匹配、对方拒绝或调用失败
     */
    public static boolean relayToMaid(EntityMaid from, EntityMaid to, String text) {
        if (from == null || to == null || text == null || text.isEmpty()) {
            return false;
        }
        Method method = resolveRelayMethod();
        if (method == null) {
            return false;
        }
        try {
            Object result = method.invoke(null, from, to, text);
            if (relayReturnsVoid) {
                return true;
            }
            return result instanceof Boolean value && value;
        } catch (Throwable throwable) {
            // 对方内部异常（含对方未开启互聊、链路忙等）不应影响本模组
            LOGGER.warn("Self Talk relayToMaid failed for {} -> {}",
                    from.getUUID(), to.getUUID(), throwable);
            return false;
        }
    }

    /**
     * 对方 API 是否可用（已加载 + {@code isAvailable()} 为 true）。
     * 未加载时返回 false 且不记日志。
     */
    public static boolean isAvailable() {
        if (!ModList.get().isLoaded(TARGET_MOD_ID)) {
            return false;
        }
        if (!resolveApi()) {
            return false;
        }
        Method method = resolveAvailableMethod();
        if (method == null) {
            // isAvailable 不是硬需求：API 类存在即视为可用，relayToMaid 的缺失由 relayToMaid 自己降级
            return true;
        }
        try {
            Object result = method.invoke(null);
            return result instanceof Boolean value ? value : result != null;
        } catch (Throwable throwable) {
            LOGGER.warn("Self Talk isAvailable() call failed", throwable);
            return false;
        }
    }

    /** Self Talk 模组是否已加载（不做任何 API 解析）。 */
    public static boolean isModLoaded() {
        return ModList.get().isLoaded(TARGET_MOD_ID);
    }

    private static Method resolveRelayMethod() {
        Method method = relayMethod;
        if (method != null) {
            return method;
        }
        if (!resolveApi()) {
            return null;
        }
        synchronized (SelfTalkBridge.class) {
            if (relayMethod != null) {
                return relayMethod;
            }
            try {
                Method found = apiClass.getMethod(RELAY_METHOD, EntityMaid.class, EntityMaid.class, String.class);
                if (!Modifier.isStatic(found.getModifiers())) {
                    LOGGER.warn("Self Talk {}#{}(EntityMaid,EntityMaid,String) is not static; relay disabled",
                            API_CLASS, RELAY_METHOD);
                    return null;
                }
                found.setAccessible(true);
                relayReturnsVoid = found.getReturnType() == void.class;
                relayMethod = found;
                return found;
            } catch (NoSuchMethodException e) {
                LOGGER.warn("Self Talk {} does not expose {}#(EntityMaid,EntityMaid,String); relay disabled ({})",
                        TARGET_MOD_ID, API_CLASS, RELAY_METHOD, e.getMessage());
                return null;
            } catch (Throwable throwable) {
                LOGGER.warn("Failed to resolve Self Talk relay API; relay disabled", throwable);
                return null;
            }
        }
    }

    private static Method resolveAvailableMethod() {
        Method method = availableMethod;
        if (method != null) {
            return method;
        }
        if (!resolveApi()) {
            return null;
        }
        synchronized (SelfTalkBridge.class) {
            if (availableMethod != null) {
                return availableMethod;
            }
            try {
                Method found = apiClass.getMethod(AVAILABLE_METHOD);
                found.setAccessible(true);
                availableMethod = found;
                return found;
            } catch (NoSuchMethodException e) {
                // isAvailable 不是硬需求：relayToMaid 存在即认为 API 可用
                LOGGER.warn("Self Talk {} has no {}#(); treating relay API as available ({})",
                        TARGET_MOD_ID, AVAILABLE_METHOD, e.getMessage());
                return null;
            } catch (Throwable throwable) {
                LOGGER.warn("Failed to resolve Self Talk availability API", throwable);
                return null;
            }
        }
    }

    /** 解析一次 API 类；类不存在时记一次 warn 并永久降级。 */
    private static boolean resolveApi() {
        if (resolved) {
            return apiClass != null;
        }
        synchronized (SelfTalkBridge.class) {
            if (resolved) {
                return apiClass != null;
            }
            resolved = true;
            if (!ModList.get().isLoaded(TARGET_MOD_ID)) {
                // 未安装是正常状态，不记 warn
                return false;
            }
            Class<?> loaded;
            try {
                loaded = Class.forName(API_CLASS);
            } catch (ClassNotFoundException e) {
                LOGGER.warn("{} is loaded but {} is missing; the tell_maid relay is degraded",
                        TARGET_MOD_ID, API_CLASS);
                return false;
            } catch (Throwable throwable) {
                LOGGER.warn("Failed to load Self Talk API class {}", API_CLASS, throwable);
                return false;
            }
            apiClass = loaded;
            return true;
        }
    }
}
