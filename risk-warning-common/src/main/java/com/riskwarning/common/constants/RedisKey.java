package com.riskwarning.common.constants;

public class RedisKey {

    public static final String REDIS_KEY_FILE = "project:%d:file:hash";

    public static final Long REDIS_KEY_FILE_EXPIRE_TIME_SECONDS = 24 * 60 * 60L; // 24 hours

    public static final String REDIS_KEY_FILE_UPLOAD_CHUNK = "project:%d:file:chunk";

    public static final Long REDIS_KEY_UPLOAD_CHUNK_EXPIRE_TIME_SECONDS = 24 * 60 * 60L; // 24 hours


    public static final String REDIS_KEY_CONFIRMED_FILE_QUEUE = "file:confirmed:queue";


}
