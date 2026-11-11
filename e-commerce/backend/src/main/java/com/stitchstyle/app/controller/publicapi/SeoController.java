package com.stitchstyle.app.controller.publicapi;

import com.stitchstyle.app.service.CategoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class SeoController {

    private final CategoryService categoryService;
    private final com.stitchstyle.app.service.ProductService productService;
    private final String publicBaseUrl;

    public SeoController(CategoryService categoryService, com.stitchstyle.app.service.ProductService productService,
                         @Value("${app.public-base-url:http://localhost:4000}") String publicBaseUrl) {
        this.categoryService = categoryService;
        this.productService = productService;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        add(xml, "/", "daily", "1.0");
        categoryService.tree(false).forEach(category -> addCategory(xml, category.slug()));
        productService.activeSlugs().forEach(slug -> add(xml, "/products/" + slug, "weekly", "0.8"));
        xml.append("</urlset>\n");
        return xml.toString();
    }

    private void addCategory(StringBuilder xml, String slug) {
        add(xml, "/collections/" + slug, "weekly", "0.9");
    }

    private void add(StringBuilder xml, String path, String changeFrequency, String priority) {
        xml.append("<url><loc>").append(publicBaseUrl).append(path).append("</loc>")
                .append("<lastmod>").append(Instant.now()).append("</lastmod>")
                .append("<changefreq>").append(changeFrequency).append("</changefreq>")
                .append("<priority>").append(priority).append("</priority></url>\n");
    }
}
