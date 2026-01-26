package com.riskwarning.notification.websocket;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket通道管理器
 * 管理用户与Channel的映射关系
 */
@Component
@Slf4j
public class WebSocketChannelManager {

    /**
     * 所有连接的Channel组
     */
    private static final ChannelGroup ALL_CHANNELS = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    /**
     * 用户ID与Channel的映射
     */
    private static final ConcurrentHashMap<Long, Channel> USER_CHANNEL_MAP = new ConcurrentHashMap<>();

    /**
     * Channel与用户ID的映射（反向映射，用于断开连接时快速找到用户ID）
     */
    private static final ConcurrentHashMap<String, Long> CHANNEL_USER_MAP = new ConcurrentHashMap<>();

    /**
     * 添加Channel到全局组
     */
    public void addChannel(Channel channel) {
        ALL_CHANNELS.add(channel);
        log.info("Channel added: {}, total channels: {}", channel.id().asShortText(), ALL_CHANNELS.size());
    }

    /**
     * 从全局组移除Channel
     */
    public void removeChannel(Channel channel) {
        ALL_CHANNELS.remove(channel);
        String channelId = channel.id().asLongText();
        Long userId = CHANNEL_USER_MAP.remove(channelId);
        if (userId != null) {
            USER_CHANNEL_MAP.remove(userId);
            log.info("User {} disconnected, channel: {}", userId, channel.id().asShortText());
        }
        log.info("Channel removed: {}, total channels: {}", channel.id().asShortText(), ALL_CHANNELS.size());
    }

    /**
     * 绑定用户ID与Channel
     */
    public void bindUser(Long userId, Channel channel) {
        // 如果用户之前有旧连接，先断开
        Channel oldChannel = USER_CHANNEL_MAP.get(userId);
        if (oldChannel != null && oldChannel.isActive()) {
            oldChannel.close();
        }

        USER_CHANNEL_MAP.put(userId, channel);
        CHANNEL_USER_MAP.put(channel.id().asLongText(), userId);
        log.info("User {} bound to channel: {}", userId, channel.id().asShortText());
    }

    /**
     * 获取用户的Channel
     */
    public Channel getChannel(Long userId) {
        return USER_CHANNEL_MAP.get(userId);
    }

    /**
     * 向指定用户发送消息
     */
    public boolean sendMessageToUser(Long userId, String message) {
        Channel channel = USER_CHANNEL_MAP.get(userId);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(new TextWebSocketFrame(message));
            log.info("Message sent to user {}: {}", userId, message);
            return true;
        }
        log.warn("User {} is not online, message not sent", userId);
        return false;
    }

    /**
     * 向所有连接的客户端广播消息
     */
    public void broadcastMessage(String message) {
        ALL_CHANNELS.writeAndFlush(new TextWebSocketFrame(message));
        log.info("Broadcast message: {}", message);
    }

    /**
     * 获取在线用户数
     */
    public int getOnlineUserCount() {
        return USER_CHANNEL_MAP.size();
    }

    /**
     * 获取总连接数
     */
    public int getTotalChannelCount() {
        return ALL_CHANNELS.size();
    }

    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(Long userId) {
        Channel channel = USER_CHANNEL_MAP.get(userId);
        return channel != null && channel.isActive();
    }

    /**
     * 关闭所有连接并清理状态（主要用于测试）
     */
    public void closeAll() {
        ALL_CHANNELS.close();
        USER_CHANNEL_MAP.clear();
        CHANNEL_USER_MAP.clear();
        log.info("All channels closed and state cleared");
    }
}

