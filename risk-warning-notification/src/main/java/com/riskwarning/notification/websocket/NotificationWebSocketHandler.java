package com.riskwarning.notification.websocket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@Slf4j
public class NotificationWebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {


    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        log.info("New WebSocket connection established: {}", ctx.channel().id().asShortText());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        log.info("WebSocket connection closed: {}", ctx.channel().id().asShortText());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame webSocketFrame) throws Exception {
        if (webSocketFrame instanceof PingWebSocketFrame) {
            ctx.channel().writeAndFlush(new PongWebSocketFrame());
            return;
        }

        if (webSocketFrame instanceof TextWebSocketFrame) {
            String text = ((TextWebSocketFrame) webSocketFrame).text();
            System.out.println("Received message: " + text);
            ctx.channel().writeAndFlush(new TextWebSocketFrame("Echo: " + text));
        }

        if (webSocketFrame instanceof CloseWebSocketFrame) {
            ctx.channel().close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.info("Exception caught: {}", cause.getMessage());
        ctx.close();
    }
}
