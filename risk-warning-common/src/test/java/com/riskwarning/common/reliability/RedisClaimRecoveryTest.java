package com.riskwarning.common.reliability;

import com.riskwarning.common.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisClaimRecoveryTest {
    @Test void defaultTemplatePublisherAndConsumerAddressTheSameSerializedQueue() {
        RedisConnection connection = mock(RedisConnection.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenReturn(connection);
        Map<String, Deque<byte[]>> lists = new HashMap<>();
        java.util.function.Function<byte[], Deque<byte[]>> list = key ->
                lists.computeIfAbsent(Base64.getEncoder().encodeToString(key), ignored -> new ArrayDeque<>());
        when(connection.rPush(any(byte[].class), any(byte[].class))).thenAnswer(call -> {
            Deque<byte[]> values = list.apply(call.getArgument(0));
            values.addLast(call.getArgument(1));
            return (long) values.size();
        });
        when(connection.lIndex(any(byte[].class), eq(0L))).thenAnswer(call -> list.apply(call.getArgument(0)).peekFirst());
        when(connection.rPopLPush(any(byte[].class), any(byte[].class))).thenAnswer(call -> {
            byte[] value = list.apply(call.getArgument(0)).pollLast();
            if (value != null) list.apply(call.getArgument(1)).addFirst(value);
            return value;
        });
        when(connection.lRem(any(byte[].class), eq(1L), any(byte[].class))).thenAnswer(call -> {
            Deque<byte[]> values = list.apply(call.getArgument(0));
            byte[] value = call.getArgument(2);
            if (Arrays.equals(value, values.peekFirst())) { values.removeFirst(); return 1L; }
            return 0L;
        });
        RedisTemplate<String,Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.afterPropertiesSet(); // Production defaults: JDK keys and values, not StringRedisSerializer.
        RedisUtil publisher = new RedisUtil(); ReflectionTestUtils.setField(publisher, "redisTemplate", template);
        assertTrue(publisher.lSet("ready", "project-303"));
        RedisUtil consumer = new RedisUtil(); ReflectionTestUtils.setField(consumer, "redisTemplate", template);
        assertEquals("project-303", consumer.claimListItem("ready", "pending"));
        RedisUtil restarted = new RedisUtil(); ReflectionTestUtils.setField(restarted, "redisTemplate", template);
        Object recovered = restarted.claimListItem("ready", "pending");
        assertEquals("project-303", recovered);
        restarted.acknowledgeListItem("pending", recovered);
        assertTrue(lists.values().stream().allMatch(Deque::isEmpty));
    }

    @Test void restartRecoversClaimAndAcknowledgesOriginalSerializedBytes() {
        RedisConnection connection = mock(RedisConnection.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenReturn(connection);
        Deque<byte[]> ready = new ArrayDeque<>(), pending = new ArrayDeque<>();
        ready.add("old-format".getBytes(StandardCharsets.UTF_8));
        when(connection.lIndex(any(byte[].class), eq(0L))).thenAnswer(call -> pending.peekFirst());
        when(connection.rPopLPush(any(byte[].class), any(byte[].class))).thenAnswer(call -> {
            byte[] raw = ready.pollLast(); if(raw != null) pending.addFirst(raw); return raw;
        });
        when(connection.lRem(any(byte[].class), eq(1L), any(byte[].class))).thenAnswer(call -> {
            byte[] raw = call.getArgument(2);
            if(Arrays.equals(raw, pending.peekFirst())) { pending.removeFirst(); return 1L; }
            return 0L;
        });
        RedisTemplate<String,Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new RedisSerializer<Object>() {
            public byte[] serialize(Object value) { return "new-format".getBytes(StandardCharsets.UTF_8); }
            public Object deserialize(byte[] bytes) { return "task"; }
        });
        template.afterPropertiesSet();
        RedisUtil first = new RedisUtil(); ReflectionTestUtils.setField(first,"redisTemplate",template);
        assertEquals("task", first.claimListItem("ready","pending"));
        assertTrue(ready.isEmpty()); assertEquals(1,pending.size());
        RedisUtil restarted = new RedisUtil(); ReflectionTestUtils.setField(restarted,"redisTemplate",template);
        Object recovered = restarted.claimListItem("ready","pending");
        assertEquals("task",recovered);
        restarted.acknowledgeListItem("pending",recovered);
        assertTrue(pending.isEmpty(), "acknowledgement must use original bytes, not the new serializer output");
    }
}
