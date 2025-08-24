package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class OrderWebSocketHandler extends TextWebSocketHandler {

    // 保存 orderId -> WebSocketSession
    private static final ConcurrentHashMap<String, WebSocketSession> orderSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 从参数里取 orderId，比如 ws://localhost:8080/ws/order?orderId=123
        String query = Objects.requireNonNull(session.getUri()).getQuery();
        String orderId = query.split("=")[1];
        orderSessions.put(orderId, session);
        System.out.println("WebSocket 连接建立成功, orderId=" + orderId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        orderSessions.values().remove(session);
    }

    // 给前端推送订单状态
    public static void sendOrderMessage(Long orderId, String msg) {
        WebSocketSession session = orderSessions.get(String.valueOf(orderId));
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(msg));
            } catch (Exception e) {
                log.error("向前端发送消息:{}失败, 错误信息:{}", msg, e.getMessage());
            }
        }
    }
}

