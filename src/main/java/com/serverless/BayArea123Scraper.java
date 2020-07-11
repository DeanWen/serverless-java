package com.serverless;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gargoylesoftware.htmlunit.UnexpectedPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class BayArea123Scraper {

    private final static Logger LOGGER = LogManager.getLogger(BayArea123Scraper.class);

    private final static String BASE_URL = "http://bay123.com";
    private final static String RENTAL_PAGE_URI = "/forum-40-1.html";
    private final static int IMAGE_WIDTH = 320;
    private final static int IMAGE_HEIGHT = 320;
    private final static float COMPRESSION_RATIO = 0.8f;
    private final static WebClient client = new WebClient();

    public BayArea123Scraper() {
        client.getOptions().setJavaScriptEnabled(false);
        client.getOptions().setCssEnabled(false);
        client.getOptions().setUseInsecureSSL(true);
    }

    public List<BayArea123Post> scrape() {
        List<BayArea123Post> posts = new ArrayList<>();
        try {
            HtmlPage page = client.getPage(BASE_URL + RENTAL_PAGE_URI);
            List<HtmlElement> items = (List<HtmlElement>) page.getByXPath("//table[@id='threadlisttableid']/tbody[starts-with(@id, 'normalthread')]");
            if (items.isEmpty()) {
                System.out.println("No items found");
                return Collections.emptyList();
            }
            for (HtmlElement htmlItem : items) {
                HtmlElement itemRegion = htmlItem.getFirstByXPath(".//tr/th/em/a");
                String region = itemRegion == null ? null : itemRegion.getTextContent();
                HtmlElement itemRow = htmlItem.getFirstByXPath(".//tr/th/a[starts-with(@href, 'thread')]");
                String title = itemRow.getTextContent();
                String postUrl = BASE_URL + "/" + ((HtmlAnchor) itemRow).getHrefAttribute();

                HtmlElement itemCreateTime = htmlItem.getFirstByXPath(".//tr/td[@class='by']/em/span/span");
                String createTime;
                if (itemCreateTime == null) {
                    itemCreateTime = htmlItem.getFirstByXPath(".//tr/td[@class='by']/em/span");
                    createTime = itemCreateTime.getTextContent();
                } else {
                    createTime = itemCreateTime.getAttribute("title");
                }

                BayArea123Post post = new BayArea123Post(region, title, createTime, postUrl);
                posts.add(post);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            for (BayArea123Post post : posts) {
                String url = post.getUrl();
                HtmlPage page = client.getPage(url);
                List<HtmlElement> items = (List<HtmlElement>) page.getByXPath("//div[starts-with(@id, 'post_')]/table/tbody/tr/td/div[@class='pct']/div[@class='pcb']/div[@class='t_fsz']/table/tbody/tr/td");

                //pick the 1st post
                HtmlElement htmlItem = items.get(0);

                StringBuilder sb = new StringBuilder();
                htmlItem.getChildNodes()
                        .stream()
                        .filter(e -> e.getNodeValue() != null)
                        .forEach(e -> sb.append(e.getNodeValue()));
                post.setContent(sb.toString());


                List<HtmlElement> images = (List<HtmlElement>) htmlItem.getByXPath("(.//ignore_js_op/img | //ignore_js_op/dl/dd/div/img)");
                images.forEach(e -> {
                    String image = getImageFile(e);
                    if (image == null) {
                        return;
                    }
                    List<String> temp;
                    if (post.getImages() == null) {
                        temp = new LinkedList<>();
                    } else {
                        temp = post.getImages();
                    }
                    temp.add(image);
                    post.setImages(temp);
                });

            }

            Thread.sleep(500L);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        Path fileName = Path.of("src/main/resources/initialResult.json");
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Files.writeString(fileName, objectMapper.writeValueAsString(posts));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return posts;
    }

    private String getImageFile(HtmlElement imageElement) {
        try {
            String imageUrl = BASE_URL + "/" + imageElement.getAttribute("zoomfile");
            UnexpectedPage imagePage = client.getPage(imageUrl);
            InputStream inputStream = imagePage.getWebResponse().getContentAsStream();
            BufferedImage origin = ImageIO.read(inputStream);
            BufferedImage image = scale(origin, IMAGE_WIDTH, IMAGE_HEIGHT);
            byte[] output = compress(image, COMPRESSION_RATIO);
            return new String(Base64.getEncoder().encode(output));
        } catch (IOException e) {
            LOGGER.error("Unable to get image", e);
            return null;
        }
    }

    private byte[] compress(BufferedImage originImage, float compressRatio) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(compressRatio);
        writer.setOutput(ImageIO.createImageOutputStream(os));
        writer.write(null, new IIOImage(originImage, null, null), param);
        writer.dispose();
        return os.toByteArray();
    }

    private BufferedImage scale(BufferedImage img, int targetWidth, int targetHeight) {
        int type = (img.getTransparency() == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;

        BufferedImage ret = img;
        BufferedImage scratchImage = null;
        Graphics2D g2 = null;

        int w = img.getWidth();
        int h = img.getHeight();

        int prevW = w;
        int prevH = h;

        do {
            if (w > targetWidth) {
                w /= 2;
                w = Math.max(w, targetWidth);
            }

            if (h > targetHeight) {
                h /= 2;
                h = Math.max(h, targetHeight);
            }

            if (scratchImage == null) {
                scratchImage = new BufferedImage(w, h, type);
                g2 = scratchImage.createGraphics();
            }

            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(ret, 0, 0, w, h, 0, 0, prevW, prevH, null);

            prevW = w;
            prevH = h;
            ret = scratchImage;
        } while (w != targetWidth || h != targetHeight);

        g2.dispose();

        if (targetWidth != ret.getWidth() || targetHeight != ret.getHeight()) {
            scratchImage = new BufferedImage(targetWidth, targetHeight, type);
            g2 = scratchImage.createGraphics();
            g2.drawImage(ret, 0, 0, null);
            g2.dispose();
            ret = scratchImage;
        }

        return ret;
    }
}
