package com.riskwarning.notification.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ChannelHandler.Sharable
public class NotificationWebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    @Autowired
    private WebSocketChannelManager channelManager;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        log.info("New WebSocket connection established: {}", ctx.channel().id().asShortText());
        channelManager.addChannel(ctx.channel());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        log.info("WebSocket connection closed: {}", ctx.channel().id().asShortText());
        channelManager.removeChannel(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame webSocketFrame) throws Exception {
        if (webSocketFrame instanceof PingWebSocketFrame) {
            ctx.channel().writeAndFlush(new PongWebSocketFrame());
            return;
        }

        if (webSocketFrame instanceof TextWebSocketFrame) {
            String text = ((TextWebSocketFrame) webSocketFrame).text();
            log.info("Received message: {}", text);
            handleTextMessage(ctx, text);
        }

        if (webSocketFrame instanceof CloseWebSocketFrame) {
            ctx.channel().close();
        }
    }

    /**
     * 处理文���消息
     */
    private void handleTextMessage(ChannelHandlerContext ctx, String text) {
        try {
            JSONObject jsonObject = JSON.parseObject(text);
            String type = jsonObject.getString("type");

            if ("bind".equals(type)) {
                // 绑定用户ID
                Long userId = jsonObject.getLong("userId");
                if (userId != null) {
                    channelManager.bindUser(userId, ctx.channel());
                    ctx.channel().writeAndFlush(new TextWebSocketFrame(
                            JSON.toJSONString(new BindResponse("bind_success", userId, "用户绑定成功"))
                    ));
                } else {
                    ctx.channel().writeAndFlush(new TextWebSocketFrame(
                            JSON.toJSONString(new BindResponse("bind_error", null, "用户ID不能为空"))
                    ));
                }
            } else if ("ping".equals(type)) {
                // 心跳响应
                ctx.channel().writeAndFlush(new TextWebSocketFrame("{\"type\":\"pong\"}"));
            } else {
                // 其他消息类型，可以根据需要扩展
                ctx.channel().writeAndFlush(new TextWebSocketFrame(
                        JSON.toJSONString(new MessageResponse("received", text))
                ));
            }
        } catch (Exception e) {
            log.error("Error parsing message: {}", e.getMessage());
            ctx.channel().writeAndFlush(new TextWebSocketFrame(
                    JSON.toJSONString(new MessageResponse("error", "消息格式错误"))
            ));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception caught: {}", cause.getMessage());
        ctx.close();
    }

    /**
     * 绑定响应
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class BindResponse {
        private String type;
        private Long userId;
        private String message;
    }

    /**
     * 消息响应
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class MessageResponse {
        private String type;
        private String message;
    }
}
