package com.kingman.companion.framework.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * 支持多次重复获取流的 HttpServletRequest 包装器
 *
 * @author kingman
 */
@Getter
@Slf4j
public class RequestWrapper extends HttpServletRequestWrapper {

    /**
     * 请求体
     */
    private final String body;

    public RequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        // 将body数据存储起来
        body = getBody(request);
    }

    /**
     * 获取请求体
     *
     * @param request 请求
     * @return 请求体
     */
    private String getBody(HttpServletRequest request) throws IOException {
        return IOUtils.toString(request.getInputStream(), request.getCharacterEncoding());
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

    @Override
    public ServletInputStream getInputStream() {
        // 创建字节数组输入流
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body.getBytes(Charset.defaultCharset()));

        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return false;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() {
                return byteArrayInputStream.read();
            }
        };
    }
}