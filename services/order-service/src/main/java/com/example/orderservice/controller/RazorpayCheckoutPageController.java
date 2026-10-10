package com.example.orderservice.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal HTML page that loads Razorpay's Checkout.js and opens the payment
 * modal pre-filled with the order id. Keeps the Razorpay flow usable from a
 * browser without needing a separate frontend project.
 *
 * <h3>How it fits</h3>
 * payment-service's {@link
 * com.example.paymentservice.provider.RazorpayPaymentProvider#initiate}
 * returns a URL pointing at this controller, with the Razorpay order id,
 * amount, currency, and public key id as query params. The client opens
 * that URL, the modal appears, user pays, Razorpay webhook arrives at
 * payment-service — none of which this controller is involved in beyond
 * rendering the initial page.
 *
 * <p>HTML is inline (not a template) so order-service doesn't need Thymeleaf.
 * Params are escaped minimally since they're constrained to Razorpay-shaped
 * ids and numbers; the controller rejects anything containing a quote or
 * angle bracket.
 */
@RestController
@RequestMapping("/razorpay")
public class RazorpayCheckoutPageController {

    @GetMapping(path = "/checkout", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> checkoutPage(
            @RequestParam("orderId") String rzpOrderId,
            @RequestParam("amount") long amountPaise,
            @RequestParam(value = "currency", defaultValue = "INR") String currency,
            @RequestParam("keyId") String keyId) {

        if (unsafe(rzpOrderId) || unsafe(currency) || unsafe(keyId)) {
            return ResponseEntity.badRequest().body("<h1>Invalid parameters</h1>");
        }

        String html = """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8" />
                  <title>Pay with Razorpay</title>
                  <style>
                    body { font-family: system-ui, sans-serif; max-width: 32rem;
                           margin: 4rem auto; padding: 1rem; color: #222; }
                    .card { border: 1px solid #ddd; border-radius: 8px; padding: 1.5rem; }
                    code { background: #f5f5f5; padding: 2px 6px; border-radius: 3px; }
                    button { background: #528FF0; color: white; border: none; padding: 10px 20px;
                             border-radius: 4px; font-size: 16px; cursor: pointer; }
                    button:hover { background: #3a7bd5; }
                  </style>
                </head>
                <body>
                  <h1>Razorpay demo checkout</h1>
                  <div class="card">
                    <p>Click <strong>Pay</strong> to open the Razorpay modal.</p>
                    <p>Amount: <code>%s %s</code> (paise: %d)</p>
                    <p>Order id: <code>%s</code></p>
                    <button id="pay-btn">Pay</button>
                    <p id="status" style="margin-top: 1rem; color: #666;"></p>
                  </div>
                  <script src="https://checkout.razorpay.com/v1/checkout.js"></script>
                  <script>
                    const options = {
                      key: "%s",
                      amount: %d,
                      currency: "%s",
                      name: "my-microservices demo",
                      description: "Order payment",
                      order_id: "%s",
                      handler: function (response) {
                        document.getElementById('status').innerText =
                          "Paid. razorpay_payment_id = " + response.razorpay_payment_id +
                          ". Webhook should arrive at payment-service shortly.";
                      },
                      modal: {
                        ondismiss: function () {
                          document.getElementById('status').innerText = "Modal dismissed.";
                        }
                      }
                    };
                    document.getElementById('pay-btn').addEventListener('click', function () {
                      const rzp = new Razorpay(options);
                      rzp.on('payment.failed', function (resp) {
                        document.getElementById('status').innerText =
                          "Payment failed: " + resp.error.description;
                      });
                      rzp.open();
                    });
                  </script>
                </body>
                </html>
                """.formatted(
                        currency, formatAmount(amountPaise), amountPaise,
                        rzpOrderId,
                        keyId, amountPaise, currency,
                        rzpOrderId
                );
        return ResponseEntity.ok(html);
    }

    private static boolean unsafe(String s) {
        return s == null || s.indexOf('<') >= 0 || s.indexOf('>') >= 0
                || s.indexOf('"') >= 0 || s.indexOf('\'') >= 0;
    }

    /** Convert paise back to rupees for display only. */
    private static String formatAmount(long paise) {
        return String.format("%.2f", paise / 100.0);
    }
}
