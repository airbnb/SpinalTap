/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import lombok.experimental.UtilityClass;

/** Utility class for json operations and components. */
@UtilityClass
public class JsonUtil {
  /** The {@link ObjectMapper} used for json SerDe. */
  public ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.registerModule(new JodaModule());
  }
}
