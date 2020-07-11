package com.serverless;

import java.util.List;

public class BayArea123Post {
    private final String bayRegion;
    private final String title;
    private final String createTime;
    private final String url;
    private String content;
    private List<String> images;

    public BayArea123Post(String bayRegion, String title, String createTime, String url) {
        this.bayRegion = bayRegion;
        this.title = title;
        this.createTime = createTime;
        this.url = url;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public List<String> getImages() {
        return images;
    }

    public String getBayRegion() {
        return bayRegion;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getContent() {
        return content;
    }
}
