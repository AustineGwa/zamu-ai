package ai.zamu.reports;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * zamu.billing: rates for the running-cost estimate (REP-03, REP-04, NFR-19). ASSUMPTION: defaults are
 * placeholders; set the current exchange rate and Meta's Kenya rate for the template categories you send.
 *
 * @param usdToKes             exchange rate for AI costs, which providers bill in US dollars
 * @param whatsappMessageUsd   Meta's charge per template message
 * @param alertShare           running cost as a share of the plan fee that flags a tenant (spec: 40%)
 */
@ConfigurationProperties("zamu.billing")
record BillingProperties(BigDecimal usdToKes, BigDecimal whatsappMessageUsd, BigDecimal alertShare) {

    BillingProperties {
        usdToKes = usdToKes == null ? new BigDecimal("129.00") : usdToKes;
        whatsappMessageUsd = whatsappMessageUsd == null ? new BigDecimal("0.0100") : whatsappMessageUsd;
        alertShare = alertShare == null ? new BigDecimal("0.40") : alertShare;
    }
}
