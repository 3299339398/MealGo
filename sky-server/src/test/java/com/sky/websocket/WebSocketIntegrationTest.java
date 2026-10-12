package com.sky.websocket;

import com.sky.config.WebSocketConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = WebSocketIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=websocket-test", "spring.main.allow-circular-references=false",
                "spring.mvc.pathmatch.matching-strategy=ant_path_matcher"})
class WebSocketIntegrationTest {
    @Configuration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class, excludeName = {
            "com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure",
            "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"})
    @Import({WebSocketConfiguration.class, WebSocketServer.class})
    static class TestApplication { }

    @LocalServerPort private int port;
    @Autowired private WebSocketServer server;

    @Test
    void connectsToEndpointAndReceivesOrderNotification() throws Exception {
        CompletableFuture<String> received = new CompletableFuture<>();
        TextWebSocketHandler handler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                received.complete(message.getPayload());
            }
        };
        WebSocketSession session = new StandardWebSocketClient().doHandshake(handler,
                "ws://localhost:" + port + "/ws/" + UUID.randomUUID()).get(10, TimeUnit.SECONDS);
        try {
            assertTrue(session.isOpen());
            String notification = "{\"type\":1,\"orderId\":123,\"content\":\"test order\"}";
            server.sendToAllClient(notification);
            assertEquals(notification, received.get(10, TimeUnit.SECONDS));
        } finally {
            session.close();
        }
    }
}
