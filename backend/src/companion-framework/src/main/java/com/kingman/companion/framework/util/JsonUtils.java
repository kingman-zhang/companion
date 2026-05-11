package com.kingman.companion.framework.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import com.fasterxml.jackson.module.noctordeser.NoCtorDeserModule;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * JSON工具类（Jackson实现）
 *
 * @author kingman
 */
@Slf4j
@SuppressWarnings("unused")
public abstract class JsonUtils {

    @Getter
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        // 设置时区为GMT+8
        MAPPER.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        // 设置序列化时忽略空值
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        //反序列化的时候如果多了其他属性,不抛出异常
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        //如果是空对象的时候,不抛异常
        MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        //取消时间的转化格式,默认是时间戳,可以取消,同时需要设置要表现的时间格式
        MAPPER.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        MAPPER.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        // 配置是否缩进输出JSON
        MAPPER.configure(SerializationFeature.INDENT_OUTPUT, false);
        // 配置是否允许使用单引号包裹字符串
        MAPPER.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

        // 注册jackson-datatype-jsr310模块
        MAPPER.registerModule(new JavaTimeModule());
        // 注册jackson-datatype-joda模块
        MAPPER.registerModule(new JodaModule());
        // 注册jackson-datatype-jdk8模块
        MAPPER.registerModule(new Jdk8Module());
        // 注册jackson-module-no-ctor-deser模块
        // 允许实例化没有“默认构造函数”（不带参数的构造函数）
        MAPPER.registerModule(new NoCtorDeserModule());
        // 注册jackson-module-blackbird模块
        // Afterburner的代替者，优化序列化/反序列化性能
        MAPPER.registerModule(new BlackbirdModule());
    }

    private JsonUtils() {
    }

    /**
     * 将实体对象转换为json字符串
     *
     * @param entity 实体对象
     * @param <T>    泛型
     * @return json string
     */
    @SneakyThrows(JsonProcessingException.class)
    public static <T> String toJsonString(T entity) {
        return MAPPER.writeValueAsString(entity);
    }

    /**
     * 将实体对象转换为json字符串
     *
     * @param entity 实体对象
     * @param pretty 是否转换为美观格式
     * @param <T>    泛型
     * @return json string
     */
    @SneakyThrows(JsonProcessingException.class)
    public static <T> String toJsonString(T entity, boolean pretty) {
        if (pretty) {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(entity);
        }
        return MAPPER.writeValueAsString(entity);
    }

    /**
     * 将实体对象转换为字节数组
     *
     * @param entity 实体对象
     * @param <T>    泛型
     * @return json string
     */
    @SneakyThrows(JsonProcessingException.class)
    public static <T> byte[] toJsonBytes(T entity) {
        return MAPPER.writeValueAsBytes(entity);
    }

    /**
     * 将json字符串转换为实体类对象
     *
     * @param json json字符串
     * @param type 实体对象类型
     * @param <T>  泛型
     * @return 转换成功后的对象
     */
    @SneakyThrows(JsonProcessingException.class)
    public static <T> T toJavaObject(String json, Type type) {
        return MAPPER.readValue(json, MAPPER.constructType(type));
    }

    /**
     * 将json字符串转换为实体类对象
     *
     * @param json json字符串
     * @param type 实体对象类型
     * @param <T>  泛型
     * @return 转换成功后的对象
     */
    @SneakyThrows(JsonProcessingException.class)
    public static <T> T toJavaObject(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }

    /**
     * 将json字符串转换为实体类对象
     *
     * @param json json字符串
     * @param type 实体对象类型
     * @param <T>  泛型
     * @return 转换成功后的对象
     */
    @SneakyThrows(JsonProcessingException.class)
    public static <T> T toJavaObject(String json, TypeReference<T> type) {
        return MAPPER.readValue(json, type);
    }

    /**
     * 将json字符串转换为map
     *
     * @param json json字符串
     * @return Map
     */
    @SneakyThrows(JsonProcessingException.class)
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(String json) {
        return (Map<String, Object>) MAPPER.readValue(json, Map.class);
    }

    /**
     * 将对象转换为map
     *
     * @param obj 对象
     * @return Map
     */
    public static <T> Map<String, Object> toMap(T obj) {
        return toMap(toJsonString(obj));
    }

    /**
     * 泛化转换方式，此方式最为强大、灵活
     * <p>
     * example：
     * <p>
     * {@code Map<String, List<UserInfo>> listMap = genericConvert(jsonStr, new TypeReference<Map<String, List<UserInfo>>>() {});}
     *
     * @param json json字符串
     * @param type type
     * @param <T>  泛化
     * @return T
     */
    @SneakyThrows(JsonProcessingException.class)
    public static <T> T genericConvert(String json, TypeReference<T> type) {
        return MAPPER.readValue(json, type);
    }

    /**
     * 将map转换为实体类对象
     *
     * @param map  map
     * @param type 实体对象类型
     * @param <T>  泛型
     * @return 实体对象
     */
    public static <T> T toJavaObject(Map<?, ?> map, Class<T> type) {
        return MAPPER.convertValue(map, type);
    }

    /**
     * 将json字符串转换为实体类集合
     *
     * @param json json字符串
     * @param type 实体对象类型
     * @param <T>  泛型
     * @return list集合
     */
    @SneakyThrows(JsonProcessingException.class)
    public static <T> List<T> toJavaList(String json, Class<T> type) {
        CollectionType collectionType = MAPPER.getTypeFactory().constructCollectionType(List.class, type);
        return MAPPER.readValue(json, collectionType);
    }

    /**
     * 将实体类转换为JsonNode对象
     *
     * @param entity 实体对象
     * @param <T>    泛型
     * @return JsonNode
     */
    public static <T> JsonNode toJsonNode(T entity) {
        return MAPPER.valueToTree(entity);
    }

    /**
     * 将json字符串转换为JsonNode对象
     *
     * @param json json字符串
     * @return JsonNode对象
     */
    @SneakyThrows(JsonProcessingException.class)
    public static JsonNode toJsonNode(String json) {
        return MAPPER.readTree(json);
    }

    /**
     * 检查字符串是否是json格式
     *
     * @param json 待检查字符串
     * @return 是 true，否 false
     */
    public static boolean isJson(String json) {
        try {
            MAPPER.readTree(json);
            return true;
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("[isJson]{},字符串原文{}", e.getLocalizedMessage(), json);
            }
            return false;
        }
    }

}
