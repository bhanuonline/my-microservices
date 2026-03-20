
Cart → DeliveryMode → DeliveryCostStrategy → CalculationService → Order

1️⃣ DeliveryMode (WHAT type of delivery)
DeliveryModeModel represents how the order is delivered.
Common types:
ZoneDeliveryMode (most used)
PickupDeliveryMode (Click & Collect)
Custom delivery modes

Example:
Standard Shipping
Express Shipping
Same Day Delivery

AbstractOrderModel.getDeliveryMode()


2️⃣ ZoneDeliveryMode (MOST COMMON)
This is where delivery cost rules are defined.
ZoneDeliveryMode
    └── Zone
        └── DeliveryValue


3️⃣ How Hybris knows the Zone 🌍
Zone is resolved using:
Delivery address → Country → Zone

Example:
Address.country = IN
IN → AsiaZone
AsiaZone → DeliveryValues


4️⃣ DeliveryValue (WHERE cost is defined)
Attributes:
minimum → cart total threshold
value → delivery charge
currency
zone

Hybris selects:
Highest minimum ≤ cart total

5️⃣ DeliveryCostStrategy (HOW cost is calculated)
Default:
DefaultDeliveryCostStrategy

Logic:
Get cart total
Get delivery mode
Find zone from address
Find matching delivery value
Return delivery cost


6️⃣ CalculationService (WHEN cost is applied)
Delivery cost is not applied automatically.
You must call:
calculationService.calculate(cartModel);

What it does:
Calculates product prices
Applies taxes
Applies delivery cost
Stores it in:
cart.getDeliveryCost();

9️⃣ Pickup / CNC (No delivery cost)
For Click & Collect:
PickupDeliveryMode
Delivery cost = 0
Custom strategy usually skips calculation

Example override:
if (deliveryMode instanceof PickupDeliveryModeModel) {
return Double.valueOf(0);
}

🚚 What is DeliveryModeLookupStrategy in Hybris?
Cart
└── DeliveryModeLookupStrategy   ← (this guy)
    └── List<DeliveryModeModel>
        └── User selects one
            └── DeliveryCostStrategy

3️⃣ What it actually does internally 🧠

Default logic:
Get delivery address from cart
Resolve country → zone
Fetch all DeliveryModes
Filter delivery modes:
Compatible with the zone
Supported by the store
Valid currency
Return list of available modes


DeliveryCostStrategy

“Yes, I trace flows end-to-end starting from entry points and follow facades → services → strategies.”