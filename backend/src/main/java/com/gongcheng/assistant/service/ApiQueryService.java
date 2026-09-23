package com.gongcheng.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongcheng.assistant.common.GlobalErrorCodeConstants;
import com.gongcheng.assistant.common.ServiceException;
import com.gongcheng.assistant.entity.Member;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 梦游社接口查询服务
 * 负责解析用户输入的链接、调用外部接口、提取角色数据和特殊宝石
 * 使用 JDK 17 原生 HttpClient（内置连接池，并发场景下复用 TCP 连接）
 */
@Slf4j
@Service
public class ApiQueryService {

    /** JSON 解析器（Spring 自动注入，全局复用） */
    private final ObjectMapper objectMapper;

    /** HTTP 客户端（JDK 17 原生，内置连接池，线程安全，全局复用） */
    private final HttpClient httpClient;

    /** 读取超时（毫秒），用于每个请求的 timeout 设置 */
    private final int readTimeoutMillis;

    /** H5 个人主页链接正则：https://h5.docoi.cc/t5Config/homePage/{serverId}?userId=xxx&roleId=xxx */
    private static final Pattern H5_URL_PATTERN = Pattern.compile(
            "https?://h5\\.docoi\\.cc/t5Config/homePage/(\\d+)\\?.*userId=(\\d+).*roleId=(\\d+)",
            Pattern.CASE_INSENSITIVE);

    /** API 接口前缀 */
    private static final String API_PREFIX = "https://api-prod.docoi.cc/circle/v1/server/game/personal_page";

    /** 允许的 API 域名白名单 */
    private static final Set<String> ALLOWED_DOMAINS = Set.of(
            "api-prod.docoi.cc",
            "h5.docoi.cc"
    );

    /** 特殊宝石目标效果（需要展示在名片上） */
    private static final Set<String> TARGET_GEM_EFFECTS = Set.of(
            "攻击满血量怪物时必定造成暴击",
            "枪械子弹碰到墙壁弹射次数+2"
    );

    /**
     * 构造函数
     * 初始化 HttpClient，设置连接超时和读取超时
     * HttpClient 内置连接池，并发请求时自动复用 keep-alive 连接
     *
     * @param objectMapper   JSON 解析器
     * @param connectTimeout 连接超时（毫秒），默认10秒
     * @param readTimeout    读取超时（毫秒），默认30秒
     */
    public ApiQueryService(ObjectMapper objectMapper,
                           @Value("${api.query.connect-timeout:10000}") int connectTimeout,
                           @Value("${api.query.read-timeout:30000}") int readTimeout) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .build();
        // 读取超时通过 HttpRequest.timeout 设置
        this.readTimeoutMillis = readTimeout;
    }

    /**
     * 游戏参数（从链接中解析，用于安全存储和拼接）
     */
    public record GameParams(String serverId, String userId, String roleId) {}

    /**
     * 接口解析结果（强类型，替代 Map 避免装箱拆箱和强转）
     */
    public record ParsedGameData(
            String roleName,
            String progress,
            int atk,
            String gunDmgBonus,
            String critDmgBonus,
            String specialGems
    ) {}

    /**
     * 解析输入链接为游戏参数，用于安全存储
     * 支持两种格式：
     * 1. H5 个人主页链接：https://h5.docoi.cc/t5Config/homePage/{serverId}?userId=xxx&roleId=xxx
     * 2. API 直链：https://api-prod.docoi.cc/circle/v1/server/game/personal_page?server_id=xxx&user_id=xxx&role_id=xxx
     *
     * @param inputUrl 用户输入的链接
     * @return 解析出的游戏参数
     */
    public GameParams parseGameParams(String inputUrl) {
        if (inputUrl == null || inputUrl.trim().isEmpty()) {
            throw new ServiceException(GlobalErrorCodeConstants.API_QUERY_FAILED.getCode(), "链接不能为空");
        }

        String url = inputUrl.trim();

        // 安全校验：域名白名单
        if (!isAllowedDomain(url)) {
            throw new ServiceException(GlobalErrorCodeConstants.API_QUERY_FAILED.getCode(),
                    "链接域名不合法，仅支持梦游社官方链接");
        }

        // 已经是 API 链接，解析参数
        if (url.startsWith("https://api-prod.docoi.cc/") || url.startsWith("http://api-prod.docoi.cc/")) {
            try {
                URI uri = URI.create(url);
                String query = uri.getQuery();
                String serverId = extractQueryParam(query, "server_id");
                String userId = extractQueryParam(query, "user_id");
                String roleId = extractQueryParam(query, "role_id");
                if (serverId != null && userId != null && roleId != null) {
                    return new GameParams(serverId, userId, roleId);
                }
            } catch (Exception e) {
                log.warn("API链接解析失败: {}", e.getMessage());
            }
        }

        // 尝试匹配 H5 链接
        Matcher matcher = H5_URL_PATTERN.matcher(url);
        if (matcher.find()) {
            return new GameParams(matcher.group(1), matcher.group(2), matcher.group(3));
        }

        // 尝试从 H5 链接中提取参数（容错：参数顺序可能不同）
        if (url.contains("h5.docoi.cc/t5Config/homePage/")) {
            try {
                URI uri = URI.create(url);
                String path = uri.getPath();
                String serverId = path.substring(path.lastIndexOf('/') + 1);
                String query = uri.getQuery();
                String userId = extractQueryParam(query, "userId");
                String roleId = extractQueryParam(query, "roleId");
                if (serverId != null && userId != null && roleId != null) {
                    return new GameParams(serverId, userId, roleId);
                }
            } catch (Exception e) {
                log.warn("H5链接容错解析失败: {}", e.getMessage());
            }
        }

        throw new ServiceException(GlobalErrorCodeConstants.API_QUERY_FAILED.getCode(),
                "无法识别的链接格式，请输入H5个人主页链接或数据接口链接");
    }

    /**
     * 根据游戏参数拼接完整 API 链接
     * 参数均为纯数字，直接字符串拼接，无需 URL 编码
     *
     * @param params 游戏参数
     * @return 完整的 API 链接
     */
    public String buildApiUrl(GameParams params) {
        return API_PREFIX + "?server_id=" + params.serverId()
                + "&user_id=" + params.userId()
                + "&role_id=" + params.roleId();
    }

    /**
     * 从 query 字符串中提取指定参数值
     *
     * @param query URL query 字符串
     * @param name  参数名
     * @return 参数值，未找到返回 null
     */
    private String extractQueryParam(String query, String name) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return kv[1];
            }
        }
        return null;
    }

    /**
     * 安全校验：检查 URL 域名是否在白名单内
     *
     * @param url 待校验的 URL
     * @return 是否在白名单内
     */
    private boolean isAllowedDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return false;
            return ALLOWED_DOMAINS.contains(host);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 调用外部接口，解析并提取关键字段
     * 使用 HttpClient 发送 GET 请求，内置连接池复用 TCP 连接
     *
     * @param params 游戏参数
     * @return 解析后的强类型结果
     */
    public ParsedGameData queryAndParse(GameParams params) {
        String apiUrl = buildApiUrl(params);
        log.info("开始查询接口: serverId={}, userId={}, roleId={}", params.serverId(), params.userId(), params.roleId());

        try {
            // 构建 GET 请求，设置读取超时
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofMillis(readTimeoutMillis))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            // 发送请求，响应体为字符串
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null) {
                throw new ServiceException(GlobalErrorCodeConstants.API_QUERY_FAILED.getCode(),
                        "接口返回状态异常: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());

            // 校验业务 code
            int bizCode = root.path("code").asInt(-1);
            if (bizCode != 200) {
                String reason = root.path("reason").asText("未知错误");
                throw new ServiceException(GlobalErrorCodeConstants.API_QUERY_FAILED.getCode(),
                        "接口业务错误: " + reason);
            }

            JsonNode t5Data = root.path("data").path("t5_data");
            if (t5Data.isMissingNode() || t5Data.isNull()) {
                throw new ServiceException(GlobalErrorCodeConstants.API_QUERY_FAILED.getCode(),
                        "接口数据中未找到 t5_data 节点");
            }

            // 提取关键字段
            String roleName = t5Data.path("role_name").asText("");
            String progress = t5Data.path("progress").asText("");
            int atk = t5Data.path("atk").asInt(0);
            String gunDmgBonus = t5Data.path("gun_dmg_bonus").asText("");
            String critDmgBonus = t5Data.path("crit_dmg_bonus").asText("");
            String specialGems = String.join(",", extractSpecialGems(t5Data));

            log.info("解析成功: roleName={}, progress={}, atk={}, specialGems={}",
                    roleName, progress, atk, specialGems);

            return new ParsedGameData(roleName, progress, atk, gunDmgBonus, critDmgBonus, specialGems);

        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("接口查询失败: {}", e.getMessage());
            throw new ServiceException(GlobalErrorCodeConstants.API_QUERY_FAILED.getCode(),
                    "接口查询失败: " + e.getMessage());
        }
    }

    /**
     * 将解析结果填充到 Member 实体
     * 同时存储用户原始输入链接和解析出的游戏参数
     *
     * @param member   成员实体
     * @param inputUrl 用户原始输入的梦游社链接
     */
    public void fillMemberFromApi(Member member, String inputUrl) {
        GameParams params = parseGameParams(inputUrl);
        ParsedGameData parsed = queryAndParse(params);

        // 存储用户原始输入链接
        member.setApiUrl(inputUrl);
        // 存储解析出的游戏参数
        member.setServerId(params.serverId());
        member.setGameUserId(params.userId());
        member.setGameRoleId(params.roleId());

        // 填充游戏数据
        applyParsedData(member, parsed);
    }

    /**
     * 用已存储的游戏参数重新拉取数据（刷新时调用）
     * 不修改 apiUrl 和游戏参数，只更新游戏数据
     *
     * @param member 成员实体（需已包含 serverId/gameUserId/gameRoleId）
     */
    public void refreshMemberFromParams(Member member) {
        GameParams params = new GameParams(member.getServerId(), member.getGameUserId(), member.getGameRoleId());
        ParsedGameData parsed = queryAndParse(params);
        applyParsedData(member, parsed);
    }

    /**
     * 将解析结果填充到 Member 实体的公共方法
     * 被 fillMemberFromApi 和 refreshMemberFromParams 复用，避免重复代码
     *
     * @param member 成员实体
     * @param parsed 解析后的游戏数据
     */
    private void applyParsedData(Member member, ParsedGameData parsed) {
        member.setRoleName(parsed.roleName());
        member.setProgress(parsed.progress());
        member.setAtk(parsed.atk());
        member.setGunDmgBonus(parsed.gunDmgBonus());
        member.setCritDmgBonus(parsed.critDmgBonus());
        member.setSpecialGems(parsed.specialGems());
    }

    /**
     * 遍历 equip_plans → equips → gems，筛选出目标效果的特殊宝石。
     * JsonNode.forEach 对缺失/非数组节点为空操作，无需手动判空。
     * 使用 LinkedHashSet 保持插入顺序且去重。
     *
     * @param t5Data t5_data 节点
     * @return 匹配到的特殊宝石效果集合
     */
    private Set<String> extractSpecialGems(JsonNode t5Data) {
        Set<String> matched = new LinkedHashSet<>();
        t5Data.path("equip_plans").forEach(plan ->
            plan.path("equips").forEach(equip ->
                equip.path("gems").forEach(gem -> {
                    String effect = gem.path("effect").asText("");
                    if (TARGET_GEM_EFFECTS.contains(effect)) {
                        matched.add(effect);
                    }
                })
            )
        );
        return matched;
    }
}
