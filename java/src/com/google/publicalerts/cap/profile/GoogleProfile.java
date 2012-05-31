/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.publicalerts.cap.profile;

import com.google.publicalerts.cap.Alert;
import com.google.publicalerts.cap.AlertOrBuilder;
import com.google.publicalerts.cap.Area;
import com.google.publicalerts.cap.CapException;
import com.google.publicalerts.cap.CapException.Reason;
import com.google.publicalerts.cap.CapUtil;
import com.google.publicalerts.cap.Circle;
import com.google.publicalerts.cap.Info;
import com.google.publicalerts.cap.ValuePair;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A CAP profile for alerts intended for the Google Public Alerts
 * platform.
 * <p>
 * Based on http://goo.gl/jgHTe
 * <p>
 * Most of these checks are not possible to represent with an
 * xsd schema.
 *
 * @author shakusa@google.com (Steve Hakusa)
 */
public class GoogleProfile extends AbstractCapProfile {

  public GoogleProfile() {
    super();
  }

  /**
   * @param strictXsdValidation if true, perform by-the-spec xsd schema
   * validation, which does not check a number of properties specified
   * elsewhere in the spec. If false (the default), attempt to do extra
   * validation to conform to the text of the spec.
   */
  public GoogleProfile(boolean strictXsdValidation) {
    super(strictXsdValidation);
  }

  @Override
  public String getName() {
    return "Google Public Alerts CAP v1.0";
  }

  @Override
  public String getCode() {
    return "google";
  }

  @Override
  public String getDocumentationUrl() {
    return "http://goo.gl/jgHTe";
  }

  @Override
  public String toString() {
    return getCode();
  }

  @Override
  public List<Reason> checkForErrors(AlertOrBuilder alert) {
    List<Reason> reasons = new ArrayList<Reason>();

    // An Update or Cancel message should minimally include
    // references to all active messages
    if ((alert.getMsgType() == Alert.MsgType.UPDATE
        || alert.getMsgType() == Alert.MsgType.CANCEL)
        && alert.getReferences().getValueCount() == 0) {
      reasons.add(new Reason("/alert/msgType",
          ErrorType.UPDATE_OR_CANCEL_MUST_REFERENCE));
    }

    // Alert messages intended for public distribution must include
    // an <info> block
    if (alert.getInfoCount() == 0) {
      reasons.add(new Reason("/alert", ErrorType.INFO_IS_REQUIRED));
    }

    Set<Info.Category> categories = null;
    Set<ValuePair> eventCodes = null;
    Map<String, String> eventByLanguage = new HashMap<String, String>();
    for (int i = 0; i < alert.getInfoCount(); i++) {
      Info info = alert.getInfo(i);
      String xpath = "/alert/info[" + i + "]";

      // All infos must have same <category> and <eventCode> values
      Set<Info.Category> cats = new HashSet<Info.Category>();
      cats.addAll(info.getCategoryList());
      if (categories == null) {
        categories = cats;
      } else if (!categories.equals(cats)) {
        reasons.add(new Reason(xpath + "/category",
            ErrorType.CATEGORIES_MUST_MATCH));
      }

      Set<ValuePair> ecs = new HashSet<ValuePair>();
      ecs.addAll(info.getEventCodeList());
      if (eventCodes == null) {
        eventCodes = ecs;
      } else if (!eventCodes.equals(ecs)) {
        reasons.add(new Reason(xpath + "/eventCode",
            ErrorType.EVENT_CODES_MUST_MATCH));
      }

      if (eventByLanguage.containsKey(info.getLanguage())) {
        if (!info.getEvent().equals(eventByLanguage.get(info.getLanguage()))) {
          reasons.add(new Reason(xpath + "/event",
              ErrorType.EVENTS_IN_SAME_LANGUAGE_MUST_MATCH));
        }
      } else {
        eventByLanguage.put(info.getLanguage(), info.getEvent());
      }

      // <description> is required
      if (!info.hasDescription()
          || CapUtil.isEmptyOrWhitespace(info.getDescription())) {
        reasons.add(new Reason(xpath, ErrorType.DESCRIPTION_IS_REQUIRED));
      }

      // <effective> should be before <expires>
      if (info.hasExpires()) {
        String effective = info.hasEffective()
            ? info.getEffective() : alert.getSent();
        Date effectiveDate = CapUtil.toJavaDate(effective);
        Date expiresDate = CapUtil.toJavaDate(info.getExpires());

        if (effectiveDate.after(expiresDate)) {
          reasons.add(new Reason(xpath + "/effective",
              ErrorType.EFFECTIVE_NOT_AFTER_EXPIRES));
        }
      }

      // <web> is required
      if (!info.hasWeb() || CapUtil.isEmptyOrWhitespace(info.getWeb())) {
        reasons.add(new Reason(xpath, ErrorType.WEB_IS_REQUIRED));
      }

      // An <expires> value is required
      if (!info.hasExpires()
          || CapUtil.isEmptyOrWhitespace(info.getExpires())) {
        reasons.add(new Reason(xpath, ErrorType.EXPIRES_IS_REQUIRED));
      }

      // <area> blocks are required
      if (info.getAreaCount() == 0) {
        reasons.add(new Reason(xpath, ErrorType.AREA_IS_REQUIRED));
      }

      if (!info.hasUrgency()) {
        reasons.add(new Reason(xpath, ErrorType.URGENCY_IS_REQUIRED));
      }
      if (!info.hasSeverity()) {
        reasons.add(new Reason(xpath, ErrorType.SEVERITY_IS_REQUIRED));
      }
      if (!info.hasCertainty()) {
        reasons.add(new Reason(xpath, ErrorType.CERTAINTY_IS_REQUIRED));
      }

      // <area> blocks must have at least one <circle> <polygon> or <geocode>
      for (int j = 0; j < info.getAreaCount(); j++) {
        Area area = info.getArea(j);
        if (area.getGeocodeCount() == 0
            && area.getCircleCount() == 0
            && area.getPolygonCount() == 0) {
          reasons.add(new Reason(xpath + "/area[" + j + "]",
              ErrorType.CIRCLE_POLYGON_OR_GEOCODE_IS_REQUIRED));
        }
      }
    }

    return reasons;
  }

  @Override
  public List<Reason> checkForRecommendations(AlertOrBuilder alert) {
    List<Reason> reasons = new ArrayList<Reason>();

    // Time zone field must be included in all time values
    checkZeroTimezone(reasons, alert.getSent(), "/alert/sent",
        RecommendationType.SENT_INCLUDE_TIMEZONE_OFFSET);

    for (int i = 0; i < alert.getInfoCount(); i++) {
      Info info = alert.getInfo(i);
      String xpath = "/alert/info[" + i + "]";

      // Time zone field must be included in all time values
      checkZeroTimezone(reasons, info.getEffective(), xpath + "/effective",
          RecommendationType.EFFECTIVE_INCLUDE_TIMEZONE_OFFSET);
      checkZeroTimezone(reasons, info.getOnset(), xpath + "/onset",
          RecommendationType.ONSET_INCLUDE_TIMEZONE_OFFSET);
      checkZeroTimezone(reasons, info.getExpires(), xpath + "/expires",
          RecommendationType.EXPIRES_INCLUDE_TIMEZONE_OFFSET);

      // A <senderName> is strongly recommended
      if (CapUtil.isEmptyOrWhitespace(info.getSenderName())) {
        reasons.add(new Reason(xpath,
            RecommendationType.SENDER_NAME_STRONGLY_RECOMMENDED));
      }

      // <responseType> is strongly recommended, when applicable,
      // along with a corresponding <instruction> value
      if (info.getResponseTypeCount() == 0) {
        reasons.add(new Reason(xpath,
            RecommendationType.RESPONSE_TYPE_STRONGLY_RECOMMENDED));
      }
      if (CapUtil.isEmptyOrWhitespace(info.getInstruction())) {
        reasons.add(new Reason(xpath,
            RecommendationType.INSTRUCTION_STRONGLY_RECOMMENDED));
      }

      // Headline should be < 140 chars
      if (info.hasHeadline() && info.getHeadline().length() > 140) {
        reasons.add(new Reason(xpath + "/headline",
            RecommendationType.HEADLINE_TOO_LONG));
      }

      if (info.getDescription().equals(info.getHeadline())) {
        reasons.add(new Reason(xpath + "/headline",
            RecommendationType.HEADLINE_AND_DESCRIPTION_SHOULD_DIFFER));
      }

      if (info.hasInstruction()
          && !CapUtil.isEmptyOrWhitespace(info.getInstruction())
          && info.getDescription().equals(info.getInstruction())) {
        reasons.add(new Reason(xpath + "/description",
            RecommendationType.DESCRIPTION_AND_INSTRUCTION_SHOULD_DIFFER));
      }

      if (info.getUrgency() == Info.Urgency.UNKNOWN_URGENCY) {
        reasons.add(new Reason(xpath + "/urgency",
            RecommendationType.UNKNOWN_URGENCY_DISCOURAGED));
      }
      if (info.getSeverity() == Info.Severity.UNKNOWN_SEVERITY) {
        reasons.add(new Reason(xpath + "/severity",
            RecommendationType.UNKNOWN_SEVERITY_DISCOURAGED));
      }
      if (info.getCertainty() == Info.Certainty.UNKNOWN_CERTAINTY) {
        reasons.add(new Reason(xpath + "/certainty",
            RecommendationType.UNKNOWN_CERTAINTY_DISCOURAGED));
      }

      if (!info.hasContact()
          || CapUtil.isEmptyOrWhitespace(info.getContact())) {
        reasons.add(new Reason(xpath,
            RecommendationType.CONTACT_IS_RECOMMENDED));
      }

      // 18. Preferential treatment of <polygon> and <circle>
      boolean hasPolygonOrCircle = false;
      for (int j = 0; j < info.getAreaCount(); j++) {
        Area area = info.getArea(j);
        if (area.getCircleCount() != 0 || area.getPolygonCount() != 0) {
          hasPolygonOrCircle = true;
        }
        for (int k = 0; k < area.getCircleCount(); k++) {
          Circle circle = area.getCircle(k);
          if (circle.getRadius() == 0) {
            reasons.add(new Reason(
                xpath + "/area[" + j + "]/circle[" + k + "]",
                RecommendationType.NONZERO_CIRCLE_RADIUS_RECOMMENDED));
          }
        }
      }
      if (!hasPolygonOrCircle && info.getAreaCount() > 0) {
        reasons.add(new Reason(xpath + "/area[0]",
            RecommendationType.CIRCLE_POLYGON_ENCOURAGED));
      }
    }

    return reasons;
  }

  // TODO(shakusa) Localize messages
  public enum ErrorType implements CapException.ReasonType {
    UPDATE_OR_CANCEL_MUST_REFERENCE("All related messages that have not yet " +
        "expired must be referenced when an \"Update\" or \"Cancel\" is " +
        "issued. This ensures that an \"Update\" or \"Cancel\" applies to at " +
        "least one non-expired alert."),
    CATEGORIES_MUST_MATCH(
        "All <info> blocks must contain the same <category>s"),
    EVENTS_IN_SAME_LANGUAGE_MUST_MATCH("All <info> blocks with the same " +
        "<langauge> must contain the same <event>"),
    EVENT_CODES_MUST_MATCH(
        "All <info> blocks must contain the same <eventCode>s"),
    INFO_IS_REQUIRED("At least one <info> must be present"),
    DESCRIPTION_IS_REQUIRED("<description> must be present"),
    WEB_IS_REQUIRED("<web> must be present"),
    EXPIRES_IS_REQUIRED("<expires> must be present"),
    EFFECTIVE_NOT_AFTER_EXPIRES("<effective> should not come after <expires>"),
    URGENCY_IS_REQUIRED("<urgency> must be present"),
    SEVERITY_IS_REQUIRED("<severity> must be present"),
    CERTAINTY_IS_REQUIRED("<certainty> must be present"),
    AREA_IS_REQUIRED("At least one <area> must be present"),
    CIRCLE_POLYGON_OR_GEOCODE_IS_REQUIRED("Each <area> must have at least " +
        "one <circle>, <polygon> or <geocode>."),
    ;
    private final String message;

    private ErrorType(String message) {
      this.message = message;
    }

    @Override
    public String getMessage(Locale locale) {
      return message;
    }
  }

  // TODO(shakusa) Localize messages
  public enum RecommendationType implements CapException.ReasonType {
    SENDER_NAME_STRONGLY_RECOMMENDED(
        "<senderName> is strongly recommended."),
    RESPONSE_TYPE_STRONGLY_RECOMMENDED(
        "<responseType> is strongly recommended."),
    INSTRUCTION_STRONGLY_RECOMMENDED(
        "<instruction> is strongly recommended."),
    CIRCLE_POLYGON_ENCOURAGED("<polygon> and <circle>, while optional, are " +
        "encouraged as more accurate representations of <geocode> values"),
    SENT_INCLUDE_TIMEZONE_OFFSET("Time zone should be included in " +
        "<sent> whenever possible."),
    EFFECTIVE_INCLUDE_TIMEZONE_OFFSET("Time zone should be included in " +
        "<offset> whenever possible."),
    ONSET_INCLUDE_TIMEZONE_OFFSET("Time zone should be included in " +
        "<onset> whenever possible."),
    EXPIRES_INCLUDE_TIMEZONE_OFFSET("Time zone should be included in " +
        "<expires> whenever possible."),
    HEADLINE_TOO_LONG("Headline should be less than 140 characters"),
    HEADLINE_AND_DESCRIPTION_SHOULD_DIFFER("Description should provide " +
        "more detail than the headline and should not be identical."),
    DESCRIPTION_AND_INSTRUCTION_SHOULD_DIFFER("Description should " +
        "describe the hazard while instruction should provide " +
        "human-readable instructions. They should not be identical."),
    UNKNOWN_URGENCY_DISCOURAGED("Unknown <urgency> is discouraged."),
    UNKNOWN_SEVERITY_DISCOURAGED("Unknown <severity> is discouraged."),
    UNKNOWN_CERTAINTY_DISCOURAGED("Unknown <certainty> is discouraged."),
    CONTACT_IS_RECOMMENDED("<contact> is recommended to give users a way to " +
        "provide feedback and respond to the alert."),
    NONZERO_CIRCLE_RADIUS_RECOMMENDED("A CAP <area> defines the area inside " + 
        "which people should be alerted, not the area of the event causing " +
        "the alert. This area should normally have nonzero radius"),
    ;
    private final String message;

    private RecommendationType(String message) {
      this.message = message;
    }

    @Override
    public String getMessage(Locale locale) {
      return message;
    }
  }
}