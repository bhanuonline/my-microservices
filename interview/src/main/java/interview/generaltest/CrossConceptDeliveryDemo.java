package interview.generaltest;

import java.util.*;
import java.util.stream.Collectors;

public class CrossConceptDeliveryDemo {

    public static void main(String[] args) {

        /* ------------------ Test Data ------------------ */

        // Simulated delivery mode values from DB
        List<ZoneDeliveryModeValueModel> deliveryModeValues = new ArrayList<>();
        deliveryModeValues.add(new ZoneDeliveryModeValueModel("HC"));
        deliveryModeValues.add(new ZoneDeliveryModeValueModel("BS"));
        //deliveryModeValues.add(new ZoneDeliveryModeValueModel("LS"));

        // Concepts present in cart (cart has ONLY BS item)
        Set<String> conceptCodesPresentInCart = new HashSet<>();
        conceptCodesPresentInCart.add("BS");

        // Feature flag
        boolean isCrossConceptSellingFeatureEnabled = true;

        // Config from properties
        String configuredCrossConceptCodes = "BS,HB";

        /* ------------------ Logic ------------------ */

        boolean cartContainsCrossConcept =
                configuredCrossConceptCodes != null
                && !configuredCrossConceptCodes.trim().isEmpty()
                && Arrays.stream(configuredCrossConceptCodes.split(","))
                         .map(String::trim)
                         .anyMatch(conceptCodesPresentInCart::contains);

        if (isCrossConceptSellingFeatureEnabled && cartContainsCrossConcept) {

            System.out.println("Cross-concept cart detected");
            System.out.println("ConfiguredCrossConceptCodes = " + configuredCrossConceptCodes);
            System.out.println("CartConcepts = " + conceptCodesPresentInCart);

            for (Iterator<ZoneDeliveryModeValueModel> iterator = deliveryModeValues.iterator();
                 iterator.hasNext();) {

                ZoneDeliveryModeValueModel zoneValue = iterator.next();
                String deliveryConceptCode = zoneValue.getConceptCode();

                boolean shouldRemove =
                        Arrays.stream(configuredCrossConceptCodes.split(","))
                              .map(String::trim)
                              .anyMatch(deliveryConceptCode::equals);

                if (shouldRemove) {
                    System.out.println(
                            "Removing delivery mode for concept = " + deliveryConceptCode);
                    iterator.remove();
                }
            }
        }

        /* ------------------ Final Output ------------------ */

        System.out.println("\nEligible delivery modes after filtering:");
        deliveryModeValues.forEach(v ->
                System.out.println(" - " + v.getConceptCode()));
    }
}

/* ------------------ Helper Model ------------------ */

class ZoneDeliveryModeValueModel {

    private final String conceptCode;

    ZoneDeliveryModeValueModel(String conceptCode) {
        this.conceptCode = conceptCode;
    }

    public String getConceptCode() {
        return conceptCode;
    }
}
