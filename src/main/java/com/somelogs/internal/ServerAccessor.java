package com.somelogs.internal;

import com.google.common.base.Preconditions;
import com.somelogs.annotation.RequestRoute;
import com.somelogs.model.Response;
import com.somelogs.model.ResponseStatus;
import com.somelogs.utils.HttpUtils;
import com.somelogs.utils.JsonUtils;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * server accessor
 *
 * @author LBG - 2018/1/5 0005
 */
public class ServerAccessor implements MethodInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerAccessor.class);

    /**
     * single instance
     */
    private static ServerAccessor INSTANCE;

    /**
     * map to store client and relevant server url
     */
    private static Map<Class, String> clientServerMap = new ConcurrentHashMap<>();

    /**
     * single mode to generate ServerAccessor instance
     *
     * @return single ServerAccessor instance
     */
    public static synchronized ServerAccessor singleInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ServerAccessor();
        }
        return INSTANCE;
    }

    /**
     * add new client to map
     */
    public static void addClient(Class<?> client, String serverUrl) {
        Preconditions.checkNotNull(client, "client must be not null");
        Preconditions.checkState(client.isInterface(), "client must be interface");
        Preconditions.checkArgument(StringUtils.isNotBlank(serverUrl), "server url can't be blank");
        LOGGER.info("add client [{}] to map", client.getName());
        clientServerMap.put(client, serverUrl);
    }

    /**
     * intercept method and request server url
     */
    @Override
    public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
        RequestRoute methodAnnotation = method.getAnnotation(RequestRoute.class);
        if (methodAnnotation == null) {
            return methodProxy.invokeSuper(o, objects);
        }
        RequestRoute typeAnnotation = method.getDeclaringClass().getAnnotation(RequestRoute.class);
        String postBody = JsonUtils.writeValueAsString(objects[0]);
        Response response;
        try {
            String requestUrl = clientServerMap.get(o.getClass().getInterfaces()[0])
                                + typeAnnotation.url() + methodAnnotation.url();
            LOGGER.info("Server accessor request url:{}", requestUrl);
            String responseJson = HttpUtils.postJson(requestUrl, postBody);
            response = JsonUtils.readValue(responseJson, method.getGenericReturnType());
        } catch (Exception e) {
            LOGGER.error("Server accessor request error", e);
            response = new Response<>();
            response.setCode(ResponseStatus.INTERNAL_SERVER_ERROR.getCode());
            response.setMessage(ResponseStatus.INTERNAL_SERVER_ERROR.getMessage());
        }
        return response;
    }
}
