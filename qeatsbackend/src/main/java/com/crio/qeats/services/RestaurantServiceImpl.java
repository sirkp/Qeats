
/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.services;

import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.exchanges.GetRestaurantsRequest;
import com.crio.qeats.exchanges.GetRestaurantsResponse;
import com.crio.qeats.repositoryservices.RestaurantRepositoryService;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class RestaurantServiceImpl implements RestaurantService {

  private final Double peakHoursServingRadiusInKms = 3.0;
  private final Double normalHoursServingRadiusInKms = 5.0;
  @Autowired
  private RestaurantRepositoryService restaurantRepositoryService;


  private boolean isBetween(LocalTime time, LocalTime start, LocalTime end) {
    if ((time.isAfter(start) || time.equals(start)) 
        && (time.isBefore(end) || time.equals(end))) {
      return true;
    } else {
      return false;
    }
  }

  private boolean isPeakTime(LocalTime time) {
    if (isBetween(time, LocalTime.parse("08:00"), LocalTime.parse("10:00")) 
        || isBetween(time, LocalTime.parse("13:00"), LocalTime.parse("14:00"))
        || isBetween(time, LocalTime.parse("19:00"), LocalTime.parse("21:00"))) {
      return true;
    } else {
      return false;
    }
  }

  private double getServingRadius(LocalTime time) {
    if (isPeakTime(time)) {
      return peakHoursServingRadiusInKms;
    } else {
      return normalHoursServingRadiusInKms;
    }
  }
  
  @Override
  public GetRestaurantsResponse findAllRestaurantsCloseBy(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {
     
    List<Restaurant> restaurants = restaurantRepositoryService.findAllRestaurantsCloseBy(
        getRestaurantsRequest.getLatitude(), getRestaurantsRequest.getLongitude(),
        currentTime, getServingRadius(currentTime));
    
    return new GetRestaurantsResponse(restaurants);
  }


}

