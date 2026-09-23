package com.gongcheng.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongcheng.assistant.common.GlobalErrorCodeConstants;
import com.gongcheng.assistant.common.ServiceException;
import com.gongcheng.assistant.entity.Member;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ApiQueryService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

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

    public ApiQueryService(ObjectMapper objectMapper,
                           @Value("${api.query.connect-timeout:10000}") int connectTimeout,
                           @Value("${api.query.read-timeout:30000}") int readTimeout) {
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(connectTimeout))
                .setReadTimeout(Duration.ofMillis(readTimeout))
                .build();
    }

    /**
     * 游戏参数（从链接中解析，用于安全存储和拼接）
     */
    public record GameParams(String serverId, String userId, String roleId) {}

    /**
     * 解析输入链接为游戏参数，用于安全存储
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
     * 根据游戏参数拼接完整 API 链接（内部使用，不暴露给前端）
     */
    public String buildApiUrl(GameParams params) {
        return UriComponentsBuilder.fromHttpUrl(API_PREFIX)
                .queryParam("server_id", params.serverId())
                .queryParam("user_id", params.userId())
                .queryParam("role_id", params.roleId())
                .build()
                .toUriString();
    }

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
     */
    public Map<String, Object> queryAndParse(GameParams params) {
        String apiUrl = buildApiUrl(params);
        log.info("开始查询接口: serverId={}, userId={}, roleId={}", params.serverId(), params.userId(), params.roleId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl, HttpMethod.GET, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ServiceException(GlobalErrorCodeConstants.API_QUERY_FAILED.getCode(),
                        "接口返回状态异常: " + response.getStatusCode());
            }

            JsonNode root = objectMapper.readTree(response.getBody());

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

            String roleName = t5Data.path("role_name").asText("");
            String progress = t5Data.path("progress").asText("");
            int atk = t5Data.path("atk").asInt(0);
            String gunDmgBonus = t5Data.path("gun_dmg_bonus").asText("");
            String critDmgBonus = t5Data.path("crit_dmg_bonus").asText("");

            String specialGems = String.join(",", extractSpecialGems(t5Data));
            log.info("解析成功: roleName={}, progress={}, atk={}, specialGems={}",
                    roleName, progress, atk, specialGems);

            Map<String, Object> result = new HashMap<>();
            result.put("roleName", roleName);
            result.put("progress", progress);
            result.put("atk", atk);
            result.put("gunDmgBonus", gunDmgBonus);
            result.put("critDmgBonus", critDmgBonus);
            result.put("specialGems", specialGems);
            return result;

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
        Map<String, Object> parsed = queryAndParse(params);

        // 存储用户原始输入链接
        member.setApiUrl(inputUrl);
        // 存储解析出的游戏参数
        member.setServerId(params.serverId());
        member.setGameUserId(params.userId());
        member.setGameRoleId(params.roleId());

        member.setRoleName((String) parsed.get("roleName"));
        member.setProgress((String) parsed.get("progress"));
        member.setAtk((Integer) parsed.get("atk"));
        member.setGunDmgBonus((String) parsed.get("gunDmgBonus"));
        member.setCritDmgBonus((String) parsed.get("critDmgBonus"));
        member.setSpecialGems((String) parsed.get("specialGems"));
    }

    /**
     * 用已存储的游戏参数重新拉取数据（刷新时调用）
     */
    public void refreshMemberFromParams(Member member) {
        GameParams params = new GameParams(member.getServerId(), member.getGameUserId(), member.getGameRoleId());
        Map<String, Object> parsed = queryAndParse(params);

        member.setRoleName((String) parsed.get("roleName"));
        member.setProgress((String) parsed.get("progress"));
        member.setAtk((Integer) parsed.get("atk"));
        member.setGunDmgBonus((String) parsed.get("gunDmgBonus"));
        member.setCritDmgBonus((String) parsed.get("critDmgBonus"));
        member.setSpecialGems((String) parsed.get("specialGems"));
    }

    /**
     * 遍历 equip_plans → equips → gems，筛选出目标效果的特殊宝石。
     * JsonNode.forEach 对缺失/非数组节点为空操作，无需手动判空。
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
