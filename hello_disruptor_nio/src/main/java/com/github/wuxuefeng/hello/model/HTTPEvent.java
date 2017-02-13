package com.github.wuxuefeng.hello.model;

/**
 * Created by xfwu on 12/02/2017.
 */
public class HTTPEvent {
    private HTTPSession session;

    public void set(HTTPSession session) {
        this.session = session;
    }

    public HTTPSession getSession() {
        return session;
    }
}
