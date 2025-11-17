package com.riskwarning.common.context;

import com.riskwarning.common.po.User;

public class UserContext {

    public static ThreadLocal<User> userContext = new ThreadLocal<>();

    public static void setUser(User user) {
        userContext.set(user);
    }

    public static User getUser() {
        return userContext.get();
    }
}
