package com.example.lab05.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.example.lab05.dto.DashboardResponse;
import com.example.lab05.model.cassandra.SensorReading;
import com.example.lab05.model.elastic.ProductDocument;
import com.example.lab05.model.mongo.PurchaseReceipt;
import com.example.lab05.model.neo4j.Person;
import com.example.lab05.repository.mongo.PurchaseReceiptRepository;

@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final PurchaseReceiptRepository purchaseReceiptRepository;
    private final SocialGraphService socialGraphService;
    private final SensorService sensorService;
    private final ProductSearchService searchService;
    private final RedisTemplate<String, Object> redisTemplate;

    public DashboardService(PurchaseReceiptRepository purchaseReceiptRepository,
            SocialGraphService socialGraphService,
            SensorService sensorService,
            ProductSearchService searchService,
            RedisTemplate<String, Object> redisTemplate) {
        this.purchaseReceiptRepository = purchaseReceiptRepository;
        this.socialGraphService = socialGraphService;
        this.sensorService = sensorService;
        this.searchService = searchService;
        this.redisTemplate = redisTemplate;
    }

    public DashboardResponse getDashboard(String personName) {
        String cacheKey = "dashboard:" + personName;

        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof DashboardResponse response) {
                return new DashboardResponse(
                        response.personName(),
                        response.totalSpent(),
                        response.purchaseCount(),
                        response.recentPurchases(),
                        response.friendRecommendations(),
                        response.friendsOfFriends(),
                        response.recentActivity(),
                        response.youMightAlsoLike(),
                        true);
            }
        } catch (Exception e) {
            log.warn("Redis cache check failed for {}: {}",
                    personName, e.getMessage());
        }

        List<PurchaseReceipt> allReceipts = purchaseReceiptRepository.findByPersonName(personName);
        double totalSpent = allReceipts.stream()
                .mapToDouble(receipt -> receipt.getTotalPrice() == null ? 0.0 : receipt.getTotalPrice())
                .sum();
        int purchaseCount = allReceipts.size();

        List<PurchaseReceipt> recentPurchases = allReceipts.stream()
                .sorted(Comparator.comparing(PurchaseReceipt::getPurchasedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList();

        List<Map<String, Object>> friendRecommendations = Collections.emptyList();
        List<String> friendsOfFriends = Collections.emptyList();
        try {
            friendRecommendations = socialGraphService.getRecommendations(personName, 5);
            friendsOfFriends = socialGraphService.getFriendsOfFriends(personName).stream()
                    .map(Person::getName)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to fetch Neo4j data for {}: {}",
                    personName, e.getMessage());
        }

        List<SensorReading> recentActivity = Collections.emptyList();
        try {
            recentActivity = sensorService.getLatestReadings("user-activity-" + personName.toLowerCase(), 10);
        } catch (Exception e) {
            log.warn("Failed to fetch activity for {}: {}",
                    personName, e.getMessage());
        }

        List<String> youMightAlsoLike = new ArrayList<>();
        try {
            Set<String> purchasedNames = allReceipts.stream()
                    .map(PurchaseReceipt::getProductName)
                    .collect(HashSet::new, HashSet::add, HashSet::addAll);

            Set<String> categories = allReceipts.stream()
                    .map(PurchaseReceipt::getProductCategory)
                    .collect(HashSet::new, HashSet::add, HashSet::addAll);

            for (String category : categories) {
                if (category == null) {
                    continue;
                }

                List<String> categorySuggestions = searchService.getByCategory(category).stream()
                        .map(ProductDocument::getName)
                        .filter(name -> !purchasedNames.contains(name))
                        .limit(2)
                        .toList();
                youMightAlsoLike.addAll(categorySuggestions);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch ES suggestions for {}: {}",
                    personName, e.getMessage());
            youMightAlsoLike = Collections.emptyList();
        }

        DashboardResponse response = new DashboardResponse(
                personName,
                totalSpent,
                purchaseCount,
                recentPurchases,
                friendRecommendations,
                friendsOfFriends,
                recentActivity,
                youMightAlsoLike,
                false);

        try {
            redisTemplate.opsForValue().set(cacheKey, response, Duration.ofMinutes(5));
        } catch (Exception e) {
            log.warn("Failed to cache dashboard for {}: {}",
                    personName, e.getMessage());
        }

        return response;
    }
}
