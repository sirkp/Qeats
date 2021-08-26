/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.repositoryservices;

import ch.hsr.geohash.GeoHash;
import com.crio.qeats.configs.RedisConfiguration;
import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.globals.GlobalConstants;
import com.crio.qeats.models.RestaurantEntity;
import com.crio.qeats.repositories.RestaurantRepository;
import com.crio.qeats.utils.GeoLocation;
import com.crio.qeats.utils.GeoUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Provider;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;

@Service
public class RestaurantRepositoryServiceImpl implements RestaurantRepositoryService {

  @Autowired
  private RestaurantRepository restaurantRepository;

  @Autowired
  private RedisConfiguration redisConfiguration;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private Provider<ModelMapper> modelMapperProvider;

  private boolean isOpenNow(LocalTime time, RestaurantEntity res) {
    LocalTime openingTime = LocalTime.parse(res.getOpensAt());
    LocalTime closingTime = LocalTime.parse(res.getClosesAt());

    return time.isAfter(openingTime) && time.isBefore(closingTime);
  }

  private String seriliazeRestaurantList(List<Restaurant> restaurants) {
    ObjectMapper objectMapper = new ObjectMapper();
    String value = null;
    try {
      value = objectMapper.writeValueAsString(restaurants);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return value;
  }

  private List<Restaurant> deserializeRestaurants(String restaurants) {
    ObjectMapper objectMapper = new ObjectMapper();
    List<Restaurant> restaurantsList = null;
    try {
      restaurantsList = objectMapper.readValue(restaurants,
        new TypeReference<List<Restaurant>>(){});
    } catch (Exception e) {
      e.printStackTrace();
    }
    return restaurantsList;
  }

  private List<Restaurant> useCache(String key, String restaurants) {
    return deserializeRestaurants(restaurants);
  }

  private void addToCache(String key, List<Restaurant> restaurants, Jedis jedis) {
    String value = seriliazeRestaurantList(restaurants);
    jedis.setex(key, RedisConfiguration.REDIS_ENTRY_EXPIRY_IN_SECONDS, value);
  }

  private List<Restaurant> useDb(Double latitude, Double longitude,
      LocalTime currentTime, Double servingRadiusInKms, String key, Jedis jedis) {
    
    List<RestaurantEntity> restaurantEntities = restaurantRepository.findAll();
    ModelMapper modelMapper = modelMapperProvider.get();
    List<Restaurant> restaurants = new ArrayList<>();
    Restaurant restaurant;

    for (RestaurantEntity restaurantEntity: restaurantEntities) {
      restaurant = modelMapper.map(restaurantEntity, Restaurant.class);

      if (isRestaurantCloseByAndOpen(restaurantEntity, currentTime,
          latitude, longitude, servingRadiusInKms)) {
        restaurants.add(restaurant);
      }
      
    }
    
    addToCache(key, restaurants, jedis);

    return restaurants;
  }

  public List<Restaurant> findAllRestaurantsCloseBy(Double latitude,
      Double longitude, LocalTime currentTime, Double servingRadiusInKms) {
    
    List<Restaurant> restaurants = null;
    GeoHash geoHash = GeoHash.withCharacterPrecision(latitude, longitude, 7);

    try (Jedis jedis = redisConfiguration.getJedisPool().getResource()) {
      String key = geoHash.toBase32();
      String value = jedis.get(key);
      
      if (value != null) { // use cache
        restaurants = useCache(key, value);
      } else {
        restaurants = useDb(latitude, longitude, currentTime, servingRadiusInKms, key, jedis);
      }

    } catch (Exception e) {
      e.printStackTrace();
    }

    return restaurants;
    // TODO: CRIO_TASK_MODULE_REDIS
    // We want to use cache to speed things up. Write methods that perform the same functionality,
    // but using the cache if it is present and reachable.
    // Remember, you must ensure that if cache is not present, the queries are directed at the
    // database instead.


    //CHECKSTYLE:OFF
    //CHECKSTYLE:ON


  }









  /**
   * Utility method to check if a restaurant is within the serving radius at a given time.
   * @return boolean True if restaurant falls within serving radius and is open, false otherwise
   */
  private boolean isRestaurantCloseByAndOpen(RestaurantEntity restaurantEntity,
      LocalTime currentTime, Double latitude, Double longitude, Double servingRadiusInKms) {
    if (isOpenNow(currentTime, restaurantEntity)) {
      return GeoUtils.findDistanceInKm(latitude, longitude,
          restaurantEntity.getLatitude(), restaurantEntity.getLongitude())
          < servingRadiusInKms;
    }

    return false;
  }



}

