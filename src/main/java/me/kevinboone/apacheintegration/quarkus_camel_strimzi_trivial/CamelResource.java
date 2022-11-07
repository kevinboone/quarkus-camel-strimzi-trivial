/*===========================================================================
 
  CamelResource.java

  Copyright (c)2022 Kevin Boone, GPL v3.0

===========================================================================*/

package me.kevinboone.apacheintegration.quarkus_camel_strimzi_trivial;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import org.apache.camel.CamelContext;

@ApplicationScoped
public class CamelResource 
  {
  /** 
  Initialization code could go here, although it is currently commented
  out. 
  */
  @PostConstruct
  void postConstruct() throws Exception 
    {
    }
  }

