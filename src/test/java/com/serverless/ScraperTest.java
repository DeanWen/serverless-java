package com.serverless;

import org.junit.Test;

import java.util.List;

public class ScraperTest {

    @Test
    public void test () {
        BayArea123Scraper scraper = new BayArea123Scraper();
        List<BayArea123Post> result = scraper.scrape();
        System.out.println(result);
    }

}
