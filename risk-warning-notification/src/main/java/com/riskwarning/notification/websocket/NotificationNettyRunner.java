package com.riskwarning.notification.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NotificationNettyRunner implements CommandLineRunner {

    @Autowired
    private NotificationServer notificationServer;

    @Override
    public void run(String... args) throws Exception {
        new Thread(() -> {
            try {
                notificationServer.start();
            } catch (Exception e) {
                log.error("WebSocket服务器启动失败: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }).start();
    }
}
