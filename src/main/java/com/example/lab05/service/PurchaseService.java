package com.example.lab05.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.example.lab05.dto.PurchaseRequest;
import com.example.lab05.model.Product;
import com.example.lab05.model.cassandra.SensorReading;
import com.example.lab05.model.cassandra.SensorReadingKey;
import com.example.lab05.model.elastic.ProductDocument;
import com.example.lab05.model.mongo.PurchaseReceipt;
import com.example.lab05.repository.mongo.PurchaseReceiptRepository;

@Service
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    private final ProductService productService;
    private final PurchaseReceiptRepository purchaseReceiptRepository;
    private final SocialGraphService socialGraphService;
    private final SensorService sensorService;
    private final ProductSearchService searchService;
    private final RedisTemplate<String, Object> redisTemplate;

    public PurchaseService(ProductService productService,
            PurchaseReceiptRepository purchaseReceiptRepository,
            SocialGraphService socialGraphService,
            SensorService sensorService,
            ProductSearchService searchService,
            RedisTemplate<String, Object> redisTemplate) {
        this.productService = productService;
        this.purchaseReceiptRepository = purchaseReceiptRepository;
        this.socialGraphService = socialGraphService;
        this.sensorService = sensorService;
        this.searchService = searchService;
        this.redisTemplate = redisTemplate;
    }

    public PurchaseReceipt executePurchase(PurchaseRequest request) {
        Product product = productService.getProductById(request.productId());

        if (request.quantity() == null || request.quantity() <= 0) {
            throw new RuntimeException("Quantity must be greater than zero.");
        }

        if (product.getStockQuantity() < request.quantity()) {
            throw new RuntimeException("Insufficient stock for product: " + product.getName());
        }

        product.setStockQuantity(product.getStockQuantity() - request.quantity());
        productService.updateProduct(product.getId(), product);

        PurchaseReceipt receipt = new PurchaseReceipt(
                request.personName(),
                product.getName(),
                product.getCategory(),
                request.quantity(),
                product.getPrice(),
                product.getPrice() * request.quantity(),
                request.purchaseDetails());
        PurchaseReceipt savedReceipt = purchaseReceiptRepository.save(receipt);

        try {
            socialGraphService.purchase(request.personName(), product.getName(), request.quantity(), product.getPrice());
        } catch (Exception e) {
            log.warn("Failed to create PURCHASED edge for {} -> {}: {}",
                    request.personName(), product.getName(), e.getMessage());
        }

        try {
            SensorReading event = new SensorReading();
            SensorReadingKey key = new SensorReadingKey();
            key.setSensorId("user-activity-" + request.personName().toLowerCase());
            key.setReadingTime(Instant.now());
            event.setKey(key);
            event.setTemperature(0.0);
            event.setHumidity(0.0);
            event.setLocation(product.getName());
            sensorService.recordReading(event);
        } catch (Exception e) {
            log.warn("Failed to log purchase event for {}: {}",
                    request.personName(), e.getMessage());
        }

        try {
            if (product.getStockQuantity() == 0) {
                List<ProductDocument> products = searchService.searchByName(product.getName());
                if (!products.isEmpty()) {
                    ProductDocument doc = products.get(0);
                    doc.setInStock(false);
                    searchService.saveProduct(doc);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to update ES inStock for product {}: {}",
                    product.getId(), e.getMessage());
        }

        try {
            redisTemplate.delete("dashboard:" + request.personName());
        } catch (Exception e) {
            log.warn("Failed to evict dashboard cache for {}: {}",
                    request.personName(), e.getMessage());
        }

        return savedReceipt;
    }

    public List<PurchaseReceipt> getPurchasesByPerson(String personName) {
        return purchaseReceiptRepository.findByPersonName(personName);
    }
}
